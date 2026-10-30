package com.hazeline.music;

import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import com.hazeline.Hazeline;

/**
 * Reads what is playing on Windows and controls it - no account, no login.
 *
 * <p>Source: the Windows "Global System Media Transport Controls" sessions
 * ({@code Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager}, Windows 10 1809+).
 * Chrome (and Edge, Spotify, ...) publish the page's Media Session there - YouTube Music fills in
 * title, artist, album, cover, position and the play/pause/next/previous/seek actions. Volume is the
 * app's own slider in the Windows volume mixer (Core Audio {@link AppVolume}), because the media
 * session API has no volume.
 *
 * <p>All Windows calls run on one daemon worker thread (WinRT multithreaded apartment). The render
 * thread only reads the immutable {@link #state()} / {@link #cover()} snapshots and queues commands.
 */
public final class MediaService {
	// Vtable slots (windows-rs bindings, Windows/Media/Control/mod.rs; 6 = first method after IInspectable).
	private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
	private static final String IID_MANAGER_STATICS = "2050c4ee-11a0-57de-aed7-c97c70338245";
	private static final int STATICS_REQUEST = 6;
	private static final int MANAGER_CURRENT = 6, MANAGER_SESSIONS = 7;
	private static final int VECTOR_GET_AT = 6, VECTOR_SIZE = 7;
	private static final int SESSION_AUMID = 6, SESSION_PROPS = 7, SESSION_TIMELINE = 8, SESSION_INFO = 9, SESSION_PLAY = 10,
			SESSION_PAUSE = 11, SESSION_NEXT = 16, SESSION_PREV = 17, SESSION_TOGGLE = 20, SESSION_SEEK = 24;
	private static final int PROPS_TITLE = 6, PROPS_ARTIST = 9, PROPS_ALBUM = 10, PROPS_THUMBNAIL = 15;
	private static final int TL_START = 6, TL_END = 7, TL_POSITION = 10, TL_UPDATED = 11;
	private static final int INFO_CONTROLS = 6, INFO_STATUS = 7;
	private static final int CTRL_PLAY = 6, CTRL_PAUSE = 7, CTRL_NEXT = 12, CTRL_PREV = 13, CTRL_TOGGLE = 16, CTRL_POSITION = 20;

	/** GlobalSystemMediaTransportControlsSessionPlaybackStatus. */
	public static final int STATUS_PLAYING = 4, STATUS_PAUSED = 5;

	/** A snapshot of the chosen session. Times are 100 ns ticks; lastUpdated is a FILETIME (UTC). */
	public record State(boolean active, String app, String title, String artist, String album, int status, long startTicks,
			long endTicks, long positionTicks, long lastUpdated, long sampledAtMs, boolean canPlay, boolean canPause,
			boolean canNext, boolean canPrev, boolean canToggle, boolean canSeek, float volume) {
		static final State NONE = new State(false, "", "", "", "", 0, 0, 0, 0, 0, 0, false, false, false, false, false, false, -1f);

		public boolean playing() {
			return status == STATUS_PLAYING;
		}

		/** Length in ms, 0 if unknown. */
		public long durationMs() {
			return Math.max(0, (endTicks - startTicks) / 10_000L);
		}

		/** Current position in ms, extrapolated while playing. */
		public long positionMs(long nowMs) {
			long pos = (positionTicks - startTicks) / 10_000L;
			if (playing()) {
				long nowFt = (nowMs + 11_644_473_600_000L) * 10_000L;
				long since;
				if (lastUpdated > 0 && nowFt >= lastUpdated && nowFt - lastUpdated < 86_400_000_0000L) {
					since = (nowFt - lastUpdated) / 10_000L;
				} else {
					since = nowMs - sampledAtMs;
				}
				pos += Math.max(0, since);
			}
			long dur = durationMs();
			return Math.max(0, dur > 0 ? Math.min(pos, dur) : pos);
		}

		State withStatus(int s) {
			return new State(active, app, title, artist, album, s, startTicks, endTicks, positionTicks, lastUpdated, sampledAtMs,
					canPlay, canPause, canNext, canPrev, canToggle, canSeek, volume);
		}

		State withPosition(long ticks, long nowMs) {
			return new State(active, app, title, artist, album, status, startTicks, endTicks, ticks, 0, nowMs, canPlay, canPause,
					canNext, canPrev, canToggle, canSeek, volume);
		}

		State withVolume(float v) {
			return new State(active, app, title, artist, album, status, startTicks, endTicks, positionTicks, lastUpdated,
					sampledAtMs, canPlay, canPause, canNext, canPrev, canToggle, canSeek, v);
		}
	}

	private enum Kind {
		TOGGLE, NEXT, PREV, SEEK, VOLUME
	}

	private record Command(Kind kind, double arg) {
	}

	private static final MediaService INSTANCE = new MediaService();

	private final LinkedBlockingQueue<Command> commands = new LinkedBlockingQueue<>();
	private volatile State state = State.NONE;
	private volatile CoverArt.Cover cover;
	private volatile String status = "Off";
	private volatile Thread thread;
	private volatile boolean running;

	// worker thread only
	private Pointer manager;
	private Pointer session;
	private String sessionApp = "";
	private String trackKey = "";
	private int coverAttempts;
	private long nextCoverAttempt;
	private int coverVersion;
	private long lastVolumeRead;
	private float volume = -1f;
	private final AppVolume appVolume = new AppVolume();

	private MediaService() {
	}

	public static MediaService get() {
		return INSTANCE;
	}

	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	public State state() {
		return state;
	}

	public CoverArt.Cover cover() {
		return cover;
	}

	/** Human readable status for the module settings ("Connected", "Windows only", errors...). */
	public String status() {
		return status;
	}

	// ------------------------------------------------------------------------ commands (any thread)

	public void togglePlay() {
		State s = state;
		if (s.active()) {
			state = s.withStatus(s.playing() ? STATUS_PAUSED : STATUS_PLAYING); // instant feedback
		}
		commands.offer(new Command(Kind.TOGGLE, 0));
	}

	public void next() {
		commands.offer(new Command(Kind.NEXT, 0));
	}

	public void previous() {
		commands.offer(new Command(Kind.PREV, 0));
	}

	/** Jumps to {@code ms} from the start of the track. */
	public void seek(long ms) {
		State s = state;
		if (s.active()) {
			state = s.withPosition(s.startTicks() + ms * 10_000L, System.currentTimeMillis());
		}
		commands.removeIf(c -> c.kind() == Kind.SEEK);
		commands.offer(new Command(Kind.SEEK, ms));
	}

	/** Sets the playing app's volume (0..1) in the Windows volume mixer. */
	public void setVolume(float v) {
		float clamped = Math.max(0f, Math.min(1f, v));
		state = state.withVolume(clamped);
		commands.removeIf(c -> c.kind() == Kind.VOLUME);
		commands.offer(new Command(Kind.VOLUME, clamped));
	}

	// ------------------------------------------------------------------------ lifecycle

	public synchronized void start() {
		if (thread != null) {
			return;
		}
		if (!supported()) {
			status = "Windows only";
			return;
		}
		running = true;
		status = "Starting...";
		Thread t = new Thread(this::run, "Hazeline Music HUD");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY + 1);
		thread = t;
		t.start();
	}

	public synchronized void stop() {
		running = false;
		Thread t = thread;
		thread = null;
		if (t != null) {
			t.interrupt();
		}
		commands.clear();
	}

	private void run() {
		boolean ro = false;
		try {
			int hr = Com.fn("combase", "RoInitialize").invokeInt(new Object[] {1}); // RO_INIT_MULTITHREADED
			ro = hr >= 0; // S_OK / S_FALSE; RPC_E_CHANGED_MODE would mean this thread is already STA (cannot happen here)
			if (hr < 0) {
				Hazeline.LOGGER.warn("Music HUD: RoInitialize failed 0x{}", Integer.toHexString(hr));
			}
			manager = requestManager();
			if (manager == null) {
				status = "Could not connect to Windows media sessions (see latest.log)";
				Hazeline.LOGGER.warn("Music HUD: {}", status);
				return;
			}
			Hazeline.LOGGER.info("Music HUD: connected to Windows media sessions");
			status = "Connected - waiting for music";
			int failures = 0;
			long nextPoll = 0;
			while (running) {
				try {
					Command c = commands.poll(Math.max(1, nextPoll - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
					if (!running) {
						break;
					}
					if (c != null) {
						execute(c);
						nextPoll = System.currentTimeMillis() + 120; // refresh right after a command
						continue;
					}
					poll();
					failures = 0;
					nextPoll = System.currentTimeMillis() + 500;
				} catch (InterruptedException e) {
					break;
				} catch (RuntimeException e) {
					failures++;
					if (failures == 1 || failures % 50 == 0) {
						Hazeline.LOGGER.warn("Music HUD: reading media session failed ({}x): {}", failures, e.toString());
					}
					releaseSession();
					state = State.NONE;
					nextPoll = System.currentTimeMillis() + Math.min(5000, 500L * failures);
				}
			}
		} catch (Throwable t) {
			status = "Error: " + t;
			Hazeline.LOGGER.error("Music HUD: media worker stopped", t);
		} finally {
			try {
				releaseSession();
				Com.release(manager);
				manager = null;
				appVolume.close();
			} catch (Throwable t) {
				Hazeline.LOGGER.warn("Music HUD: cleanup failed: {}", t.toString());
			}
			if (ro) {
				try {
					Com.fn("combase", "RoUninitialize").invokeVoid(new Object[0]);
				} catch (Throwable ignored) {
				}
			}
			state = State.NONE;
			cover = null;
			trackKey = "";
			if (!status.startsWith("Error") && !status.startsWith("Windows")) {
				status = "Off";
			}
		}
	}

	private Pointer requestManager() {
		Pointer cls = Com.hstring(MANAGER_CLASS);
		Pointer statics;
		try {
			PointerByReference out = new PointerByReference();
			int hr = Com.fn("combase", "RoGetActivationFactory").invokeInt(new Object[] {cls, Com.guid(IID_MANAGER_STATICS), out});
			if (hr < 0) {
				Hazeline.LOGGER.warn("Music HUD: RoGetActivationFactory failed 0x{}", Integer.toHexString(hr));
				return null;
			}
			statics = out.getValue();
		} finally {
			Com.deleteHString(cls);
		}
		try {
			return Com.awaitPtr(Com.getPtr(statics, STATICS_REQUEST, "RequestAsync"), 10_000, "RequestAsync");
		} finally {
			Com.release(statics);
		}
	}

	// ------------------------------------------------------------------------ reading

	private void releaseSession() {
		Com.release(session);
		session = null;
		sessionApp = "";
	}

	/** Picks the session to show: playing beats paused; the last shown one and Windows' current one win ties. */
	private void poll() {
		String currentApp = "";
		Pointer current = Com.getPtr(manager, MANAGER_CURRENT, "GetCurrentSession");
		if (current != null) {
			try {
				currentApp = Com.getString(current, SESSION_AUMID, "SourceAppUserModelId");
			} finally {
				Com.release(current);
			}
		}

		Pointer list = Com.getPtr(manager, MANAGER_SESSIONS, "GetSessions");
		Pointer best = null;
		String bestApp = "";
		int bestScore = 0;
		if (list != null) {
			try {
				int n = Com.getInt(list, VECTOR_SIZE, "Size");
				for (int i = 0; i < n; i++) {
					PointerByReference out = new PointerByReference();
					if (Com.call(list, VECTOR_GET_AT, i, out) < 0 || out.getValue() == null) {
						continue;
					}
					Pointer s = out.getValue();
					int score = 0;
					String app = "";
					try {
						app = Com.getString(s, SESSION_AUMID, "SourceAppUserModelId");
						Pointer info = Com.getPtr(s, SESSION_INFO, "GetPlaybackInfo");
						int st = 0;
						if (info != null) {
							try {
								st = Com.getInt(info, INFO_STATUS, "PlaybackStatus");
							} finally {
								Com.release(info);
							}
						}
						score = st == STATUS_PLAYING ? 40 : st == STATUS_PAUSED ? 20 : 0;
						if (score > 0 && app.equals(sessionApp)) {
							score += 10;
						}
						if (score > 0 && app.equals(currentApp)) {
							score += 5;
						}
					} catch (RuntimeException e) {
						score = 0;
					}
					if (score > bestScore) {
						Com.release(best);
						best = s;
						bestApp = app;
						bestScore = score;
					} else {
						Com.release(s);
					}
				}
			} finally {
				Com.release(list);
			}
		}

		releaseSession();
		if (best == null) {
			if (state.active()) {
				Hazeline.LOGGER.info("Music HUD: nothing playing any more");
			}
			state = State.NONE;
			status = "Connected - nothing playing";
			return;
		}
		session = best;
		sessionApp = bestApp;
		read(best, bestApp);
	}

	private void read(Pointer s, String app) {
		String title = "", artist = "", album = "";
		Pointer thumbnail = null;
		Pointer props = Com.awaitPtr(Com.getPtr(s, SESSION_PROPS, "TryGetMediaPropertiesAsync"), 3000, "media properties");
		if (props != null) {
			try {
				title = Com.getString(props, PROPS_TITLE, "Title");
				artist = Com.getString(props, PROPS_ARTIST, "Artist");
				album = Com.getString(props, PROPS_ALBUM, "AlbumTitle");
				thumbnail = Com.getPtr(props, PROPS_THUMBNAIL, "Thumbnail");
			} finally {
				Com.release(props);
			}
		}
		try {
			long start = 0, end = 0, pos = 0, updated = 0;
			Pointer tl = Com.getPtr(s, SESSION_TIMELINE, "GetTimelineProperties");
			if (tl != null) {
				try {
					start = Com.getLong(tl, TL_START, "StartTime");
					end = Com.getLong(tl, TL_END, "EndTime");
					pos = Com.getLong(tl, TL_POSITION, "Position");
					updated = Com.getLong(tl, TL_UPDATED, "LastUpdatedTime");
				} finally {
					Com.release(tl);
				}
			}
			int st = 0;
			boolean play = false, pause = false, next = false, prev = false, toggle = false, seek = false;
			Pointer info = Com.getPtr(s, SESSION_INFO, "GetPlaybackInfo");
			if (info != null) {
				try {
					st = Com.getInt(info, INFO_STATUS, "PlaybackStatus");
					Pointer c = Com.getPtr(info, INFO_CONTROLS, "Controls");
					if (c != null) {
						try {
							play = Com.getBool(c, CTRL_PLAY, "IsPlayEnabled");
							pause = Com.getBool(c, CTRL_PAUSE, "IsPauseEnabled");
							next = Com.getBool(c, CTRL_NEXT, "IsNextEnabled");
							prev = Com.getBool(c, CTRL_PREV, "IsPreviousEnabled");
							toggle = Com.getBool(c, CTRL_TOGGLE, "IsPlayPauseToggleEnabled");
							seek = Com.getBool(c, CTRL_POSITION, "IsPlaybackPositionEnabled");
						} finally {
							Com.release(c);
						}
					}
				} finally {
					Com.release(info);
				}
			}

			long now = System.currentTimeMillis();
			if (now - lastVolumeRead > 1000) {
				lastVolumeRead = now;
				volume = appVolume.get(AppVolume.exeFor(app));
			}

			String key = app + "\u0000" + title + "\u0000" + artist + "\u0000" + album;
			if (!key.equals(trackKey)) {
				if (!title.equals(state.title()) || !app.equals(state.app())) {
					Hazeline.LOGGER.info("Music HUD: now playing \"{}\" - \"{}\" ({})", title, artist, app);
				}
				trackKey = key;
				coverAttempts = 0;
				nextCoverAttempt = 0;
				cover = null;
			}
			if (cover == null && thumbnail != null && coverAttempts < 6 && now >= nextCoverAttempt) {
				coverAttempts++;
				nextCoverAttempt = now + 1500L * coverAttempts;
				byte[] bytes = readThumbnail(thumbnail);
				if (bytes != null) {
					CoverArt.Cover c = CoverArt.process(bytes, ++coverVersion);
					if (c != null) {
						cover = c;
					}
				}
			}

			state = new State(true, app, title, artist, album, st, start, end, pos, updated, now, play, pause, next, prev, toggle,
					seek, volume);
			status = "Connected - " + (st == STATUS_PLAYING ? "playing" : st == STATUS_PAUSED ? "paused" : "idle") + " in "
					+ (app.isEmpty() ? "unknown app" : app);
		} finally {
			Com.release(thumbnail);
		}
	}

	// IRandomAccessStreamReference.OpenReadAsync = 6; IRandomAccessStream.Size = 6 (Windows/Storage/Streams/mod.rs).
	private static final Memory IID_RANDOM_ACCESS_STREAM = Com.guid("905a0fe1-bc53-11df-8c49-001e4fc686da");
	private static final Memory IID_ISTREAM = Com.guid("0000000c-0000-0000-c000-000000000046");
	private static final int MAX_COVER_BYTES = 8 << 20;

	/** Reads the cover image bytes (PNG/JPEG...) through an IStream wrapper (shcore CreateStreamOverRandomAccessStream). */
	private static byte[] readThumbnail(Pointer ref) {
		Pointer stream = Com.awaitPtr(Com.getPtr(ref, 6, "OpenReadAsync"), 3000, "thumbnail");
		if (stream == null) {
			return null;
		}
		Pointer ras = null;
		Pointer istream = null;
		try {
			ras = Com.query(stream, IID_RANDOM_ACCESS_STREAM);
			if (ras == null) {
				return null;
			}
			long size = Com.getLong(ras, 6, "Size");
			if (size <= 0 || size > MAX_COVER_BYTES) {
				return null;
			}
			PointerByReference out = new PointerByReference();
			int hr = Com.fn("shcore", "CreateStreamOverRandomAccessStream").invokeInt(new Object[] {ras, IID_ISTREAM, out});
			if (hr < 0 || out.getValue() == null) {
				debug("CreateStreamOverRandomAccessStream 0x" + Integer.toHexString(hr));
				return null;
			}
			istream = out.getValue();
			byte[] data = new byte[(int) size];
			Memory buf = new Memory(64 * 1024);
			Memory read = new Memory(4);
			int total = 0;
			while (total < data.length) {
				read.setInt(0, 0);
				int want = (int) Math.min(buf.size(), data.length - total);
				hr = Com.call(istream, 3, buf, want, read); // ISequentialStream::Read
				int n = read.getInt(0);
				if (hr < 0 || n <= 0) {
					break;
				}
				buf.read(0, data, total, n);
				total += n;
			}
			if (total < data.length) {
				byte[] cut = new byte[total];
				System.arraycopy(data, 0, cut, 0, total);
				data = cut;
			}
			return data.length > 0 ? data : null;
		} finally {
			Com.release(istream);
			Com.release(ras);
			Com.release(stream);
		}
	}

	// ------------------------------------------------------------------------ commands (worker)

	private void execute(Command c) {
		if (c.kind() == Kind.VOLUME) {
			String exe = AppVolume.exeFor(sessionApp.isEmpty() ? state.app() : sessionApp);
			int n = appVolume.set(exe, (float) c.arg());
			if (n > 0) {
				volume = (float) c.arg();
			}
			lastVolumeRead = System.currentTimeMillis(); // keep the new value until the next read
			return;
		}
		Pointer s = session;
		if (s == null) {
			return;
		}
		State st = state;
		boolean ok = switch (c.kind()) {
			case TOGGLE -> {
				if (st.canToggle()) {
					yield Com.awaitBool(Com.getPtr(s, SESSION_TOGGLE, "TryTogglePlayPauseAsync"), 2000, "toggle");
				}
				// state was already flipped for instant feedback: playing now = it was paused
				yield Com.awaitBool(Com.getPtr(s, st.playing() ? SESSION_PLAY : SESSION_PAUSE, "TryPlay/Pause"), 2000, "play/pause");
			}
			case NEXT -> Com.awaitBool(Com.getPtr(s, SESSION_NEXT, "TrySkipNextAsync"), 2000, "next");
			case PREV -> Com.awaitBool(Com.getPtr(s, SESSION_PREV, "TrySkipPreviousAsync"), 2000, "previous");
			case SEEK -> Com.awaitBool(Com.getPtrWith(s, SESSION_SEEK, "TryChangePlaybackPositionAsync",
					Long.valueOf(st.startTicks() + (long) c.arg() * 10_000L)), 2000, "seek");
			default -> false;
		};
		if (!ok) {
			debug(c.kind() + " was not accepted by " + sessionApp);
		}
	}

	private static volatile String lastDebug = "";

	/** Logs a message once (repeats of the same text are skipped). */
	static void debug(String msg) {
		if (!msg.equals(lastDebug)) {
			lastDebug = msg;
			Hazeline.LOGGER.info("Music HUD: {}", msg);
		}
	}
}

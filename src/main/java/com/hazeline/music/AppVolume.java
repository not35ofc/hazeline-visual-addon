package com.hazeline.music;

import java.util.Locale;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * One app's slider in the Windows volume mixer (Core Audio audio sessions), e.g. Chrome's.
 *
 * <p>Walks every active output device ({@code IMMDeviceEnumerator.EnumAudioEndpoints}), its audio
 * sessions ({@code IAudioSessionManager2.GetSessionEnumerator}) and matches the session's process
 * by executable name; the volume is the session's {@code ISimpleAudioVolume}. Interface layouts and
 * IIDs from Wine's mmdeviceapi.idl / audiopolicy.idl / audioclient.idl. Worker thread only.
 */
final class AppVolume {
	private static final Memory CLSID_ENUMERATOR = Com.guid("bcde0395-e52f-467c-8e3d-c4579291692e");
	private static final Memory IID_ENUMERATOR = Com.guid("a95664d2-9614-4f35-a746-de8db63617e6");
	private static final Memory IID_SESSION_MANAGER2 = Com.guid("77aa99a0-1bd6-484f-8bc7-2c654c9a9b6f");
	private static final Memory IID_SESSION_CONTROL2 = Com.guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d");
	private static final Memory IID_SIMPLE_VOLUME = Com.guid("87ce5498-68d6-44e5-9215-6da47ef883d8");
	private static final int CLSCTX_ALL = 0x17;
	private static final int ENUM_ENDPOINTS = 3; // IMMDeviceEnumerator
	private static final int COLLECTION_COUNT = 3, COLLECTION_ITEM = 4; // IMMDeviceCollection
	private static final int DEVICE_ACTIVATE = 3; // IMMDevice
	private static final int MANAGER2_ENUMERATOR = 5; // IAudioSessionManager2 (after GetAudioSessionControl 3, GetSimpleAudioVolume 4)
	private static final int SESSIONS_COUNT = 3, SESSIONS_GET = 4; // IAudioSessionEnumerator
	private static final int CONTROL2_PROCESS_ID = 14; // IAudioSessionControl2 (IAudioSessionControl has 3..11)
	private static final int VOLUME_SET = 3, VOLUME_GET = 4; // ISimpleAudioVolume

	private Pointer enumerator;
	private boolean failed;

	/** Maps a media session's app id to the executable that plays its audio. Empty = unknown. */
	static String exeFor(String appId) {
		String a = appId == null ? "" : appId.toLowerCase(Locale.ROOT);
		if (a.isEmpty() || a.contains("chrome")) {
			return "chrome.exe"; // Chrome's app id is "Chrome" (or chrome.exe for some installs)
		}
		if (a.contains("msedge") || a.contains("microsoftedge")) {
			return "msedge.exe";
		}
		if (a.contains("brave")) {
			return "brave.exe";
		}
		if (a.contains("opera")) {
			return "opera.exe";
		}
		if (a.contains("firefox") || a.contains("308046b0af4a39cb")) {
			return "firefox.exe";
		}
		if (a.contains("spotify")) {
			return "spotify.exe";
		}
		if (a.endsWith(".exe")) {
			int slash = Math.max(a.lastIndexOf('\\'), a.lastIndexOf('/'));
			return a.substring(slash + 1);
		}
		return "";
	}

	private boolean ensure() {
		if (enumerator != null) {
			return true;
		}
		if (failed) {
			return false;
		}
		PointerByReference out = new PointerByReference();
		int hr = Com.fn("ole32", "CoCreateInstance").invokeInt(new Object[] {CLSID_ENUMERATOR, null, CLSCTX_ALL, IID_ENUMERATOR, out});
		if (hr < 0 || out.getValue() == null) {
			failed = true;
			MediaService.debug("volume mixer unavailable (CoCreateInstance 0x" + Integer.toHexString(hr) + ")");
			return false;
		}
		enumerator = out.getValue();
		return true;
	}

	/** Volume 0..1 of the first matching session, -1 if none was found. */
	float get(String exe) {
		float[] result = {-1f};
		visit(exe, vol -> {
			if (result[0] < 0f) {
				FloatByReference v = new FloatByReference();
				if (Com.call(vol, VOLUME_GET, v) >= 0) {
					result[0] = v.getValue();
				}
			}
		});
		return result[0];
	}

	/** Sets every matching session; returns how many were changed. */
	int set(String exe, float level) {
		int[] n = {0};
		visit(exe, vol -> {
			if (Com.call(vol, VOLUME_SET, Float.valueOf(level), null) >= 0) {
				n[0]++;
			}
		});
		if (n[0] == 0) {
			MediaService.debug("no audio session of " + (exe.isEmpty() ? "the playing app" : exe) + " in the volume mixer");
		}
		return n[0];
	}

	private interface VolumeVisitor {
		void accept(Pointer simpleAudioVolume);
	}

	private void visit(String exe, VolumeVisitor visitor) {
		if (exe.isEmpty() || !ensure()) {
			return;
		}
		PointerByReference out = new PointerByReference();
		if (Com.call(enumerator, ENUM_ENDPOINTS, 0 /* eRender */, 1 /* DEVICE_STATE_ACTIVE */, out) < 0 || out.getValue() == null) {
			return;
		}
		Pointer devices = out.getValue();
		try {
			IntByReference count = new IntByReference();
			Com.call(devices, COLLECTION_COUNT, count);
			for (int d = 0; d < count.getValue(); d++) {
				PointerByReference dev = new PointerByReference();
				if (Com.call(devices, COLLECTION_ITEM, d, dev) < 0 || dev.getValue() == null) {
					continue;
				}
				try {
					visitDevice(dev.getValue(), exe, visitor);
				} finally {
					Com.release(dev.getValue());
				}
			}
		} finally {
			Com.release(devices);
		}
	}

	private void visitDevice(Pointer device, String exe, VolumeVisitor visitor) {
		PointerByReference mgr = new PointerByReference();
		if (Com.call(device, DEVICE_ACTIVATE, IID_SESSION_MANAGER2, CLSCTX_ALL, null, mgr) < 0 || mgr.getValue() == null) {
			return;
		}
		Pointer manager = mgr.getValue();
		Pointer sessions = null;
		try {
			PointerByReference en = new PointerByReference();
			if (Com.call(manager, MANAGER2_ENUMERATOR, en) < 0 || en.getValue() == null) {
				return;
			}
			sessions = en.getValue();
			IntByReference count = new IntByReference();
			Com.call(sessions, SESSIONS_COUNT, count);
			for (int i = 0; i < count.getValue(); i++) {
				PointerByReference ctl = new PointerByReference();
				if (Com.call(sessions, SESSIONS_GET, i, ctl) < 0 || ctl.getValue() == null) {
					continue;
				}
				Pointer control = ctl.getValue();
				Pointer control2 = null;
				Pointer volume = null;
				try {
					control2 = Com.query(control, IID_SESSION_CONTROL2);
					if (control2 == null) {
						continue;
					}
					IntByReference pid = new IntByReference();
					if (Com.call(control2, CONTROL2_PROCESS_ID, pid) < 0 || pid.getValue() == 0) {
						continue;
					}
					if (!exe.equalsIgnoreCase(processName(pid.getValue()))) {
						continue;
					}
					volume = Com.query(control, IID_SIMPLE_VOLUME);
					if (volume != null) {
						visitor.accept(volume);
					}
				} finally {
					Com.release(volume);
					Com.release(control2);
					Com.release(control);
				}
			}
		} finally {
			Com.release(sessions);
			Com.release(manager);
		}
	}

	/** Executable file name of a process ("chrome.exe"), "" if it cannot be read. */
	private static String processName(int pid) {
		Pointer h = (Pointer) Com.fn("kernel32", "OpenProcess").invoke(Pointer.class,
				new Object[] {0x1000 /* PROCESS_QUERY_LIMITED_INFORMATION */, 0, pid});
		if (h == null) {
			return "";
		}
		try {
			Memory buf = new Memory(2 * 1024);
			IntByReference size = new IntByReference(1024);
			int ok = Com.fn("kernel32", "QueryFullProcessImageNameW").invokeInt(new Object[] {h, 0, buf, size});
			if (ok == 0) {
				return "";
			}
			String path = Com.utf16(buf, Math.max(0, Math.min(1024, size.getValue())));
			int slash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
			return path.substring(slash + 1);
		} finally {
			Com.fn("kernel32", "CloseHandle").invokeInt(new Object[] {h});
		}
	}

	void close() {
		Com.release(enumerator);
		enumerator = null;
	}
}

package com.hazeline.appearance;

import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Short shareable theme codes like {@code PC1.AeijPfL_9uD_9MldAOij...}.
 *
 * <p>Layout (32 bytes, then URL-safe Base64 without padding = 43 characters):
 * version (1) | accent, panel background, header, header gradient end, text, secondary text,
 * border (7 x 4 bytes ARGB) | flags (bit0 rainbow, bit1 gradient headers, bit2 blur) |
 * rainbow speed x10 (1) | checksum (1, sum of all previous bytes).
 *
 * <p>Codes contain colours and effects only (the Colors tab), not fonts or sounds.
 */
public final class ThemeCode {
	public static final String PREFIX = "PC1.";
	private static final int VERSION = 1;
	private static final int LENGTH = 32;

	private ThemeCode() {
	}

	public static String encode(Appearance a) {
		ByteBuffer buf = ByteBuffer.allocate(LENGTH);
		buf.put((byte) VERSION);
		buf.putInt(a.accent.get());
		buf.putInt(a.panelBackground.get());
		buf.putInt(a.panelHeader.get());
		buf.putInt(a.headerGradientEnd.get());
		buf.putInt(a.text.get());
		buf.putInt(a.textSecondary.get());
		buf.putInt(a.border.get());
		int flags = (a.rainbowAccent.get() ? 1 : 0) | (a.gradientHeader.get() ? 2 : 0) | (a.backgroundBlur.get() ? 4 : 0);
		buf.put((byte) flags);
		buf.put((byte) Math.max(1, Math.min(100, Math.round(a.rainbowSpeed.get() * 10))));
		buf.put(checksum(buf.array(), LENGTH - 1));
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
	}

	/**
	 * Applies a code to {@code a}.
	 *
	 * @throws IllegalArgumentException with a human readable reason if the code is invalid
	 */
	public static void decodeInto(String code, Appearance a) {
		String s = code == null ? "" : code.trim();
		if (!s.startsWith(PREFIX)) {
			throw new IllegalArgumentException("it should start with " + PREFIX);
		}
		byte[] data;
		try {
			data = Base64.getUrlDecoder().decode(s.substring(PREFIX.length()));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("not valid Base64");
		}
		if (data.length != LENGTH) {
			throw new IllegalArgumentException("wrong length (incomplete copy?)");
		}
		if (data[0] != VERSION) {
			throw new IllegalArgumentException("unsupported version " + data[0]);
		}
		if (checksum(data, LENGTH - 1) != data[LENGTH - 1]) {
			throw new IllegalArgumentException("checksum mismatch (typo or incomplete copy?)");
		}
		ByteBuffer buf = ByteBuffer.wrap(data);
		buf.get();
		a.accent.set(buf.getInt());
		a.panelBackground.set(buf.getInt());
		a.panelHeader.set(buf.getInt());
		a.headerGradientEnd.set(buf.getInt());
		a.text.set(buf.getInt());
		a.textSecondary.set(buf.getInt());
		a.border.set(buf.getInt());
		int flags = buf.get();
		a.rainbowAccent.set((flags & 1) != 0);
		a.gradientHeader.set((flags & 2) != 0);
		a.backgroundBlur.set((flags & 4) != 0);
		a.rainbowSpeed.set((buf.get() & 0xFF) / 10.0);
	}

	private static byte checksum(byte[] data, int length) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += data[i] & 0xFF;
		}
		return (byte) sum;
	}
}

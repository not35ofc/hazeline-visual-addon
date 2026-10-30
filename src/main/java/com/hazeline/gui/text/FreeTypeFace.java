package com.hazeline.gui.text;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/**
 * One TrueType face opened with FreeType (the rasteriser Minecraft 1.21.11 itself ships, via
 * LWJGL 3.3.3 {@code lwjgl-freetype}). Used only on the render thread.
 *
 * <p>Glyphs are rendered at an exact (fractional, 1/64 px precision) pixel size with FreeType's <em>light</em>
 * hinting: outlines are snapped to the pixel grid vertically only (baseline, x-height and cap
 * height become crisp) while horizontal shapes and advances stay true to the design, which is
 * what makes fractional (subpixel) pen positions possible. The 8-bit anti-aliased coverage
 * bitmap is returned as is - no gamma or threshold is applied, so thin strokes keep every
 * partially covered pixel.
 */
public final class FreeTypeFace implements AutoCloseable {
	/** {@code FT_LOAD_TARGET_(FT_RENDER_MODE_LIGHT)}; LWJGL names this constant FT_FT_LOAD_TARGET_LIGHT. */
	private static final int LOAD_TARGET_LIGHT = (FreeType.FT_RENDER_MODE_LIGHT & 15) << 16;
	private static final int LOAD_FLAGS = FreeType.FT_LOAD_NO_BITMAP | LOAD_TARGET_LIGHT;

	private final String name;
	private ByteBuffer memory;
	private FT_Face face;
	private int currentSize64 = -1;
	private final Map<Integer, Integer> glyphIndexCache = new HashMap<>();

	private FreeTypeFace(String name, ByteBuffer memory, FT_Face face) {
		this.name = name;
		this.memory = memory;
		this.face = face;
	}

	/** Opens {@code bytes} (a .ttf file) with {@code library}. The bytes are copied to native memory. */
	public static FreeTypeFace open(long library, byte[] bytes, String name) {
		ByteBuffer memory = MemoryUtil.memAlloc(bytes.length);
		memory.put(bytes).flip();
		FT_Face face = null;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer out = stack.mallocPointer(1);
			check(FreeType.FT_New_Memory_Face(library, memory, 0L, out), "opening " + name);
			face = FT_Face.create(out.get(0));
			check(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "selecting the Unicode charmap of " + name);
			return new FreeTypeFace(name, memory, face);
		} catch (RuntimeException e) {
			if (face != null) {
				FreeType.FT_Done_Face(face);
			}
			MemoryUtil.memFree(memory);
			throw e;
		}
	}

	public String name() {
		return name;
	}

	/** Glyph index for a Unicode code point, 0 (.notdef) if the font has no glyph for it. */
	public int glyphIndex(int codepoint) {
		Integer cached = glyphIndexCache.get(codepoint);
		if (cached != null) {
			return cached;
		}
		int index = face == null ? 0 : FreeType.FT_Get_Char_Index(face, codepoint);
		glyphIndexCache.put(codepoint, index);
		return index;
	}

	/**
	 * Renders {@code glyph} at {@code size64}/64 pixels per em, shifted right by {@code subpixel64}/64
	 * of a pixel (0..63) before rasterising, so a glyph whose pen position has a fractional part
	 * is rendered for exactly that position instead of being blurred or rounded later.
	 *
	 * @param emboldenPx synthetic emboldening in pixels (only used when a font has no bold file)
	 */
	public RasterGlyph render(int glyph, int size64, int subpixel64, int emboldenPx) {
		if (face == null) {
			throw new IllegalStateException("Face " + name + " is closed");
		}
		if (size64 != currentSize64) {
			// 26.6 fixed point at 72 dpi: the character size in points equals the size in pixels.
			check(FreeType.FT_Set_Char_Size(face, 0L, size64, 72, 72), "setting size " + size64 / 64f + " px of " + name);
			currentSize64 = size64;
		}
		check(FreeType.FT_Load_Glyph(face, glyph, LOAD_FLAGS), "loading glyph " + glyph + " of " + name);
		FT_GlyphSlot slot = face.glyph();
		if (slot == null) {
			throw new IllegalStateException("FreeType returned no glyph slot for glyph " + glyph + " of " + name);
		}
		if (slot.format() == FreeType.FT_GLYPH_FORMAT_OUTLINE) {
			if (emboldenPx > 0) {
				FreeType.FT_Outline_EmboldenXY(slot.outline(), emboldenPx * 64L, 0L);
			}
			if (subpixel64 != 0) {
				FreeType.FT_Outline_Translate(slot.outline(), subpixel64, 0L);
			}
		}
		check(FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_LIGHT), "rendering glyph " + glyph + " of " + name);

		FT_Bitmap bitmap = slot.bitmap();
		int width = bitmap.width();
		int rows = bitmap.rows();
		if (width <= 0 || rows <= 0) {
			return new RasterGlyph(0, 0, 0, 0, new byte[0]);
		}
		if (bitmap.pixel_mode() != FreeType.FT_PIXEL_MODE_GRAY) {
			throw new IllegalStateException("Glyph " + glyph + " of " + name + " was not rendered as 8-bit grayscale");
		}
		int pitch = bitmap.pitch();
		int absPitch = Math.abs(pitch);
		ByteBuffer buffer = bitmap.buffer(absPitch * rows);
		if (buffer == null) {
			throw new IllegalStateException("Glyph " + glyph + " of " + name + " has no bitmap");
		}
		byte[] coverage = new byte[width * rows];
		for (int y = 0; y < rows; y++) {
			// A negative pitch means the rows are stored bottom-up.
			int srcRow = pitch >= 0 ? y : rows - 1 - y;
			buffer.get(srcRow * absPitch, coverage, y * width, width);
		}
		return new RasterGlyph(width, rows, slot.bitmap_left(), slot.bitmap_top(), coverage);
	}

	@Override
	public void close() {
		if (face != null) {
			FreeType.FT_Done_Face(face);
			face = null;
		}
		if (memory != null) {
			MemoryUtil.memFree(memory);
			memory = null;
		}
	}

	private static void check(int error, String what) {
		if (error != 0) {
			String message = FreeType.FT_Error_String(error);
			throw new IllegalStateException("FreeType error " + error + (message != null ? " (" + message + ")" : "") + " while " + what);
		}
	}

	/** Creates a FreeType library instance (one per {@link TextEngine}). */
	public static long createLibrary() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer out = stack.mallocPointer(1);
			check(FreeType.FT_Init_FreeType(out), "initialising FreeType");
			return out.get(0);
		}
	}

	public static void destroyLibrary(long library) {
		if (library != 0L) {
			FreeType.FT_Done_FreeType(library);
		}
	}

	/** FreeType version string, for the log. */
	public static String version(long library) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			var major = stack.mallocInt(1);
			var minor = stack.mallocInt(1);
			var patch = stack.mallocInt(1);
			FreeType.FT_Library_Version(library, major, minor, patch);
			return major.get(0) + "." + minor.get(0) + "." + patch.get(0);
		}
	}

	/** A rendered glyph: coverage bitmap ({@code width*height} bytes, rows top-down) and its offset from the pen. */
	public record RasterGlyph(int width, int height, int left, int top, byte[] coverage) {
		public boolean isEmpty() {
			return width == 0 || height == 0;
		}
	}
}

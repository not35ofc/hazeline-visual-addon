package com.hazeline.gui.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link TextEngine.TextLayout} into textured quads in screen pixels, grouped by atlas
 * page (one run = one GUI element = one batch). The quads are relative to the string origin
 * (x = 0 at the start, y = 0 on the baseline), so they are built once per layout and only offset
 * when drawn. Minecraft-independent so the exact same code is exercised by the offline tests.
 */
public final class TextQuads {
	/** Floats per quad in {@link Run#quads}: x0, y0, x1, y1, u0, v0, u1, v1. */
	public static final int STRIDE = 8;

	private TextQuads() {
	}

	/**
	 * Quads of one atlas page, relative to the string origin. {@code minX..maxY} is their
	 * bounding box (for the GUI layer tree and culling).
	 */
	public record Run(GlyphAtlas atlas, int page, float[] quads, int count, float minX, float minY, float maxX, float maxY) {
	}

	/** Atlas page identity ({@link GlyphAtlas} has identity equality). */
	private record PageKey(GlyphAtlas atlas, int page) {
	}

	static List<Run> build(TextEngine.TextLayout layout) {
		Map<PageKey, List<TextEngine.PlacedGlyph>> byPage = new LinkedHashMap<>();
		for (TextEngine.PlacedGlyph pg : layout.glyphs()) {
			byPage.computeIfAbsent(new PageKey(pg.atlas(), pg.glyph().page()), k -> new ArrayList<>()).add(pg);
		}
		List<Run> runs = new ArrayList<>(byPage.size());
		for (Map.Entry<PageKey, List<TextEngine.PlacedGlyph>> entry : byPage.entrySet()) {
			GlyphAtlas atlas = entry.getKey().atlas();
			int page = entry.getKey().page();
			List<TextEngine.PlacedGlyph> glyphs = entry.getValue();
			float pageSize = atlas.pageSize(page);
			float[] quads = new float[glyphs.size() * STRIDE];
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
			int n = 0;
			for (TextEngine.PlacedGlyph pg : glyphs) {
				GlyphAtlas.AtlasGlyph g = pg.glyph();
				// Whole screen pixels: x from floor(pen) + bitmap left, y from the baseline - bitmap top.
				float x0 = pg.x();
				float y0 = pg.y();
				float x1 = x0 + g.width();
				float y1 = y0 + g.height();
				int o = n * STRIDE;
				quads[o] = x0;
				quads[o + 1] = y0;
				quads[o + 2] = x1;
				quads[o + 3] = y1;
				// UVs on texel edges: with 1:1 quads every pixel centre samples a texel centre exactly.
				quads[o + 4] = g.x() / pageSize;
				quads[o + 5] = g.y() / pageSize;
				quads[o + 6] = (g.x() + g.width()) / pageSize;
				quads[o + 7] = (g.y() + g.height()) / pageSize;
				n++;
				minX = Math.min(minX, x0);
				minY = Math.min(minY, y0);
				maxX = Math.max(maxX, x1);
				maxY = Math.max(maxY, y1);
			}
			runs.add(new Run(atlas, page, quads, n, minX, minY, maxX, maxY));
		}
		return List.copyOf(runs);
	}
}

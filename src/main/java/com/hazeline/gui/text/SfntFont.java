package com.hazeline.gui.text;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The parts of a TrueType file the text layout needs, read straight from the font's tables
 * (no FreeType, so widths can be measured without rasterising anything):
 * <ul>
 *   <li>{@code head}: units per em;</li>
 *   <li>{@code hhea} / {@code OS/2}: ascender, descender, line gap (OS/2 typo metrics when the
 *       font sets USE_TYPO_METRICS, else hhea - the same rule browsers and FreeType use);</li>
 *   <li>{@code hmtx}: the unhinted advance width of every glyph;</li>
 *   <li>{@code GPOS}: pair kerning ({@link Kerning}).</li>
 * </ul>
 * All values are in font units; the layout scales them by {@code ppem / unitsPerEm}.
 *
 * <p>Why GPOS: none of the five bundled fonts has a legacy {@code kern} table, so FreeType's
 * {@code FT_Get_Kerning} returns 0 for every pair. Their kerning lives in GPOS PairPos lookups
 * (Inter wraps them in extension lookups), which FreeType does not apply on its own.
 */
public final class SfntFont {
	public final int unitsPerEm;
	public final int ascender;
	/** Negative (below the baseline), as stored in the font. */
	public final int descender;
	public final int lineGap;
	public final int numGlyphs;
	private final int[] advances;
	public final Kerning kerning;

	private SfntFont(int unitsPerEm, int ascender, int descender, int lineGap, int numGlyphs, int[] advances, Kerning kerning) {
		this.unitsPerEm = unitsPerEm;
		this.ascender = ascender;
		this.descender = descender;
		this.lineGap = lineGap;
		this.numGlyphs = numGlyphs;
		this.advances = advances;
		this.kerning = kerning;
	}

	/** Unhinted advance width of {@code glyph} in font units. */
	public int advance(int glyph) {
		if (glyph < 0 || glyph >= advances.length) {
			return 0;
		}
		return advances[glyph];
	}

	public static SfntFont parse(byte[] bytes) {
		ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		int version = data.getInt(0);
		if (version != 0x00010000 && version != 0x74727565 /* 'true' */) {
			throw new IllegalArgumentException("Not a TrueType font (sfnt version 0x" + Integer.toHexString(version) + ")");
		}
		Map<String, int[]> tables = new HashMap<>();
		int numTables = u16(data, 4);
		for (int i = 0; i < numTables; i++) {
			int rec = 12 + i * 16;
			String tag = new String(new char[] {(char) data.get(rec), (char) data.get(rec + 1), (char) data.get(rec + 2), (char) data.get(rec + 3)});
			tables.put(tag, new int[] {data.getInt(rec + 8), data.getInt(rec + 12)});
		}
		int head = require(tables, "head");
		int hhea = require(tables, "hhea");
		int hmtx = require(tables, "hmtx");
		int maxp = require(tables, "maxp");

		int unitsPerEm = u16(data, head + 18);
		int numGlyphs = u16(data, maxp + 4);
		int ascender = s16(data, hhea + 4);
		int descender = s16(data, hhea + 6);
		int lineGap = s16(data, hhea + 8);
		int numberOfHMetrics = u16(data, hhea + 34);

		int[] os2 = tables.get("OS/2");
		if (os2 != null && os2[1] >= 74) {
			int o = os2[0];
			int fsSelection = u16(data, o + 62);
			boolean useTypoMetrics = (fsSelection & (1 << 7)) != 0;
			if (useTypoMetrics) {
				ascender = s16(data, o + 68);
				descender = s16(data, o + 70);
				lineGap = s16(data, o + 72);
			}
		}

		int[] advances = new int[numGlyphs];
		int last = 0;
		for (int g = 0; g < numGlyphs; g++) {
			if (g < numberOfHMetrics) {
				last = u16(data, hmtx + g * 4);
			}
			advances[g] = last;
		}

		int[] gpos = tables.get("GPOS");
		Kerning kerning = gpos == null ? Kerning.NONE : Kerning.parse(data, gpos[0]);
		return new SfntFont(unitsPerEm, ascender, descender, lineGap, numGlyphs, advances, kerning);
	}

	private static int require(Map<String, int[]> tables, String tag) {
		int[] t = tables.get(tag);
		if (t == null) {
			throw new IllegalArgumentException("Font has no '" + tag + "' table");
		}
		return t[0];
	}

	static int u16(ByteBuffer b, int at) {
		return b.getShort(at) & 0xFFFF;
	}

	static int s16(ByteBuffer b, int at) {
		return b.getShort(at);
	}

	static String tag(ByteBuffer b, int at) {
		return new String(new char[] {(char) b.get(at), (char) b.get(at + 1), (char) b.get(at + 2), (char) b.get(at + 3)});
	}

	/**
	 * OpenType GPOS pair kerning (lookup type 2, both formats, also inside type 9 extension
	 * lookups), applied the way a shaper applies the {@code kern} feature for Latin text:
	 * the lookups of the 'kern' feature of script 'latn' (else 'DFLT'), in lookup-list order;
	 * within one lookup the first subtable that applies wins; adjustments of all lookups add up.
	 * Results are cached per glyph pair.
	 */
	public static final class Kerning {
		static final Kerning NONE = new Kerning(null, List.of());

		private final ByteBuffer data;
		private final List<int[]> subtablesPerLookup; // absolute offsets of PairPos subtables per lookup
		private final Map<Long, Adjustment> cache = new HashMap<>();

		private Kerning(ByteBuffer data, List<int[]> subtablesPerLookup) {
			this.data = data;
			this.subtablesPerLookup = subtablesPerLookup;
		}

		/** Pair adjustment in font units: added to the first glyph's advance, and placement/advance of the second. */
		public record Adjustment(int advance1, int placement2, int advance2) {
			static final Adjustment ZERO = new Adjustment(0, 0, 0);

			public boolean isZero() {
				return advance1 == 0 && placement2 == 0 && advance2 == 0;
			}
		}

		public boolean isEmpty() {
			return subtablesPerLookup.isEmpty();
		}

		public int lookupCount() {
			return subtablesPerLookup.size();
		}

		public Adjustment get(int left, int right) {
			if (subtablesPerLookup.isEmpty()) {
				return Adjustment.ZERO;
			}
			long key = ((long) left << 32) | (right & 0xFFFFFFFFL);
			Adjustment cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
			int a1 = 0, p2 = 0, a2 = 0;
			for (int[] subtables : subtablesPerLookup) {
				for (int st : subtables) {
					int[] v = applyPairPos(st, left, right);
					if (v != null) {
						a1 += v[0];
						p2 += v[1];
						a2 += v[2];
						break;
					}
				}
			}
			Adjustment result = (a1 == 0 && p2 == 0 && a2 == 0) ? Adjustment.ZERO : new Adjustment(a1, p2, a2);
			cache.put(key, result);
			return result;
		}

		/** {xAdvance of value1, xPlacement of value2, xAdvance of value2}, or null if the subtable does not apply. */
		private int[] applyPairPos(int st, int left, int right) {
			int format = u16(data, st);
			int coverageIndex = coverageIndex(st + u16(data, st + 2), left);
			if (coverageIndex < 0) {
				return null;
			}
			int vf1 = u16(data, st + 4);
			int vf2 = u16(data, st + 6);
			int size1 = valueRecordSize(vf1);
			int size2 = valueRecordSize(vf2);
			if (format == 1) {
				int pairSetCount = u16(data, st + 8);
				if (coverageIndex >= pairSetCount) {
					return null;
				}
				int pairSet = st + u16(data, st + 10 + coverageIndex * 2);
				int count = u16(data, pairSet);
				int recordSize = 2 + size1 + size2;
				int lo = 0, hi = count - 1;
				while (lo <= hi) {
					int mid = (lo + hi) >>> 1;
					int rec = pairSet + 2 + mid * recordSize;
					int second = u16(data, rec);
					if (second < right) {
						lo = mid + 1;
					} else if (second > right) {
						hi = mid - 1;
					} else {
						return values(rec + 2, vf1, rec + 2 + size1, vf2);
					}
				}
				return null;
			}
			if (format == 2) {
				int classDef1 = st + u16(data, st + 8);
				int classDef2 = st + u16(data, st + 10);
				int class1Count = u16(data, st + 12);
				int class2Count = u16(data, st + 14);
				int c1 = classOf(classDef1, left);
				int c2 = classOf(classDef2, right);
				if (c1 >= class1Count || c2 >= class2Count) {
					return null;
				}
				int recordSize = size1 + size2;
				int rec = st + 16 + (c1 * class2Count + c2) * recordSize;
				return values(rec, vf1, rec + size1, vf2);
			}
			return null;
		}

		private int[] values(int rec1, int vf1, int rec2, int vf2) {
			return new int[] {field(rec1, vf1, 0x0004), field(rec2, vf2, 0x0001), field(rec2, vf2, 0x0004)};
		}

		/** Reads one field of a ValueRecord (fields are int16, present in bit order). */
		private int field(int rec, int format, int bit) {
			if ((format & bit) == 0) {
				return 0;
			}
			int index = Integer.bitCount(format & (bit - 1));
			return s16(data, rec + index * 2);
		}

		private static int valueRecordSize(int format) {
			return Integer.bitCount(format & 0xFF) * 2;
		}

		private int coverageIndex(int coverage, int glyph) {
			int format = u16(data, coverage);
			if (format == 1) {
				int count = u16(data, coverage + 2);
				int lo = 0, hi = count - 1;
				while (lo <= hi) {
					int mid = (lo + hi) >>> 1;
					int g = u16(data, coverage + 4 + mid * 2);
					if (g < glyph) {
						lo = mid + 1;
					} else if (g > glyph) {
						hi = mid - 1;
					} else {
						return mid;
					}
				}
				return -1;
			}
			if (format == 2) {
				int count = u16(data, coverage + 2);
				int lo = 0, hi = count - 1;
				while (lo <= hi) {
					int mid = (lo + hi) >>> 1;
					int rec = coverage + 4 + mid * 6;
					int start = u16(data, rec);
					int end = u16(data, rec + 2);
					if (glyph < start) {
						hi = mid - 1;
					} else if (glyph > end) {
						lo = mid + 1;
					} else {
						return u16(data, rec + 4) + glyph - start;
					}
				}
				return -1;
			}
			return -1;
		}

		private int classOf(int classDef, int glyph) {
			int format = u16(data, classDef);
			if (format == 1) {
				int start = u16(data, classDef + 2);
				int count = u16(data, classDef + 4);
				int i = glyph - start;
				return i >= 0 && i < count ? u16(data, classDef + 6 + i * 2) : 0;
			}
			if (format == 2) {
				int count = u16(data, classDef + 2);
				int lo = 0, hi = count - 1;
				while (lo <= hi) {
					int mid = (lo + hi) >>> 1;
					int rec = classDef + 4 + mid * 6;
					int start = u16(data, rec);
					int end = u16(data, rec + 2);
					if (glyph < start) {
						hi = mid - 1;
					} else if (glyph > end) {
						lo = mid + 1;
					} else {
						return u16(data, rec + 4);
					}
				}
			}
			return 0;
		}

		static Kerning parse(ByteBuffer data, int gpos) {
			int scriptList = gpos + u16(data, gpos + 4);
			int featureList = gpos + u16(data, gpos + 6);
			int lookupList = gpos + u16(data, gpos + 8);

			// Feature indices of the 'latn' (else 'DFLT') script's default language system.
			int langSys = findDefaultLangSys(data, scriptList, "latn");
			if (langSys < 0) {
				langSys = findDefaultLangSys(data, scriptList, "DFLT");
			}
			int featureCount = u16(data, featureList);
			TreeSet<Integer> lookups = new TreeSet<>();
			if (langSys >= 0) {
				int required = u16(data, langSys + 2);
				int count = u16(data, langSys + 4);
				List<Integer> features = new ArrayList<>();
				if (required != 0xFFFF) {
					features.add(required);
				}
				for (int i = 0; i < count; i++) {
					features.add(u16(data, langSys + 6 + i * 2));
				}
				for (int f : features) {
					if (f < featureCount) {
						addKernLookups(data, featureList, f, lookups);
					}
				}
			} else {
				for (int f = 0; f < featureCount; f++) {
					addKernLookups(data, featureList, f, lookups);
				}
			}

			int lookupCount = u16(data, lookupList);
			List<int[]> result = new ArrayList<>();
			for (int index : lookups) {
				if (index >= lookupCount) {
					continue;
				}
				int lookup = lookupList + u16(data, lookupList + 2 + index * 2);
				int type = u16(data, lookup);
				int subCount = u16(data, lookup + 4);
				List<Integer> pairPos = new ArrayList<>();
				for (int s = 0; s < subCount; s++) {
					int sub = lookup + u16(data, lookup + 6 + s * 2);
					int subType = type;
					if (type == 9) { // extension: format(2) extensionLookupType(2) extensionOffset(4)
						subType = u16(data, sub + 2);
						sub = sub + data.getInt(sub + 4);
					}
					if (subType == 2) {
						pairPos.add(sub);
					}
				}
				if (!pairPos.isEmpty()) {
					result.add(pairPos.stream().mapToInt(Integer::intValue).toArray());
				}
			}
			return result.isEmpty() ? NONE : new Kerning(data, result);
		}

		private static void addKernLookups(ByteBuffer data, int featureList, int featureIndex, TreeSet<Integer> out) {
			int rec = featureList + 2 + featureIndex * 6;
			if (!"kern".equals(tag(data, rec))) {
				return;
			}
			int feature = featureList + u16(data, rec + 4);
			int count = u16(data, feature + 2);
			for (int i = 0; i < count; i++) {
				out.add(u16(data, feature + 4 + i * 2));
			}
		}

		private static int findDefaultLangSys(ByteBuffer data, int scriptList, String scriptTag) {
			int count = u16(data, scriptList);
			for (int i = 0; i < count; i++) {
				int rec = scriptList + 2 + i * 6;
				if (scriptTag.equals(tag(data, rec))) {
					int script = scriptList + u16(data, rec + 4);
					int defaultLangSys = u16(data, script);
					if (defaultLangSys != 0) {
						return script + defaultLangSys;
					}
					int langSysCount = u16(data, script + 2);
					if (langSysCount > 0) {
						return script + u16(data, script + 4 + 4);
					}
				}
			}
			return -1;
		}
	}
}

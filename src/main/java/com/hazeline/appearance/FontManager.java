package com.hazeline.appearance;

import java.util.ArrayList;
import java.util.List;

import com.hazeline.Hazeline;
import com.hazeline.setting.ChoiceSetting;

/**
 * The five fonts the click GUI can use.
 *
 * <p>The click GUI does not use Minecraft's font system for its text: the .ttf files in
 * {@code assets/hazeline/font/} are rasterised by {@link com.hazeline.gui.text.TextRenderer}
 * at the real screen pixel size (see there for the pipeline). Each font ships its regular file and
 * the bold file of the same family (real bold weight instead of a fake one).
 *
 * <p>{@code emAt9} is the em size in GUI pixels at font size 9. It differs per font so that the
 * five fonts look equally big at the same setting (their designs have different x-heights).
 *
 * <p>Bundled files (official, unmodified static builds, SIL OFL 1.1, licences in
 * assets/hazeline/font/licenses/): Inter Medium/Bold (rsms/inter v4.1), Poppins Medium/Bold
 * (google/fonts), Comfortaa SemiBold/Bold (googlefonts/comfortaa), Nunito SemiBold/Bold
 * (googlefonts/nunito), JetBrains Mono Medium/Bold (JetBrains/JetBrainsMono).
 */
public final class FontManager {
	/** One selectable font. */
	public record FontEntry(String id, String label, String description, String regularFile, String boldFile, double emAt9) {
	}

	/** The only selectable fonts, in picker order. */
	public static final List<FontEntry> FONTS = List.of(
			new FontEntry("hazeline:inter_medium", "Inter Medium", "Clean modern UI font",
					"inter_medium.ttf", "inter_bold.ttf", 8.5),
			new FontEntry("hazeline:poppins_medium", "Poppins Medium", "Geometric & round",
					"poppins_medium.ttf", "poppins_bold.ttf", 9.0),
			new FontEntry("hazeline:comfortaa_semibold", "Comfortaa SemiBold", "Soft rounded, bubbly",
					"comfortaa_semibold.ttf", "comfortaa_bold.ttf", 8.0),
			new FontEntry("hazeline:nunito_semibold", "Nunito SemiBold", "Rounded, friendly but readable",
					"nunito_semibold.ttf", "nunito_bold.ttf", 9.0),
			new FontEntry("hazeline:jetbrains_mono_medium", "JetBrains Mono Medium", "Monospace, techy look",
					"jetbrains_mono_medium.ttf", "jetbrains_mono_bold.ttf", 8.5));

	/** Default GUI font (also the fallback for characters another font lacks). */
	public static final String DEFAULT_ID = FONTS.get(0).id();

	private static final FontManager INSTANCE = new FontManager();

	private FontManager() {
	}

	public static FontManager get() {
		return INSTANCE;
	}

	public List<ChoiceSetting.Choice> choices() {
		List<ChoiceSetting.Choice> list = new ArrayList<>();
		for (FontEntry e : FONTS) {
			list.add(new ChoiceSetting.Choice(e.id(), e.label()));
		}
		return list;
	}

	public static boolean isBundled(String id) {
		return entry(id) != null;
	}

	private static FontEntry entry(String id) {
		for (FontEntry e : FONTS) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** Em size in GUI pixels at font size 9 (unknown ids: the default font's). */
	public static double emAt9(String id) {
		FontEntry e = entry(id);
		return e != null ? e.emAt9() : FONTS.get(0).emAt9();
	}

	public static String label(String id) {
		FontEntry e = entry(id);
		return e != null ? e.label() : id;
	}

	/** The selected font id. */
	public static String currentId() {
		String id = Appearance.get().font.get();
		return isBundled(id) ? id : DEFAULT_ID;
	}

	/** The "Font size" setting (9 = vanilla size). */
	public static double fontSizeSetting() {
		return Appearance.get().fontSize.get();
	}

	/**
	 * Called once after the config is loaded: a saved font that is not one of the five (e.g. Great
	 * Vibes from 1.4.0, the vanilla font, or a custom font) is switched to Inter Medium.
	 */
	public static void migrateSavedFont(ChoiceSetting setting) {
		String saved = setting.get();
		if (!isBundled(saved)) {
			setting.set(DEFAULT_ID);
			Hazeline.LOGGER.info("Font '{}' is no longer available; switched the GUI font to {}", saved, FONTS.get(0).label());
		}
	}
}

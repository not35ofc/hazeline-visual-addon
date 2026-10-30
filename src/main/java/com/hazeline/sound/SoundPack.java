package com.hazeline.sound;

/**
 * The keyboard sound packs of the Sounds and Clicks module (1.35.0). Every pack has five press
 * variants (Minecraft picks one at random per press), a release sound and its own Space, Enter and
 * Backspace press/release sounds: sound events {@code hazeline:kb.<id>.press}, {@code .release},
 * {@code .space}, {@code .space_release}, ... in {@code assets/hazeline/sounds.json}.
 *
 * <p>All recordings are from kbsim by Thomas Lai (MIT, see {@code licenses/SOUNDS-kbsim-MIT.txt});
 * they were converted by {@code tools/sounds/make_packs.py}. {@link #CLICKY} and {@link #CREAMY} are
 * the packs Hazeline already had: they keep their hand-made click GUI sounds
 * ({@code sounds/clicky|creamy/<action>.ogg}, press + release in one file) for the Click GUI row.
 */
public enum SoundPack {
	CLICKY("clicky", "Clicky (MX Blue)", true),
	CREAMY("creamy", "Creamy (NK Cream)", true),
	ALPS_BLUE("alps_blue", "Clicky: Alps Blue", false),
	BOX_NAVY("box_navy", "Clicky: Box Navy", false),
	BUCKLING("buckling", "Clicky: Buckling Spring", false),
	ALPACA("alpaca", "Creamy: Alpaca", false),
	BLACK_INK("black_ink", "Creamy: Black Ink", false),
	TEALIOS("tealios", "Creamy: Tealios", false);

	private final String id;
	private final String label;
	private final boolean guiSounds;

	SoundPack(String id, String label, boolean guiSounds) {
		this.id = id;
		this.label = label;
		this.guiSounds = guiSounds;
	}

	public String id() {
		return id;
	}

	/** Whether the pack has its own click GUI action sounds (open.ogg, click.ogg, ...). */
	public boolean hasGuiSounds() {
		return guiSounds;
	}

	@Override
	public String toString() {
		return label;
	}

	/** Old Appearance "Sound pack" value (before 1.35.0) -> pack, or null for "none". */
	public static SoundPack fromLegacy(String value) {
		if (value == null || value.equals("none")) {
			return null;
		}
		return value.equals("creamy") ? CREAMY : CLICKY; // clicky, minecraft and removed custom packs -> Clicky
	}
}

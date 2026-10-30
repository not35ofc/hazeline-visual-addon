package com.hazeline.module.modules.render;

/** Colour sets for Custom Block Outlines. CUSTOM uses the module's own colours, RAINBOW the full hue circle. */
public enum OutlinePalette {
	CUSTOM("Custom"),
	RAINBOW("Rainbow"),
	SUNSET("Sunset", 0xFFFF5E62, 0xFFFF9966, 0xFFFFD86F, 0xFFC86DD7),
	OCEAN("Ocean", 0xFF00C6FF, 0xFF0072FF, 0xFF22D3EE, 0xFF14B8A6),
	AURORA("Aurora", 0xFF00F5A0, 0xFF00D9F5, 0xFF7F5AF0, 0xFF2CB67D),
	FIRE("Fire", 0xFFFF1E00, 0xFFFF7A00, 0xFFFFD000, 0xFFFF4D00),
	CANDY("Candy", 0xFFFF6FD8, 0xFF3813C2, 0xFF7EE8FA, 0xFFFFB7E5),
	NEON("Neon", 0xFF39FF14, 0xFF00FFFF, 0xFFFF00FF, 0xFFFFFF00),
	ICE("Ice", 0xFFFFFFFF, 0xFFA5F3FC, 0xFF60A5FA, 0xFFC4B5FD),
	FOREST("Forest", 0xFF14532D, 0xFF22C55E, 0xFFA3E635, 0xFF65A30D),
	GALAXY("Galaxy", 0xFF1E1B4B, 0xFF7C3AED, 0xFFEC4899, 0xFF38BDF8);

	private final String label;
	private final int[] colors;

	OutlinePalette(String label, int... colors) {
		this.label = label;
		this.colors = colors;
	}

	/** The preset colours (a copy). Empty for CUSTOM and RAINBOW. */
	public int[] colors() {
		return colors.clone();
	}

	@Override
	public String toString() {
		return label;
	}
}

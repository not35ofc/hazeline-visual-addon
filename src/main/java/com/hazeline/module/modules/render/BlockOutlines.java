package com.hazeline.module.modules.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;

import com.hazeline.Hazeline;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Custom Block Outlines: replaces the vanilla outline of the block you are looking at.
 *
 * <h2>How (1.21.11, Yarn, verified against the bytecode)</h2>
 * {@code WorldRenderer#renderTargetBlockOutline(Immediate, MatrixStack, boolean translucent, WorldRenderState)}
 * draws {@code state.outlineRenderState} (pos + the block's outline shape, which is the visual shape -
 * e.g. the hopper funnel - not the collision box). {@code BlockOutlineMixin} cancels it at HEAD while
 * this module is on and {@link OutlineRenderer} draws instead, into the same buffers vanilla uses:
 * {@code RenderLayers.lines()} (per-vertex colour + line width) and {@code RenderLayers.debugQuads()}
 * (translucent, no culling) for the fill. Anything that goes wrong logs once and falls back to vanilla
 * for the rest of the session.
 *
 * <p>Break progress comes from {@code ClientPlayerInteractionManager.currentBreakingProgress}
 * (opened with the access widener), interpolated between ticks with
 * {@code BlockState#calcBlockBreakingDelta} - the exact amount vanilla adds every tick - so it moves
 * smoothly and reaches 100 % when the block breaks.
 */
public class BlockOutlines extends Module {

	public enum Mode {
		OUTLINE("Outline"), FILL("Fill"), BOTH("Outline + Fill");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		public boolean outline() {
			return this != FILL;
		}

		public boolean fill() {
			return this != OUTLINE;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum ShapeMode {
		BLOCK_SHAPE("Block shape"), FULL_BLOCK("Full block");

		private final String label;

		ShapeMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Animation {
		STATIC("Static"), FADE("Fade"), WAVE("Wave");

		private final String label;

		Animation(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum BreakEffect {
		OFF("Off"), GROW("Grow"), SHRINK("Shrink"), FILL_UP("Fill up");

		private final String label;

		BreakEffect(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum OnTop {
		NEVER("Never"), WHILE_BREAKING("While breaking"), ALWAYS("Always");

		private final String label;

		OnTop(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static final int MAX_COLORS = 6;
	/** Default custom colours: blue, green, purple, pink, orange, yellow. */
	private static final int[] DEFAULT_COLORS = {0xFF3B82F6, 0xFF22C55E, 0xFFA855F7, 0xFFEC4899, 0xFFF97316, 0xFFFACC15};

	// ------------------------------------------------------------------ look
	public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode",
			"Outline = only the edges, Fill = see-through faces, or both", Mode.OUTLINE));
	public final EnumSetting<ShapeMode> shape = add(new EnumSetting<>("Shape",
			"Block shape follows what the block looks like (a hopper keeps its funnel, a slab stays half), Full block always draws the whole 1x1x1 cube",
			ShapeMode.BLOCK_SHAPE));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width",
			"Outline thickness, 1 = vanilla thickness", 1.5, 0.5, 6.0, 0.1), () -> mode.get().outline());
	public final NumberSetting outlineOpacity = add(new NumberSetting("Outline opacity",
			"How see-through the outline is (%)", 100, 5, 100, 1), () -> mode.get().outline());
	public final NumberSetting fillOpacity = add(new NumberSetting("Fill opacity",
			"How see-through the fill is (%)", 25, 2, 100, 1),
			() -> mode.get().fill() || this.breakEffect.get() == BreakEffect.FILL_UP);
	public final EnumSetting<OnTop> onTop = add(new EnumSetting<>("Draw on top",
			"Draw the outline over the block and its textures (texture packs can't hide it). While breaking = only while the break animation runs, so the growing / shrinking box inside the block stays visible",
			OnTop.WHILE_BREAKING));

	// ------------------------------------------------------------------ colours
	public final EnumSetting<OutlinePalette> palette = add(new EnumSetting<>("Colours",
			"Custom = your own colours below, Rainbow, or a ready-made colour set", OutlinePalette.CUSTOM));
	public final NumberSetting colorCount = add(new NumberSetting("Colour count",
			"How many of your colours to use (2 or more blend smoothly into each other)", 2, 1, MAX_COLORS, 1),
			() -> palette.get() == OutlinePalette.CUSTOM);
	public final ColorSetting[] colors = createColorSettings();
	public final NumberSetting rainbowSaturation = add(new NumberSetting("Rainbow saturation",
			"0 = white, 100 = full colour (%)", 85, 0, 100, 1), () -> palette.get() == OutlinePalette.RAINBOW);
	public final NumberSetting rainbowBrightness = add(new NumberSetting("Rainbow brightness",
			"(%)", 100, 10, 100, 1), () -> palette.get() == OutlinePalette.RAINBOW);
	public final EnumSetting<Animation> animation = add(new EnumSetting<>("Animation",
			"Static = colours stay in place, Fade = the whole outline smoothly changes colour, Wave = the colours travel around the outline",
			Animation.WAVE), this::multiColor);
	public final NumberSetting speed = add(new NumberSetting("Speed",
			"How fast the colours move (1 = one full colour cycle every 4 seconds)", 1.0, 0.1, 5.0, 0.05),
			() -> multiColor() && animation.get() != Animation.STATIC);
	public final NumberSetting spread = add(new NumberSetting("Colour spread",
			"How many times the colour set repeats around the outline", 1.0, 0.25, 4.0, 0.05),
			() -> multiColor() && animation.get() != Animation.FADE);
	public final BooleanSetting reverse = add(new BooleanSetting("Reverse direction",
			"Moves the colours the other way round", false),
			() -> multiColor() && animation.get() != Animation.STATIC);

	// ------------------------------------------------------------------ breaking
	public final EnumSetting<BreakEffect> breakEffect = add(new EnumSetting<>("Break animation",
			"Follows the mining time: Grow = the outline grows to full size, Shrink = shrinks away, Fill up = a fill rises from the bottom",
			BreakEffect.GROW));
	public final BooleanSetting breakColor = add(new BooleanSetting("Break colour",
			"Blend towards the finished colour while mining", false));
	public final ColorSetting finishedColor = add(new ColorSetting("Finished colour",
			"Colour reached when the block breaks", 0xFFEF4444), breakColor::get);

	private static BlockOutlines instance;
	private static boolean failed;

	public BlockOutlines() {
		super("Custom Block Outlines", "Fully customisable outline for the block you look at", Category.RENDER);
		instance = this;
	}

	/**
	 * Runs as a field initialiser between "Colour count" and "Rainbow saturation", so Colour 1..6 are
	 * registered (and shown) right under "Colour count". Only the first "Colour count" are visible.
	 */
	private ColorSetting[] createColorSettings() {
		ColorSetting[] out = new ColorSetting[MAX_COLORS];
		for (int i = 0; i < MAX_COLORS; i++) {
			final int index = i;
			out[i] = add(new ColorSetting("Colour " + (i + 1), "Outline colour " + (i + 1), DEFAULT_COLORS[i]),
					() -> palette.get() == OutlinePalette.CUSTOM && index < colorCount.getInt());
		}
		return out;
	}

	/** Whether the current colours are more than one colour (so animation options matter). */
	public boolean multiColor() {
		return palette.get() != OutlinePalette.CUSTOM || colorCount.getInt() > 1;
	}

	/** The colours of the selected set (custom colours or a preset). Rainbow returns an empty array. */
	public int[] colourSet() {
		OutlinePalette p = palette.get();
		if (p == OutlinePalette.RAINBOW) {
			return new int[0];
		}
		if (p != OutlinePalette.CUSTOM) {
			return p.colors();
		}
		int n = Math.max(1, Math.min(MAX_COLORS, colorCount.getInt()));
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = colors[i].get();
		}
		return out;
	}

	/**
	 * BlockOutlineMixin hook. Returns true when this module drew the outline (vanilla must be skipped).
	 */
	public static boolean render(VertexConsumerProvider.Immediate consumers, MatrixStack matrices, boolean translucentPass,
			WorldRenderState worldState) {
		BlockOutlines module = instance;
		if (module == null || failed || !module.isEnabled()) {
			return false;
		}
		try {
			OutlineRenderer.render(module, consumers, matrices, translucentPass, worldState);
			return true;
		} catch (RuntimeException | LinkageError e) {
			failed = true;
			Hazeline.LOGGER.error("Custom Block Outlines failed; using the vanilla outline for this session", e);
			return false;
		}
	}
}

package com.hazeline.module.modules.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.WorldRenderState;

import com.hazeline.Hazeline;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.BooleanSetting;
import com.hazeline.setting.ColorSetting;
import com.hazeline.setting.EntityListSetting;
import com.hazeline.setting.EnumSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Hit Boxes: draws the real bounding box of the chosen entity types (players by default) with the
 * same colour system as Custom Block Outlines.
 *
 * <h2>How (1.21.11, Yarn, verified against the bytecode)</h2>
 * The box is what vanilla's own F3+B renderer ({@code EntityHitboxDebugRenderer#drawHitbox}) draws:
 * {@code entity.getBoundingBox()} moved by {@code getLerpedPos(tickDelta) - getEntityPos()}, so it
 * follows the entity smoothly between ticks. The look line starts at {@code getCameraPosVec(tickDelta)}
 * (the interpolated eye position) and points along {@code getRotationVec(tickDelta)}, like vanilla's
 * blue F3+B line. {@code HitboxMixin} calls {@link #render} at HEAD of {@code WorldRenderer#collectGizmos},
 * which runs inside the main world pass right after entities and the block outline were drawn and
 * before translucent terrain. The lines use the depth-tested Custom Block Outlines layers
 * ({@link OutlinePipelines}, never the "on top" ones), so a box is only visible where you could see
 * it anyway - it never shows through walls.
 */
public class HitBoxes extends Module {

	public static final int MAX_COLORS = BlockOutlines.MAX_COLORS;
	/** Default custom colours: white, sky blue, purple, pink, orange, yellow. */
	private static final int[] DEFAULT_COLORS = {0xFFFFFFFF, 0xFF38BDF8, 0xFFA855F7, 0xFFEC4899, 0xFFF97316, 0xFFFACC15};

	// ------------------------------------------------------------------ which entities
	public final EntityListSetting entities = add(new EntityListSetting("Entities",
			"Which entities get a hitbox (search or pick a group)", "minecraft:player"));
	public final BooleanSetting showSelf = add(new BooleanSetting("Show yourself",
			"Also draw your own hitbox in third person (F5)", false));
	public final NumberSetting range = add(new NumberSetting("Range",
			"Only draw hitboxes of entities closer than this (blocks)", 64, 8, 256, 1));

	// ------------------------------------------------------------------ look
	public final EnumSetting<BlockOutlines.Mode> mode = add(new EnumSetting<>("Mode",
			"Outline = only the edges, Fill = see-through faces, or both", BlockOutlines.Mode.OUTLINE));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width",
			"Outline thickness, 1 = vanilla thickness", 1.5, 0.5, 6.0, 0.1), () -> mode.get().outline());
	public final NumberSetting outlineOpacity = add(new NumberSetting("Outline opacity",
			"How see-through the outline is (%)", 100, 5, 100, 1), () -> mode.get().outline());
	public final NumberSetting fillOpacity = add(new NumberSetting("Fill opacity",
			"How see-through the fill is (%)", 15, 2, 100, 1), () -> mode.get().fill());

	// ------------------------------------------------------------------ colours (same system as Custom Block Outlines)
	public final EnumSetting<OutlinePalette> palette = add(new EnumSetting<>("Colours",
			"Custom = your own colours below, Rainbow, or a ready-made colour set", OutlinePalette.CUSTOM));
	public final NumberSetting colorCount = add(new NumberSetting("Colour count",
			"How many of your colours to use (2 or more blend smoothly into each other)", 1, 1, MAX_COLORS, 1),
			() -> palette.get() == OutlinePalette.CUSTOM);
	public final ColorSetting[] colors = createColorSettings();
	public final NumberSetting rainbowSaturation = add(new NumberSetting("Rainbow saturation",
			"0 = white, 100 = full colour (%)", 85, 0, 100, 1), () -> palette.get() == OutlinePalette.RAINBOW);
	public final NumberSetting rainbowBrightness = add(new NumberSetting("Rainbow brightness",
			"(%)", 100, 10, 100, 1), () -> palette.get() == OutlinePalette.RAINBOW);
	public final EnumSetting<BlockOutlines.Animation> animation = add(new EnumSetting<>("Animation",
			"Static = colours stay in place, Fade = the whole box smoothly changes colour, Wave = the colours travel around the box",
			BlockOutlines.Animation.WAVE), this::multiColor);
	public final NumberSetting speed = add(new NumberSetting("Speed",
			"How fast the colours move (1 = one full colour cycle every 4 seconds)", 1.0, 0.1, 5.0, 0.05),
			() -> multiColor() && animation.get() != BlockOutlines.Animation.STATIC);
	public final NumberSetting spread = add(new NumberSetting("Colour spread",
			"How many times the colour set repeats around the box", 1.0, 0.25, 4.0, 0.05),
			() -> multiColor() && animation.get() != BlockOutlines.Animation.FADE);
	public final BooleanSetting reverse = add(new BooleanSetting("Reverse direction",
			"Moves the colours the other way round", false),
			() -> multiColor() && animation.get() != BlockOutlines.Animation.STATIC);

	// ------------------------------------------------------------------ extras
	public final BooleanSetting lookLine = add(new BooleanSetting("Look direction",
			"A line from the eyes showing where the entity is looking", true));
	public final NumberSetting lookLength = add(new NumberSetting("Look line length",
			"(blocks)", 2.0, 0.5, 6.0, 0.1), lookLine::get);
	public final BooleanSetting lookUsesBoxColour = add(new BooleanSetting("Look line in box colours",
			"Off = use the colour below", false), lookLine::get);
	public final ColorSetting lookColor = add(new ColorSetting("Look line colour",
			"Colour of the look direction line", 0xFF3B82F6), () -> lookLine.get() && !lookUsesBoxColour.get());

	public final BooleanSetting hurtFlash = add(new BooleanSetting("Hurt flash",
			"The box briefly fades to the hurt colour when the entity takes damage", true));
	public final ColorSetting hurtColor = add(new ColorSetting("Hurt colour",
			"Colour the box flashes to", 0xFFEF4444), hurtFlash::get);
	public final NumberSetting hurtStrength = add(new NumberSetting("Flash strength",
			"How strongly the box turns the hurt colour (%)", 85, 10, 100, 1), hurtFlash::get);

	public final BooleanSetting smoothFade = add(new BooleanSetting("Smooth fade in/out",
			"Boxes fade in when an entity appears and fade out when it leaves, dies or is turned off", true));
	public final NumberSetting fadeTime = add(new NumberSetting("Fade time",
			"(seconds)", 0.3, 0.05, 1.5, 0.05), smoothFade::get);

	private static HitBoxes instance;
	private static boolean failed;

	public HitBoxes() {
		super("Hit Boxes", "Shows the real hitbox of players and other entities, fully customisable", Category.RENDER);
		instance = this;
	}

	private ColorSetting[] createColorSettings() {
		ColorSetting[] out = new ColorSetting[MAX_COLORS];
		for (int i = 0; i < MAX_COLORS; i++) {
			final int index = i;
			out[i] = add(new ColorSetting("Colour " + (i + 1), "Hitbox colour " + (i + 1), DEFAULT_COLORS[i]),
					() -> palette.get() == OutlinePalette.CUSTOM && index < colorCount.getInt());
		}
		return out;
	}

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
	 * HitboxMixin hook (every frame, inside the world pass). Never throws. Keeps running for a moment
	 * after the module is switched off so the boxes can fade out.
	 */
	public static void render(VertexConsumerProvider.Immediate consumers, WorldRenderState worldState) {
		HitBoxes module = instance;
		if (module == null || failed || (!module.isEnabled() && !HitboxRenderer.hasTracks())) {
			return;
		}
		try {
			HitboxRenderer.render(module, consumers, worldState);
		} catch (RuntimeException | LinkageError e) {
			failed = true;
			HitboxRenderer.clear();
			Hazeline.LOGGER.error("Hit Boxes failed; turned off for this session", e);
		}
	}
}

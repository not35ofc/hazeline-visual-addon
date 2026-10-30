package com.hazeline.module.modules.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.NumberSetting;

/**
 * Swing Speed: changes only how fast YOUR hand swing animation plays (first and third person).
 *
 * <h2>How (1.21.11, Yarn, verified)</h2>
 * The swing animation progress is {@code handSwingTicks / getHandSwingDuration()}
 * ({@code LivingEntity#tickHandSwing}). {@code LivingEntityMixin} divides the duration returned by
 * {@code LivingEntity#getHandSwingDuration} by the multiplier, only for the client's own player
 * object. That duration is used only for the animation (and for when a new click restarts it);
 * the attack cooldown ({@code attackStrengthTicker}, attack speed attribute), block breaking and
 * placing do not read it, and the swing packet is sent on every swing regardless.
 * The duration is whole ticks (vanilla: 6 for most items), so the speed changes in steps.
 */
public class SwingSpeed extends Module {
	public final NumberSetting multiplier = add(new NumberSetting("Speed multiplier",
			"1 = normal, higher = faster swing, lower = slower (visual only)", 1.0, 0.1, 3.0, 0.05));

	private static SwingSpeed instance;

	public SwingSpeed() {
		super("Swing Speed", "Changes only the visual speed of your hand swing", Category.RENDER);
		instance = this;
	}

	/** LivingEntityMixin: the swing duration (ticks) to use for {@code entity}. */
	public static int duration(Object entity, int original) {
		SwingSpeed module = instance;
		if (module == null || !module.isEnabled() || entity != MinecraftClient.getInstance().player) {
			return original;
		}
		double m = MathHelper.clamp(module.multiplier.get(), 0.1, 3.0);
		return Math.max(1, (int) Math.round(original / m));
	}
}

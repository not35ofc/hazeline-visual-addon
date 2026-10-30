package com.hazeline.module.modules.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Hand;

import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.setting.ActionSetting;
import com.hazeline.setting.NumberSetting;

/**
 * Hand View: moves, rotates and scales the first-person hand / held item, separately for the main
 * hand and the offhand. Purely visual.
 *
 * <h2>How (1.21.11, Yarn, verified)</h2>
 * {@code HeldItemRenderer#renderFirstPersonItem} draws one hand: it pushes the matrix stack once,
 * applies vanilla's arm/animation transforms and finally calls {@code renderItem} (held item) or
 * {@code renderArmHoldingItem} (empty main hand). {@code ItemInHandRendererMixin}:
 * <ul>
 *   <li>right after that {@code push}: translate by the hand's X/Y/Z offset (screen space:
 *       X right, Y up, Z toward you), so the whole hand moves and every vanilla animation (swing,
 *       eating, bow, equip) still plays on top;</li>
 *   <li>right before {@code renderItem} / {@code renderPlayerArm}: rotate X/Y/Z and scale around the
 *       item itself, so scaling does not slide it toward the screen centre.</li>
 * </ul>
 *
 * <h2>Live preview</h2>
 * While this module's settings are expanded in the click GUI, the menu skips its blur and dim
 * ({@link #isPreviewing()}), so your real hand is clearly visible behind the panels and every
 * change shows immediately.
 */
public class HandView extends Module {
	/** One hand's settings. */
	public final class HandSettings {
		final NumberSetting x;
		final NumberSetting y;
		final NumberSetting z;
		final NumberSetting rotX;
		final NumberSetting rotY;
		final NumberSetting rotZ;
		final NumberSetting scale;

		HandSettings(String prefix, String who) {
			x = add(new NumberSetting(prefix + " X", "Move " + who + " right (+) / left (-)", 0, -1, 1, 0.01));
			y = add(new NumberSetting(prefix + " Y", "Move " + who + " up (+) / down (-)", 0, -1, 1, 0.01));
			z = add(new NumberSetting(prefix + " Z", "Move " + who + " toward you (+) / away (-)", 0, -1, 1, 0.01));
			rotX = add(new NumberSetting(prefix + " rotation X", "Tilt " + who + " forward/back in degrees", 0, -180, 180, 1));
			rotY = add(new NumberSetting(prefix + " rotation Y", "Turn " + who + " left/right in degrees", 0, -180, 180, 1));
			rotZ = add(new NumberSetting(prefix + " rotation Z", "Roll " + who + " in degrees", 0, -180, 180, 1));
			scale = add(new NumberSetting(prefix + " scale", "Size of " + who, 1.0, 0.1, 2.0, 0.05));
			add(new ActionSetting("Reset " + who, "Put " + who + " back to the vanilla position", this::reset));
		}

		void reset() {
			x.reset();
			y.reset();
			z.reset();
			rotX.reset();
			rotY.reset();
			rotZ.reset();
			scale.reset();
		}
	}

	/** How long after the last frame with expanded settings the preview stays on. */
	private static final long PREVIEW_HOLD_NANOS = 200_000_000L;

	public final HandSettings mainHand = new HandSettings("Main", "the main hand");
	public final HandSettings offHand = new HandSettings("Offhand", "the offhand");

	private static HandView instance;
	private static Hand currentHand = Hand.MAIN_HAND;
	private static volatile long lastShownNanos;

	public HandView() {
		super("Hand View", "Move, rotate and scale your first-person hands and items", Category.RENDER);
		instance = this;
	}

	@Override
	public void onSettingsVisible() {
		lastShownNanos = System.nanoTime();
	}

	/** True while this module is on and its settings are open in the click GUI. */
	public static boolean isPreviewing() {
		HandView module = instance;
		return module != null && module.isEnabled() && System.nanoTime() - lastShownNanos < PREVIEW_HOLD_NANOS;
	}

	private static HandSettings settings(Hand hand) {
		return hand == Hand.OFF_HAND ? instance.offHand : instance.mainHand;
	}

	private static boolean active() {
		return instance != null && instance.isEnabled();
	}

	/** Mixin: right after renderArmWithItem's pushPose. */
	public static void applyOffset(Hand hand, MatrixStack poseStack) {
		currentHand = hand;
		if (!active()) {
			return;
		}
		HandSettings s = settings(hand);
		float x = (float) MathHelper.clamp(s.x.get(), -1, 1);
		float y = (float) MathHelper.clamp(s.y.get(), -1, 1);
		float z = (float) MathHelper.clamp(s.z.get(), -1, 1);
		if (x != 0 || y != 0 || z != 0) {
			poseStack.translate(x, y, z);
		}
	}

	/** Mixin: right before renderItem / renderPlayerArm inside renderArmWithItem. */
	public static void applyItemTransform(MatrixStack poseStack) {
		if (!active()) {
			return;
		}
		HandSettings s = settings(currentHand);
		float rx = (float) MathHelper.clamp(s.rotX.get(), -180, 180);
		float ry = (float) MathHelper.clamp(s.rotY.get(), -180, 180);
		float rz = (float) MathHelper.clamp(s.rotZ.get(), -180, 180);
		float scale = (float) MathHelper.clamp(s.scale.get(), 0.1, 2.0);
		if (rx != 0) {
			poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
		}
		if (ry != 0) {
			poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
		}
		if (rz != 0) {
			poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
		}
		if (scale != 1.0f) {
			poseStack.scale(scale, scale, scale);
		}
	}
}

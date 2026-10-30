package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import com.hazeline.gui.GuiOverlay;

/**
 * Click GUI overlay ({@link GuiOverlay}). {@code Screen#renderWithTooltip(DrawContext,int,int,float)}
 * is final and only declared in {@code Screen}; in 1.21.11 it does createNewRootLayer,
 * renderBackground, createNewRootLayer, render, drawDeferredElements (verified against the
 * 1.21.11 bytecode). While the overlay is open on this screen:
 * <ul>
 *   <li>HEAD: the screen gets an off-screen mouse position, so nothing under the menu is hovered
 *       and no item tooltips appear;</li>
 *   <li>TAIL: the overlay is drawn after everything of the screen.</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class ScreenOverlayMixin {
	private static final int HAZELINE$NOWHERE = -100000;

	@ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int hazeline$hideMouseX(int mouseX) {
		return GuiOverlay.isHost((Screen) (Object) this) ? HAZELINE$NOWHERE : mouseX;
	}

	@ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int hazeline$hideMouseY(int mouseY) {
		return GuiOverlay.isHost((Screen) (Object) this) ? HAZELINE$NOWHERE : mouseY;
	}

	@Inject(method = "renderWithTooltip", at = @At("TAIL"))
	private void hazeline$drawOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		GuiOverlay.render((Screen) (Object) this, context, delta);
	}
}

package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;

import com.hazeline.module.modules.menus.CustomMenus;

/**
 * Custom Menus: the loading screen. In 1.21.11 (Yarn) {@code SplashOverlay#render} does, in this order:
 * <ol>
 *   <li>the background: {@code DrawContext#fill(IIIII)} in two branches (alpha = fade) or, in the
 *       plain start-up branch, {@code CommandEncoder#clearColorTexture} with the brand colour;</li>
 *   <li>the Mojang logo: two {@code DrawContext#drawTexture} calls (left half u = -0.0625, right half u = 0.0625);</li>
 *   <li>{@code renderProgressBar(DrawContext, int, int, int, int, float)} while the fade-out has not started.</li>
 * </ol>
 * Each of these is wrapped: with the custom look active the sky replaces 1, the wordmark replaces 2
 * and the glowing bar replaces 3; otherwise (or when the custom look cannot be drawn) the original runs.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow
	private float progress;

	/** The draw context of the frame being rendered (set at the start of {@code render}). */
	@Unique
	private DrawContext hazeline$context;

	@Inject(method = "render", at = @At("HEAD"))
	private void hazeline$beginFrame(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		this.hazeline$context = context;
		CustomMenus.beginLoadingFrame();
	}

	@WrapOperation(method = "render",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
	private void hazeline$background(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
		if (!CustomMenus.drawLoadingBackground(context, color >>> 24)) {
			original.call(context, x1, y1, x2, y2, color);
		}
	}

	@WrapOperation(method = "render",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V"))
	private void hazeline$clear(CommandEncoder encoder, GpuTexture texture, int color, Operation<Void> original) {
		// Start-up branch: vanilla clears the whole frame to the brand colour. Draw the sky instead (opaque).
		if (this.hazeline$context == null || !CustomMenus.drawLoadingBackground(this.hazeline$context, 255)) {
			original.call(encoder, texture, color);
		}
	}

	@WrapOperation(method = "render",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V"))
	private void hazeline$logo(DrawContext context, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
			int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color,
			Operation<Void> original) {
		if (CustomMenus.loadingCustom()) {
			if (u > 0f) { // second (right) half: draw the wordmark once, with the logo's fade alpha
				CustomMenus.drawLoadingWordmark(context, color >>> 24);
			}
			return;
		}
		original.call(context, pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
	}

	@WrapOperation(method = "render",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashOverlay;renderProgressBar(Lnet/minecraft/client/gui/DrawContext;IIIIF)V"))
	private void hazeline$bar(SplashOverlay self, DrawContext context, int x1, int y1, int x2, int y2, float opacity,
			Operation<Void> original) {
		if (!CustomMenus.drawLoadingBar(context, this.progress, opacity)) {
			original.call(self, context, x1, y1, x2, y2, opacity);
		}
	}
}

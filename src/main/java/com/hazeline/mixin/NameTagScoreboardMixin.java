package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import com.hazeline.module.modules.render.NameTag;

/**
 * Name Tag: your name in the scoreboard sidebar (verified in the 1.21.11 bytecode).
 * <ul>
 *   <li>Each line is built every frame in the lambda {@code method_55439} (ScoreboardEntry ->
 *       SidebarEntry) as {@code Team.decorateName(team, entry.name())} - team prefix + name + suffix,
 *       which is also where servers put text like "Player: Name". The line is measured there too.</li>
 *   <li>The title is {@code ScoreboardObjective#getDisplayName()} in
 *       {@code renderScoreboardSidebar(DrawContext, ScoreboardObjective)}.</li>
 * </ul>
 */
@Mixin(InGameHud.class)
public abstract class NameTagScoreboardMixin {
	@ModifyExpressionValue(method = "method_55439", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"))
	private MutableText hazeline$nameTagLine(MutableText original) {
		return NameTag.scoreboardLine(original);
	}

	@ModifyExpressionValue(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
			require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"))
	private Text hazeline$nameTagTitle(Text original) {
		return NameTag.scoreboardText(original);
	}
}

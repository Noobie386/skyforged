package com.skyforged;

import com.skyforged.network.CooldownPayload;
import com.skyforged.network.ShoutPayload;
import com.skyforged.shouts.Shouts;
import com.skyforged.skills.ModAttachments;
import com.skyforged.skills.SkillManager;
import com.skyforged.skills.SkillType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Skyforged implements ModInitializer {
	public static final String MOD_ID = "skyforged";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Loads the class so the skills attachment is registered.
		ModAttachments.init();

		// Networking: the client asks for a shout, the server tells the client about cooldowns.
		PayloadTypeRegistry.serverboundPlay().register(ShoutPayload.TYPE, ShoutPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CooldownPayload.TYPE, CooldownPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShoutPayload.TYPE,
			(payload, context) -> Shouts.cast(context.player(), payload.shout()));

		// Combat XP: any kill you land, scaled by how tough the victim was.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (damageSource.getEntity() instanceof ServerPlayer player && entity != player) {
				int xp = Math.max(1, Math.round(entity.getMaxHealth() / 2.0f));
				SkillManager.addXp(player, SkillType.COMBAT, xp);
			}
		});

		// Mining XP: any block you break, scaled by how hard it was.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				float hardness = state.getDestroySpeed(level, pos);
				if (hardness > 0.0f) {
					SkillManager.addXp(serverPlayer, SkillType.MINING, Math.max(1, Math.round(hardness)));
				}
			}
		});

		// Attribute bonuses are re-applied on join and respawn.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SkillManager.applyBonuses(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SkillManager.applyBonuses(newPlayer));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Shouts.forget(handler.getPlayer().getUUID()));

		LOGGER.info("Skyforged loaded.");
	}
}

package com.skyforged.shouts;

import com.skyforged.network.CooldownPayload;
import com.skyforged.skills.SkillManager;
import com.skyforged.skills.SkillType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The three shouts. All logic runs on the server; the client only sends the key press
 * and shows cooldowns.
 *
 * The Voice skill makes shouts stronger (range, power, damage) and cuts cooldowns by 1% per level (max 50%).
 */
public final class Shouts {
	/** Order matters: the index is what the client sends over the network. */
	public enum Shout {
		GALE("Gale Push", 200),
		EMBER("Ember Breath", 300),
		BLINK("Blink Step", 160);

		public final String displayName;
		public final int baseCooldownTicks;

		Shout(String displayName, int baseCooldownTicks) {
			this.displayName = displayName;
			this.baseCooldownTicks = baseCooldownTicks;
		}
	}

	private static final int VOICE_XP_PER_SHOUT = 8;
	private static final Map<UUID, long[]> READY_AT = new HashMap<>();

	private Shouts() {
	}

	public static void forget(UUID playerId) {
		READY_AT.remove(playerId);
	}

	public static void cast(ServerPlayer player, int index) {
		Shout[] all = Shout.values();
		if (index < 0 || index >= all.length) {
			return;
		}
		Shout shout = all[index];
		ServerLevel level = (ServerLevel) player.level();

		long now = level.getGameTime();
		long[] readyAt = READY_AT.computeIfAbsent(player.getUUID(), id -> new long[all.length]);
		if (now < readyAt[index]) {
			return;
		}

		int voice = SkillManager.get(player).level(SkillType.VOICE);
		boolean success = switch (shout) {
			case GALE -> galePush(player, level, voice);
			case EMBER -> emberBreath(player, level, voice);
			case BLINK -> blinkStep(player, level, voice);
		};
		if (!success) {
			return;
		}

		double reduction = Math.min(0.5, (voice - 1) * 0.01);
		int cooldown = (int) (shout.baseCooldownTicks * (1.0 - reduction));
		readyAt[index] = now + cooldown;
		ServerPlayNetworking.send(player, new CooldownPayload(index, cooldown));
		SkillManager.addXp(player, SkillType.VOICE, VOICE_XP_PER_SHOUT);
	}

	/** Knocks every mob in a cone in front of you away. Players are not pushed. */
	private static boolean galePush(ServerPlayer player, ServerLevel level, int voice) {
		double range = 8.0 + voice * 0.1;
		double power = 1.6 + voice * 0.03;
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();

		for (LivingEntity target : inCone(player, level, range, 0.5)) {
			if (target instanceof ServerPlayer) {
				continue;
			}
			target.push(look.x * power, 0.35 + voice * 0.005, look.z * power);
		}

		level.sendParticles(ParticleTypes.CLOUD,
			eye.x + look.x * 2.5, eye.y + look.y * 2.5 - 0.3, eye.z + look.z * 2.5,
			40, 1.0, 0.5, 1.0, 0.15);
		playSound(level, player, SoundEvents.ENDER_DRAGON_FLAP);
		return true;
	}

	/** Damages and ignites every living thing in a short cone. */
	private static boolean emberBreath(ServerPlayer player, ServerLevel level, int voice) {
		double range = 6.0 + voice * 0.06;
		float damage = 3.0f + voice * 0.1f;
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();

		for (LivingEntity target : inCone(player, level, range, 0.6)) {
			target.hurtServer(level, level.damageSources().playerAttack(player), damage);
			target.setRemainingFireTicks(100);
		}

		int steps = 40;
		for (int i = 0; i < steps; i++) {
			double d = 1.0 + i * (range - 1.0) / steps;
			double spread = d * 0.12;
			level.sendParticles(ParticleTypes.FLAME,
				eye.x + look.x * d, eye.y + look.y * d - 0.2, eye.z + look.z * d,
				2, spread, spread, spread, 0.01);
		}
		playSound(level, player, SoundEvents.BLAZE_SHOOT);
		return true;
	}

	/** Teleports you forward along your look direction, stopping before any wall. */
	private static boolean blinkStep(ServerPlayer player, ServerLevel level, int voice) {
		double maxDistance = 8.0 + voice * 0.1;
		Vec3 look = player.getLookAngle();
		AABB box = player.getBoundingBox();

		Vec3 best = null;
		for (double d = 0.5; d <= maxDistance; d += 0.5) {
			Vec3 offset = look.scale(d);
			if (!level.noCollision(player, box.move(offset))) {
				break;
			}
			best = offset;
		}
		if (best == null) {
			return false; // Nowhere to go, so the cooldown is not spent.
		}

		Vec3 from = player.position();
		level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.3);
		if (!player.randomTeleport(from.x + best.x, from.y + best.y, from.z + best.z, true)) {
			return false;
		}
		level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
		playSound(level, player, SoundEvents.ENDERMAN_TELEPORT);
		return true;
	}

	private static List<LivingEntity> inCone(ServerPlayer player, ServerLevel level, double range, double minDot) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB searchBox = player.getBoundingBox().inflate(range);

		return level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> {
			if (entity == player || !entity.isAlive()) {
				return false;
			}
			Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(eye);
			double distance = toEntity.length();
			if (distance > range || distance < 0.001) {
				return false;
			}
			return toEntity.normalize().dot(look) >= minDot;
		});
	}

	private static void playSound(ServerLevel level, ServerPlayer player, SoundEvent sound) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
	}
}

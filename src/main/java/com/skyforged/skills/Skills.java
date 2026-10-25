package com.skyforged.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable per-player skill experience. Levels are derived from XP, so only XP is stored.
 * Attachments must hold immutable values, so every change creates a new record.
 */
public record Skills(int combat, int mining, int voice) {
	public static final Skills EMPTY = new Skills(0, 0, 0);
	public static final int MAX_LEVEL = 50;

	/** Saved with the player. */
	public static final Codec<Skills> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("combat", 0).forGetter(Skills::combat),
		Codec.INT.optionalFieldOf("mining", 0).forGetter(Skills::mining),
		Codec.INT.optionalFieldOf("voice", 0).forGetter(Skills::voice)
	).apply(instance, Skills::new));

	/** Sent to the owning client so the HUD can show progress. */
	public static final StreamCodec<ByteBuf, Skills> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Skills::combat,
		ByteBufCodecs.VAR_INT, Skills::mining,
		ByteBufCodecs.VAR_INT, Skills::voice,
		Skills::new
	);

	public int xp(SkillType type) {
		return switch (type) {
			case COMBAT -> combat;
			case MINING -> mining;
			case VOICE -> voice;
		};
	}

	public Skills withXp(SkillType type, int xp) {
		return switch (type) {
			case COMBAT -> new Skills(xp, mining, voice);
			case MINING -> new Skills(combat, xp, voice);
			case VOICE -> new Skills(combat, mining, xp);
		};
	}

	public int level(SkillType type) {
		return levelForXp(xp(type));
	}

	/** Fraction (0..1) of the way from the current level to the next one. */
	public float progress(SkillType type) {
		int xp = xp(type);
		int level = levelForXp(xp);
		if (level >= MAX_LEVEL) {
			return 1.0f;
		}
		int start = xpForLevel(level);
		int end = xpForLevel(level + 1);
		return (xp - start) / (float) (end - start);
	}

	// Level 2 at 20 XP, level 10 at 1,620 XP, level 50 at 48,020 XP.
	public static int levelForXp(int xp) {
		return Math.min(MAX_LEVEL, 1 + (int) Math.sqrt(Math.max(0, xp) / 20.0));
	}

	public static int xpForLevel(int level) {
		int l = Math.max(1, level) - 1;
		return 20 * l * l;
	}
}

package com.skyforged.network;

import com.skyforged.Skyforged;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: "I pressed the key for shout number {@code shout}". */
public record ShoutPayload(int shout) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "shout");
	public static final CustomPacketPayload.Type<ShoutPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, ShoutPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ShoutPayload::shout,
		ShoutPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

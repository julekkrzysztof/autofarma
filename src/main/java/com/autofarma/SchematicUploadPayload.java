package com.autofarma;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SchematicUploadPayload(String fileName, String json) implements CustomPayload {
    public static final int MAX_JSON_LENGTH = 500_000;
    public static final Id<SchematicUploadPayload> ID =
            new Id<>(Identifier.of(AutoFarmMod.MOD_ID, "schematic_upload"));
    public static final PacketCodec<RegistryByteBuf, SchematicUploadPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeString(payload.fileName(), 64);
                buf.writeString(payload.json(), MAX_JSON_LENGTH);
            },
            buf -> new SchematicUploadPayload(
                    buf.readString(64),
                    buf.readString(MAX_JSON_LENGTH)
            )
    );

    public SchematicUploadPayload {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("Schematic upload exceeds the size limit");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

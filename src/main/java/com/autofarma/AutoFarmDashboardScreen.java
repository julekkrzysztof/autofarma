package com.autofarma;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AutoFarmDashboardScreen extends HandledScreen<AutoFarmScreenHandler> {
    public AutoFarmDashboardScreen() {
        super(
                new AutoFarmScreenHandler(0, MinecraftClient.getInstance().player.getInventory()),
                MinecraftClient.getInstance().player.getInventory(),
                Text.literal("AutoFarm")
        );
    }

    public AutoFarmDashboardScreen(AutoFarmScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
    }

    @Override
    public void onFilesDropped(List<Path> files) {
        if (files.isEmpty()) return;

        for (Path file : files) {
            if (!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                showMessage("§cMożna dodawać tylko pliki schematu .json.");
                continue;
            }

            try {
                if (Files.size(file) > SchematicUploadPayload.MAX_JSON_LENGTH) {
                    showMessage("§cPlik schematu przekracza limit 500 KB.");
                    continue;
                }
                String json = Files.readString(file, StandardCharsets.UTF_8);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new SchematicUploadPayload(file.getFileName().toString(), json)
                );
                showMessage("§aWysyłanie schematu: " + file.getFileName());
                return;
            } catch (IOException exception) {
                showMessage("§cNie udało się odczytać pliku schematu.");
            }
        }
    }

    private static void showMessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
        }
    }
}

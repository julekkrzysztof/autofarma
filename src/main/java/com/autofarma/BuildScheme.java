package com.autofarma;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record BuildScheme(String name, int width, int height, int depth, List<CactusScheme.Placement> placements) {
    private static final int MAX_PLACEMENTS = 32768;
    private static final int MAX_DIMENSION = 256;

    public BuildScheme {
        placements = List.copyOf(placements);
    }

    public static BuildScheme cactus(int floors) {
        return new BuildScheme(
                "Kaktusiarka",
                CactusScheme.WIDTH,
                CactusScheme.height(floors),
                CactusScheme.DEPTH,
                CactusScheme.placements(floors)
        );
    }

    public static BuildScheme load(Path file) throws IOException {
        if (Files.size(file) > SchematicUploadPayload.MAX_JSON_LENGTH) {
            throw new IOException("Plik schematu przekracza limit 500 KB.");
        }
        return parse(Files.readString(file));
    }

    public static BuildScheme parse(String contents) throws IOException {
        if (contents.length() > SchematicUploadPayload.MAX_JSON_LENGTH) {
            throw new IOException("Plik schematu przekracza limit przesyłania.");
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(contents).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Niepoprawny plik JSON schematu.", exception);
        }

        try {
            String name = requiredString(root, "name");
            if (name.isBlank() || name.length() > 64) {
                throw new IOException("Nazwa schematu musi mieć od 1 do 64 znaków.");
            }
            JsonArray blocks = root.getAsJsonArray("blocks");
            if (blocks == null || blocks.isEmpty() || blocks.size() > MAX_PLACEMENTS) {
                throw new IOException("Schemat musi zawierać od 1 do " + MAX_PLACEMENTS + " bloków.");
            }

            List<CactusScheme.Placement> placements = new ArrayList<>(blocks.size());
            Set<Long> occupied = new HashSet<>();
            int maxX = -1;
            int maxY = -1;
            int maxZ = -1;
            for (JsonElement element : blocks) {
                JsonObject block = element.getAsJsonObject();
                int x = requiredInt(block, "x");
                int y = requiredInt(block, "y");
                int z = requiredInt(block, "z");
                long key = ((long) x << 40) | ((long) y << 20) | z;
                if (!occupied.add(key)) throw new IOException("Schemat zawiera powtórzone pozycje bloków.");

                Identifier id = Identifier.tryParse(requiredString(block, "block"));
                if (id == null) throw new IOException("Niepoprawny identyfikator bloku.");
                if (!Registries.BLOCK.containsId(id)) throw new IOException("Nieznany blok w schemacie: " + id);
                Block blockType = Registries.BLOCK.get(id);
                if (blockType.asItem() == net.minecraft.item.Items.AIR) {
                    throw new IOException("Blok nie może zostać postawiony z przedmiotu: " + id);
                }
                placements.add(new CactusScheme.Placement(x, y, z, blockType));
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }

            int width = optionalInt(root, "width", maxX + 1);
            int height = optionalInt(root, "height", maxY + 1);
            int depth = optionalInt(root, "depth", maxZ + 1);
            if (width < 1 || width > 16 || depth < 1 || depth > 16
                    || height < 1 || height > MAX_DIMENSION) {
                throw new IOException("Wymiary schematu muszą mieścić się w zakresie 1–16 × 1–256 × 1–16.");
            }
            for (CactusScheme.Placement placement : placements) {
                if (placement.x() < 0 || placement.x() >= width
                        || placement.y() < 0 || placement.y() >= height
                        || placement.z() < 0 || placement.z() >= depth) {
                    throw new IOException("Blok schematu znajduje się poza zadeklarowanymi wymiarami.");
                }
            }

            return new BuildScheme(name, width, height, depth, placements);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Niepoprawna struktura JSON schematu.", exception);
        }
    }

    public java.util.Map<Item, Integer> requiredItems() {
        java.util.Map<Item, Integer> items = new java.util.LinkedHashMap<>();
        for (CactusScheme.Placement placement : placements) {
            items.merge(placement.block().asItem(), 1, Integer::sum);
        }
        return items;
    }

    private static String requiredString(JsonObject json, String key) throws IOException {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IOException("Brak tekstowego pola '" + key + "'.");
        }
        return element.getAsString();
    }

    private static int requiredInt(JsonObject json, String key) throws IOException {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Brak liczbowego pola '" + key + "'.");
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IOException("Pole '" + key + "' musi być liczbą całkowitą.", exception);
        }
    }

    private static int optionalInt(JsonObject json, String key, int inferred) throws IOException {
        JsonElement element = json.get(key);
        if (element == null) {
            return inferred;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Pole '" + key + "' musi być liczbą całkowitą.");
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IOException("Pole '" + key + "' musi być liczbą całkowitą.", exception);
        }
    }
}

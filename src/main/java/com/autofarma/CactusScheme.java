package com.autofarma;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CactusScheme {
    public static final int WIDTH = 16;
    public static final int DEPTH = 16;
    public static final int DEFAULT_FLOORS = 7;
    public static final int MAX_FLOORS = 30;

    private CactusScheme() {}

    public static int height(int floors) {
        return floors * 5 + 4;
    }

    public static List<Placement> placements(int floors) {
        List<Placement> placements = new ArrayList<>();
        addBaseAndCollection(placements);
        addLevels(placements, floors);
        addTopAndSides(placements, floors);
        return List.copyOf(placements);
    }

    public static Map<Item, Integer> requiredItems(int floors) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (Placement placement : placements(floors)) {
            Item item = itemFor(placement.block());
            if (item != Items.AIR) {
                result.merge(item, 1, Integer::sum);
            }
        }
        return result;
    }

    private static Item itemFor(Block block) {
        if (block == Blocks.CACTUS) return Items.CACTUS;
        if (block == Blocks.SAND) return Items.SAND;
        if (block == Blocks.GLASS) return Items.GLASS;
        if (block == Blocks.HOPPER) return Items.HOPPER;
        if (block == Blocks.CHEST) return Items.CHEST;
        if (block == Blocks.RAIL) return Items.RAIL;
        if (block == Blocks.OAK_PLANKS) return Items.OAK_PLANKS;
        if (block == Blocks.OAK_FENCE) return Items.OAK_FENCE;
        if (block == Blocks.SMOOTH_SANDSTONE) return Items.SMOOTH_SANDSTONE;
        if (block == Blocks.WATER) return Items.WATER_BUCKET;
        if (block == Blocks.REDSTONE_BLOCK) return Items.REDSTONE_BLOCK;
        if (block == Blocks.OBSERVER) return Items.OBSERVER;
        if (block == Blocks.PISTON) return Items.PISTON;
        if (block == Blocks.STICKY_PISTON) return Items.STICKY_PISTON;
        return Items.AIR;
    }

    private static void add(int x, int y, int z, Block block, List<Placement> out) {
        out.add(new Placement(x, y, z, block));
    }

    private static void addBaseAndCollection(List<Placement> out) {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                add(x, 0, z, Blocks.SMOOTH_SANDSTONE, out);
            }
        }

        for (int x = 1; x < 8; x++) add(x, 1, 8, Blocks.HOPPER, out);
        for (int x = 9; x < WIDTH - 1; x++) add(x, 1, 8, Blocks.HOPPER, out);
        add(8, 1, 8, Blocks.CHEST, out);
        add(0, 1, 8, Blocks.RAIL, out);
        add(WIDTH - 1, 1, 8, Blocks.RAIL, out);

        for (int x = 0; x < WIDTH; x++) {
            add(x, 1, 0, Blocks.GLASS, out);
            add(x, 1, DEPTH - 1, Blocks.GLASS, out);
        }
        for (int z = 0; z < DEPTH; z++) {
            if (z == 8) continue;
            add(0, 1, z, Blocks.GLASS, out);
            add(WIDTH - 1, 1, z, Blocks.GLASS, out);
        }
    }

    private static void addLevels(List<Placement> out, int floors) {
        for (int level = 0; level < floors; level++) {
            int baseY = 2 + level * 5;

            for (int x = 2; x <= 12; x += 2) {
                for (int z = 2; z <= 12; z += 5) {
                    add(x, baseY, z, Blocks.SAND, out);
                    add(x, baseY + 1, z, Blocks.CACTUS, out);
                    add(x + 1, baseY + 2, z, Blocks.GLASS, out);
                }
            }

            for (int x = 0; x < WIDTH; x++) {
                add(x, baseY, 0, Blocks.OAK_PLANKS, out);
                add(x, baseY, DEPTH - 1, Blocks.OAK_PLANKS, out);
                add(x, baseY + 1, 0, Blocks.GLASS, out);
                add(x, baseY + 1, DEPTH - 1, Blocks.GLASS, out);
                add(x, baseY + 2, 0, Blocks.GLASS, out);
                add(x, baseY + 2, DEPTH - 1, Blocks.GLASS, out);
            }

            for (int z = 1; z < DEPTH - 1; z++) {
                add(0, baseY, z, Blocks.OAK_PLANKS, out);
                add(WIDTH - 1, baseY, z, Blocks.OAK_PLANKS, out);
                add(0, baseY + 1, z, Blocks.GLASS, out);
                add(WIDTH - 1, baseY + 1, z, Blocks.GLASS, out);
                add(0, baseY + 2, z, Blocks.GLASS, out);
                add(WIDTH - 1, baseY + 2, z, Blocks.GLASS, out);
            }

            for (int x = 1; x < WIDTH - 1; x++) {
                add(x, baseY + 3, 8, Blocks.HOPPER, out);
            }
        }
    }

    private static void addTopAndSides(List<Placement> out, int floors) {
        int roofY = floors == 0 ? 1 : 2 + (floors - 1) * 5 + 5;
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                add(x, roofY, z, Blocks.GLASS, out);
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            add(x, roofY + 1, 0, Blocks.OAK_FENCE, out);
            add(x, roofY + 1, DEPTH - 1, Blocks.OAK_FENCE, out);
        }
        for (int z = 1; z < DEPTH - 1; z++) {
            add(0, roofY + 1, z, Blocks.OAK_FENCE, out);
            add(WIDTH - 1, roofY + 1, z, Blocks.OAK_FENCE, out);
        }
    }

    public record Placement(int x, int y, int z, Block block) {}
}

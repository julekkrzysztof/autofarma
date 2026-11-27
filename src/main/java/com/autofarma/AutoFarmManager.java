

package com.autofarma;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.WorldSavePath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutoFarmManager {
    private static final int DEFAULT_FLOORS = 1;

    private final MinecraftServer server;
    private final Map<UUID, PlayerSelection> selections = new HashMap<>();
    private final Map<UUID, BuildRuntime> runtimes = new HashMap<>();
    private final Map<UUID, Integer> floorCounts = new HashMap<>();
    private final Map<UUID, Integer> placementDelays = new HashMap<>();
    private final Map<UUID, BuildScheme> selectedSchemes = new HashMap<>();

    public AutoFarmManager(MinecraftServer server) {
        this.server = server;
    }

    private int validateFloors(int floors) {
        if (floors < CactusScheme.MIN_FLOORS || floors > CactusScheme.MAX_FLOORS) {
            throw new IllegalArgumentException("Liczba pięter musi wynosić od 1 do 30.");
        }
        return floors;
    }

    public void setCorner(ServerPlayerEntity player, int corner, BlockPos pos) {
        if (corner != 1 && corner != 2) {
            player.sendMessage(Text.literal("§cAutoFarm: narożnik musi mieć numer 1 lub 2."), false);
            return;
        }

        BlockPos chunkCorner = new BlockPos(
                pos.getX() >> 4 << 4,
                pos.getY(),
                pos.getZ() >> 4 << 4
        );

        PlayerSelection selection = selections.computeIfAbsent(
                player.getUuid(),
                ignored -> new PlayerSelection(null, null, player.getServerWorld())
        );

        if (corner == 1) {
            selections.put(player.getUuid(),
                    new PlayerSelection(chunkCorner, selection.second(), player.getServerWorld()));
        } else {
            selections.put(player.getUuid(),
                    new PlayerSelection(selection.first(), chunkCorner, player.getServerWorld()));
        }

        player.sendMessage(Text.literal(
                "§aAutoFarm: narożnik " + corner + " chunku ustawiony na "
                        + chunkCorner.toShortString()
        ), false);

        PlayerSelection updated = selections.get(player.getUuid());

        if (updated.isComplete() && !updated.isSameChunk()) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: oba narożniki muszą wskazywać ten sam chunk 16×16."
            ), false);
        }
    }

    public PlayerSelection selection(UUID playerId) {
        return selections.get(playerId);
    }

    public int floorCount(UUID playerId) {
        return floorCounts.getOrDefault(playerId, DEFAULT_FLOORS);
    }

    public int placementDelay(UUID playerId) {
        return placementDelays.getOrDefault(playerId, 2);
    }

    public void setPlacementDelay(ServerPlayerEntity player, int ticks) {
        if (ticks < 1 || ticks > 20) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: opóźnienie musi wynosić od 1 do 20 ticków."
            ), false);
            return;
        }

        placementDelays.put(player.getUuid(), ticks);

        player.sendMessage(Text.literal(
                "§aAutoFarm: szybkość ustawiona na 1 blok co "
                        + ticks + (ticks == 1 ? " tick." : " ticki.")
        ), false);
    }

    public void setFloorCount(ServerPlayerEntity player, int floors) {
        if (floors < CactusScheme.MIN_FLOORS || floors > CactusScheme.MAX_FLOORS) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: możesz wybrać od 1 do 30 pięter."
            ), false);
            return;
        }

        floorCounts.put(player.getUuid(), floors);

        player.sendMessage(Text.literal(
                "§aAutoFarm: ustawiono " + floors
                        + (floors == 1 ? " piętro." : " pięter.")
                        + " Użyj /autofarm start, aby rozpocząć budowę."
        ), false);
    }

    public void loadScheme(ServerPlayerEntity player, String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,48}")) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: nieprawidłowa nazwa schematu."
            ), false);
            return;
        }

        Path directory = server.getSavePath(WorldSavePath.ROOT).resolve("schematics");
        Path file = directory.resolve(name + ".json");

        try {
            Files.createDirectories(directory);
            BuildScheme scheme = BuildScheme.load(file);
            selectedSchemes.put(player.getUuid(), scheme);

            player.sendMessage(Text.literal(
                    "§aAutoFarm: załadowano schemat " + scheme.name()
                            + " (" + scheme.width() + "×" + scheme.height()
                            + "×" + scheme.depth() + ", "
                            + scheme.placements().size() + " bloków)."
            ), false);
        } catch (IOException exception) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: nie udało się wczytać "
                            + file.getFileName() + ": " + exception.getMessage()
            ), false);
        }
    }

    public void importScheme(ServerPlayerEntity player, String fileName, String json) {
        String safeName = fileName.replaceFirst("(?i)\\.json$", "");

        if (!safeName.matches("[A-Za-z0-9_-]{1,48}")) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: nieprawidłowa nazwa pliku."
            ), false);
            return;
        }

        try {
            BuildScheme scheme = BuildScheme.parse(json);

            Path directory = server.getSavePath(WorldSavePath.ROOT).resolve("schematics");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(safeName + ".json"), json);

            selectedSchemes.put(player.getUuid(), scheme);

            player.sendMessage(Text.literal(
                    "§aAutoFarm: dodano schemat " + scheme.name()
                            + " (" + scheme.placements().size() + " bloków)."
            ), false);
        } catch (IOException exception) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: import schematu nie powiódł się: "
                            + exception.getMessage()
            ), false);
        }
    }

    public BuildRuntime runtime(UUID playerId) {
        return runtimes.get(playerId);
    }

    public String schemeName(UUID playerId) {
        BuildRuntime runtime = runtimes.get(playerId);

        if (runtime != null) {
            return runtime.scheme().name();
        }

        BuildScheme selected = selectedSchemes.get(playerId);

        return selected == null
                ? "Kaktusiarka 16×16"
                : selected.name();
    }

    public void start(ServerPlayerEntity player) {
        if (player.getServer() == null) return;

        if (!ServerPlayNetworking.canSend(player, BuildStatePayload.ID)) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: klient musi mieć zainstalowany AutoFarm."
            ), false);
            return;
        }

        BuildRuntime existingRuntime = runtimes.get(player.getUuid());

        if (existingRuntime != null
                && existingRuntime.status() == BuildStatus.BUILDING) {
            player.sendMessage(Text.literal(
                    "§eAutoFarm: budowa już trwa."
            ), false);
            return;
        }

        if (existingRuntime != null
                && existingRuntime.status() == BuildStatus.PAUSED) {
            existingRuntime.setStatus(BuildStatus.BUILDING);
            existingRuntime.setLastMessage("Building");
            sendBuildState(existingRuntime, false);

            player.sendMessage(Text.literal(
                    "§aAutoFarm: budowa wznowiona."
            ), false);
            return;
        }

        PlayerSelection selection = selections.get(player.getUuid());

        if (selection == null || !selection.isComplete()) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: najpierw ustaw oba narożniki (/autofarm set 1|2)."
            ), false);
            return;
        }

        if (selection.world() != player.getServerWorld()) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: zaznaczenie należy do innego wymiaru."
            ), false);
            return;
        }

        if (!selection.isSameChunk()) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: oba narożniki muszą wskazywać ten sam chunk 16×16."
            ), false);
            return;
        }

        int floors;

        try {
            floors = validateFloors(floorCount(player.getUuid()));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Text.literal("§cAutoFarm: " + exception.getMessage()), false);
            return;
        }

        BuildScheme scheme = selectedSchemes.get(player.getUuid());

        if (scheme == null) {
            scheme = BuildScheme.cactus(floors);
        }

        BlockPos origin = selection.minCorner();

        if (origin.getY() < selection.world().getBottomY()
                || origin.getY() + scheme.height()
                > selection.world().getBottomY() + selection.world().getHeight()) {
            player.sendMessage(Text.literal(
                    "§cAutoFarm: za mało miejsca nad wybranym chunkem."
            ), false);
            return;
        }

        BuildRuntime runtime = new BuildRuntime(
                player,
                player.getServerWorld(),
                origin,
                floors,
                scheme
        );

        runtimes.put(player.getUuid(), runtime);

        runtime.setStatus(BuildStatus.BUILDING);
        runtime.setLastMessage("Building");

        sendBuildState(runtime, true);

        player.sendMessage(Text.literal(
                "§aAutoFarm: budowanie schematu " + scheme.name()
                        + (selectedSchemes.containsKey(player.getUuid())
                        ? ""
                        : " (" + floors + " pięter)")
                        + " rozpoczęte."
        ), false);
    }

    public void stop(ServerPlayerEntity player) {
        BuildRuntime runtime = runtimes.get(player.getUuid());

        if (runtime == null) {
            player.sendMessage(Text.literal("§eAutoFarm: STOPPED"), false);
            return;
        }

        runtime.setStatus(BuildStatus.STOPPED);
        runtime.setLastMessage("Stopped");

        sendBuildState(runtime, false);

        player.sendMessage(Text.literal(
                "§eAutoFarm: budowanie zatrzymane."
        ), false);
    }

    public void deleteBuild(ServerPlayerEntity player) {
        BuildRuntime runtime = runtimes.remove(player.getUuid());
        selections.remove(player.getUuid());

        if (runtime != null) {
            runtime.setStatus(BuildStatus.STOPPED);
            runtime.setLastMessage("Build cleared");
            sendBuildState(runtime, false);
        }

        player.sendMessage(Text.literal(
                "§eAutoFarm: zadanie i zaznaczenie wyczyszczone. "
                        + "Postawione bloki pozostają."
        ), false);
    }

    public void pause(ServerPlayerEntity player) {
        BuildRuntime runtime = runtimes.get(player.getUuid());

        if (runtime == null || runtime.status() != BuildStatus.BUILDING) {
            player.sendMessage(Text.literal(
                    "§eAutoFarm: budowa nie jest uruchomiona."
            ), false);
            return;
        }

        runtime.setStatus(BuildStatus.PAUSED);
        runtime.setLastMessage("Paused by player");

        sendBuildState(runtime, false);

        player.sendMessage(Text.literal(
                "§eAutoFarm: budowa wstrzymana. Użyj /autofarm start, aby wznowić."
        ), false);
    }

    public void checkBlocks(ServerPlayerEntity player) {
        int floors = floorCount(player.getUuid());
        BuildScheme scheme = selectedSchemes.get(player.getUuid());

        Map<Item, Integer> required = scheme == null
                ? CactusScheme.requiredItems(floors)
                : scheme.requiredItems();

        Map<Item, Integer> owned = countInventory(player);

        player.sendMessage(Text.literal(
                scheme == null
                        ? "§6Materiały dla " + floors
                            + (floors == 1 ? " piętra:" : " pięter:")
                        : "§6Materiały schematu " + scheme.name() + ":"
        ), false);

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int have = owned.getOrDefault(entry.getKey(), 0);
            int missing = Math.max(0, entry.getValue() - have);

            String state = missing == 0
                    ? "§aOK"
                    : "§cBRAKUJE " + missing;

            player.sendMessage(Text.literal(
                    " - " + entry.getKey().getName().getString()
                            + ": " + entry.getValue()
                            + " wymagane, " + have + " masz (" + state + "§r)"
            ), false);
        }
    }

    public void sendInfo(ServerPlayerEntity player) {
        BuildRuntime runtime = runtimes.get(player.getUuid());
        int floors = runtime == null
                ? floorCount(player.getUuid())
                : runtime.floors();

        BuildScheme selected = selectedSchemes.get(player.getUuid());

        int total = runtime != null
                ? runtime.placements().size()
                : selected != null
                    ? selected.placements().size()
                    : CactusScheme.placements(floors).size();

        int index = runtime == null ? 0 : runtime.placementIndex();

        BuildStatus status = runtime == null
                ? BuildStatus.STOPPED
                : runtime.status();

        int percent = total == 0
                ? 0
                : (int) (index * 100L / total);

        player.sendMessage(Text.literal("§6=== AutoFarm ==="), false);
        player.sendMessage(Text.literal("Status: " + status), false);
        player.sendMessage(Text.literal(
                "Postęp: " + percent + "% (" + index + "/" + total + ")"
        ), false);

        String schemeLabel = schemeName(player.getUuid());

        player.sendMessage(Text.literal(
                "Projekt: " + schemeLabel
                        + (runtime == null ? ", " + floors + " pięter" : "")
        ), false);

        if (runtime != null) {
            player.sendMessage(Text.literal(
                    "Pozycja: " + runtime.origin().toShortString()
            ), false);

            player.sendMessage(Text.literal(
                    "Komunikat: " + runtime.lastMessage()
            ), false);
        }
    }

    public void tick() {
        for (BuildRuntime runtime : new ArrayList<>(runtimes.values())) {
            if (runtime.status() != BuildStatus.BUILDING) {
                if (runtime.ticksUntilSync() <= 0) {
                    sendBuildState(runtime, false);
                    runtime.setTicksUntilSync(5);
                } else {
                    runtime.setTicksUntilSync(runtime.ticksUntilSync() - 1);
                }
                continue;
            }

            if (runtime.ticksUntilSync() <= 0) {
                sendBuildState(runtime, false);
                runtime.setTicksUntilSync(5);
            } else {
                runtime.setTicksUntilSync(runtime.ticksUntilSync() - 1);
            }

            if (runtime.player().isRemoved() || !runtime.player().isAlive()) {
                runtime.setStatus(BuildStatus.PAUSED);
                runtime.setLastMessage("Player disconnected");
                continue;
            }

            ServerWorld world = runtime.world();

            if (!world.getServer().equals(server)) continue;

            if (runtime.player().getServerWorld() != world) {
                runtime.setStatus(BuildStatus.PAUSED);
                runtime.setLastMessage("Player left the build world");
                continue;
            }

            BlockPos center = runtime.origin().add(
                    runtime.scheme().width() / 2,
                    runtime.scheme().height() / 2,
                    runtime.scheme().depth() / 2
            );

            if (runtime.player().getBlockPos().getSquaredDistance(center) > 96.0 * 96.0) {
                runtime.setStatus(BuildStatus.PAUSED);
                runtime.setLastMessage("Player is too far from the build");
                continue;
            }

            while (runtime.placementIndex() < runtime.placements().size()) {
                CactusScheme.Placement placement =
                        runtime.placements().get(runtime.placementIndex());

                BlockPos targetPos = runtime.origin().add(
                        placement.x(), placement.y(), placement.z()
                );

                BlockState current = world.getBlockState(targetPos);

                if (current.getBlock() != placement.block()) break;

                runtime.setPlacementIndex(runtime.placementIndex() + 1);
            }

            if (runtime.placementIndex() >= runtime.placements().size()) {
                runtime.setStatus(BuildStatus.STOPPED);
                runtime.setLastMessage("Build complete");

                sendBuildState(runtime, false);

                runtime.player().sendMessage(Text.literal(
                        "§aAutoFarm: budowa zakończona!"
                ), false);

                continue;
            }

            CactusScheme.Placement placement =
                    runtime.placements().get(runtime.placementIndex());

            BlockPos targetPos = runtime.origin().add(
                    placement.x(), placement.y(), placement.z()
            );

            BlockState current = world.getBlockState(targetPos);

            if (!current.isAir() && !canReplace(current)) {
                runtime.setStatus(BuildStatus.PAUSED);
                runtime.setLastMessage(
                        "Existing block would be damaged: " + targetPos.toShortString()
                );

                runtime.player().sendMessage(Text.literal(
                        "§cAutoFarm: budowa wstrzymana, aby nie niszczyć bloku "
                                + targetPos.toShortString()
                ), false);

                continue;
            }

            Item requiredItem = itemForBlock(placement.block());

            int hotbarSlot = findHotbarItem(runtime.player(), requiredItem);

            if (requiredItem != net.minecraft.item.Items.AIR && hotbarSlot < 0) {
                runtime.setStatus(BuildStatus.PAUSED);
                runtime.setLastMessage(
                        "Required item is not in the hotbar: "
                                + requiredItem.getName().getString()
                );

                runtime.player().sendMessage(Text.literal(
                        "§cAutoFarm: przenieś "
                                + requiredItem.getName().getString()
                                + " na pasek szybkiego dostępu i wznowij budowę."
                ), false);
            }
        }
    }

    private void sendBuildState(BuildRuntime runtime, boolean includeScheme) {
        ServerPlayerEntity player = runtime.player();

        if (ServerPlayNetworking.canSend(player, BuildStatePayload.ID)) {
            ServerPlayNetworking.send(player, new BuildStatePayload(
                    runtime.origin(),
                    runtime.floors(),
                    runtime.placementIndex(),
                    runtime.placements().size(),
                    placementDelay(player.getUuid()),
                    runtime.status().ordinal(),
                    includeScheme ? runtime.placements() : List.of()
            ));
        }
    }

    private int findHotbarItem(ServerPlayerEntity player, Item item) {
        if (item == net.minecraft.item.Items.AIR) return -1;

        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    private boolean canReplace(BlockState state) {
        return state.isAir() || state.isReplaceable();
    }

    private Map<Item, Integer> countInventory(ServerPlayerEntity player) {
        Map<Item, Integer> counts = new HashMap<>();

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);

            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        return counts;
    }

    private Item itemForBlock(Block block) {
        if (block == net.minecraft.block.Blocks.WATER) {
            return net.minecraft.item.Items.WATER_BUCKET;
        }

        return block.asItem();
    }
}

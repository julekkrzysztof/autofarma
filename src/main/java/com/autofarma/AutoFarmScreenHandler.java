
package com.autofarma;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.text.Text;
import java.util.List;
import java.util.UUID;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AutoFarmScreenHandler extends GenericContainerScreenHandler {
    private final SimpleInventory inventory;
    private final AutoFarmManager manager;
    private final UUID playerId;

    public AutoFarmScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public AutoFarmScreenHandler(int syncId, PlayerInventory playerInventory, AutoFarmManager manager) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(54), 6);
        this.manager = manager;
        this.playerId = playerInventory.player.getUuid();
        this.inventory = (SimpleInventory) getInventory();
        refresh();
    }

    private void refresh() {
        inventory.clear();

        for (int slot = 0; slot < 54; slot++) {
            inventory.setStack(slot, named(Items.GRAY_STAINED_GLASS_PANE, " "));
        }

        inventory.setStack(10, named(Items.LIME_DYE, "§a▶ START",
                "§7Rozpoczyna automatyczne budowanie."));

        inventory.setStack(12, named(Items.RED_DYE, "§c■ STOP",
                "§7Zatrzymuje budowę."));

        inventory.setStack(14, named(Items.COMPASS, "§eSprawdź materiały",
                "§7Pokazuje brakujące bloki."));

        String status = "STOPPED";
        int percent = 0;
        int floors = 1;

        if (manager != null) {
            floors = Math.max(1, Math.min(30, manager.floorCount(playerId)));

            BuildRuntime runtime = manager.runtime(playerId);

            if (runtime != null) {
                status = runtime.status().name();
                floors = Math.max(1, Math.min(30, runtime.floors()));

                int total = runtime.placements().size();
                percent = total == 0 ? 0
                        : runtime.placementIndex() * 100 / total;

                percent = Math.max(0, Math.min(100, percent));
            }
        }

        String schemeName = manager == null
                ? "Kaktusiarka 16×16"
                : manager.schemeName(playerId);

        inventory.setStack(30, named(Items.CACTUS, "§2" + schemeName,
                "§7Schemat budowany blok po bloku.",
                "§7Ustaw narożniki w wybranym chunku."));

        inventory.setStack(32, named(Items.KNOWLEDGE_BOOK, "§bUstawianie pięter",
                "§7Użyj: /autofarm give <1-30>",
                "§7Następnie: /autofarm start",
                "§7Aktualnie: " + floors
                        + (floors == 1 ? " piętro" : " pięter")));

        inventory.setStack(38, named(Items.CLOCK, "§6Status: " + status,
                "§7Aktualny stan budowania."));

        inventory.setStack(40, named(Items.PAPER, "§bPostęp: " + percent + "%",
                "§7Liczba już postawionych bloków."));

        inventory.setStack(42, named(Items.SAND, "§eWybór chunku",
                "§7Użyj /autofarm set 1",
                "§7oraz /autofarm set 2."));
    }

    private ItemStack named(net.minecraft.item.Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));

        if (lore.length > 0) {
            List<Text> loreLines = new java.util.ArrayList<>(lore.length);

            for (String line : lore) {
                loreLines.add(Text.literal(line));
            }

            stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        }

        return stack;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onSlotClick(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player
    ) {
        if (slotIndex >= 0 && slotIndex < 54
                && player instanceof ServerPlayerEntity serverPlayer
                && manager != null) {

            switch (slotIndex) {
                case 10 -> manager.start(serverPlayer);
                case 12 -> manager.stop(serverPlayer);
                case 14 -> manager.checkBlocks(serverPlayer);
            }
        }
    }
}

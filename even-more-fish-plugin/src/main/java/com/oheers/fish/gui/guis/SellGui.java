package com.oheers.fish.gui.guis;

import com.oheers.fish.FishUtils;
import com.oheers.fish.api.economy.Economy;
import com.oheers.fish.api.economy.selling.SellHelper;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.config.gui.GuiConfig;
import com.oheers.fish.config.gui.impl.SellMenuConfirmGuiConfig;
import com.oheers.fish.config.gui.impl.SellMenuNormalGuiConfig;
import com.oheers.fish.gui.EMFConfigGui;
import de.themoep.inventorygui.GuiElement;
import de.themoep.inventorygui.GuiStorageElement;
import de.themoep.inventorygui.InventoryGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

// TODO look into dynamically updating the sell items when a fish is added/removed
public class SellGui extends EMFConfigGui {

    private final Inventory fishInventory;

    public SellGui(@NonNull Player player, @NonNull SellState sellState, @Nullable Inventory fishInventory) {
        super(sellState.getGuiConfig(), player);

        this.fishInventory = Optional.ofNullable(fishInventory).orElse(Bukkit.createInventory(player, 54));

        Economy economy = Economy.getInstance();

        addReplacement("{sell-price}", () -> {
            double shopSaleValue = FishUtils.calculateInventoryWorth(this.fishInventory);
            return economy.getWorthFormat(shopSaleValue, true);
        });
        addReplacement("{sell-all-price}", () -> {
            double playerSaleValue = FishUtils.calculateInventoryWorth(player.getInventory());
            return economy.getWorthFormat(playerSaleValue, true);
        });

        setCloseAction(close -> {
            if (MainConfig.getInstance().sellOverDrop()) {
                SellHelper.get().sell(this.fishInventory, this.player);
            }
            doRescue(close.getGui());
            return false;
        });
    }

    public Inventory getFishInventory() {
        return this.fishInventory;
    }

    @Override
    public void configure(@NonNull InventoryGui inventoryGui) {
        char character = FishUtils.getCharFromString(guiConfig.getConfig().getString("deposit-character", "i"), 'i');
        GuiStorageElement storageElement = new GuiStorageElement(character, this.fishInventory);
        GuiElement.Action defaultAction = storageElement.getAction(player);
        storageElement.setAction(click -> {
            if (click.getRawEvent() instanceof InventoryClickEvent event && isShiftClickFromPlayerInventory(event)) {
                return handleShiftClickDeposit(storageElement, event, click);
            }
            return defaultAction == null || defaultAction.onClick(click);
        });
        inventoryGui.addElement(storageElement);
    }

    private boolean isShiftClickFromPlayerInventory(@NonNull InventoryClickEvent event) {
        return event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            && event.getRawSlot() >= event.getView().getTopInventory().getSize();
    }

    /**
     * Moves a shift-clicked item from the player's inventory straight into the storage inventory.
     * <p>
     * InventoryGui lets vanilla move the item into the GUI while it writes the item into the storage separately,
     * which can make the two go out of sync and cause the item to vanish. Handling it here keeps the storage
     * as the single source of truth, so anything that isn't sold is always returned on close.
     *
     * @return true, as the original event should always be cancelled.
     */
    private boolean handleShiftClickDeposit(@NonNull GuiStorageElement element, @NonNull InventoryClickEvent event, GuiElement.@NonNull Click click) {
        event.setCancelled(true);
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.isEmpty()) {
            return true;
        }

        int capacity = Math.min(element.getSlots().length, this.fishInventory.getSize());
        ItemStack remaining = moving.clone();

        // Merge with similar stacks first
        for (int i = 0; i < capacity && remaining.getAmount() > 0; i++) {
            ItemStack existing = this.fishInventory.getItem(i);
            if (existing == null || existing.isEmpty() || !existing.isSimilar(remaining)) {
                continue;
            }
            int maxStack = Math.min(existing.getMaxStackSize(), this.fishInventory.getMaxStackSize());
            int space = maxStack - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int added = Math.min(space, remaining.getAmount());
            existing.setAmount(existing.getAmount() + added);
            this.fishInventory.setItem(i, existing);
            remaining.setAmount(remaining.getAmount() - added);
        }

        // Then fill empty slots
        for (int i = 0; i < capacity && remaining.getAmount() > 0; i++) {
            ItemStack existing = this.fishInventory.getItem(i);
            if (existing != null && !existing.isEmpty()) {
                continue;
            }
            this.fishInventory.setItem(i, remaining.clone());
            remaining.setAmount(0);
        }

        event.setCurrentItem(remaining.getAmount() > 0 ? remaining : null);
        click.getGui().draw(click.getWhoClicked(), false);
        return true;
    }

    @Override
    protected void addClickActions(@NonNull Map<String, ConfigGuiClickAction> map) {}

    public enum SellState {
        NORMAL(SellMenuNormalGuiConfig::getInstance),
        CONFIRM(SellMenuConfirmGuiConfig::getInstance);

        private final Supplier<GuiConfig> configSupplier;

        SellState(@NonNull Supplier<GuiConfig> configSupplier) {
            this.configSupplier = configSupplier;
        }

        public GuiConfig getGuiConfig() {
            return configSupplier.get();
        }
    }

}

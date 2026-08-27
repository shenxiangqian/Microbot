package net.runelite.client.plugins.microbot.SDK.BankTask;

import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Represents a single equipment-slot requirement within a {@link BankTask}.
 *
 * <p>Carries the equipment slot, item ID, and a quantity specification
 * ({@link BankAmount.Amount}).</p>
 *
 * <p>Example usage (mirrors Tribot's {@code EquipmentReq.slot()}):</p>
 * <pre>{@code
 * BankTask task = BankTask.builder()
 *     .addEquipmentItem(BankTask.EquipmentReq(EquipmentInventorySlot.RING).item(123, BankAmount.of(1)))
 *     .addEquipmentItem(BankTask.EquipmentReq(EquipmentInventorySlot.AMMO).item(995, BankAmount.fill(6000)))
 *     .build();
 * }</pre>
 *
 * @see BankTask
 * @see BankAmount
 * @see BankAmount.Amount
 */
public class EquipmentReq {

    /**
     */
    @Getter
    private final EquipmentInventorySlot slot;
    private volatile int itemId = -1;
    private volatile Supplier<Integer> resolver;
    /**
     */
    @Getter
    private final BankAmount.Amount amount;

    private EquipmentReq(EquipmentInventorySlot slot, int itemId, BankAmount.Amount amount) {
        this.slot = slot;
        this.itemId = itemId;
        this.amount = amount;
    }

    private EquipmentReq(EquipmentInventorySlot slot, Supplier<Integer> resolver, BankAmount.Amount amount) {
        this.slot = slot;
        this.resolver = resolver;
        this.amount = amount;
    }

    // ---------- static factory: Tribot's EquipmentReq.slot() entry-point ----------

    /**
     * Begins constructing an equipment requirement by specifying the slot.
     *
     * <pre>{@code
     * BankTask.EquipmentReq(EquipmentInventorySlot.RING).item(123, BankAmount.of(1))
     * BankTask.EquipmentReq(EquipmentInventorySlot.AMMO).item(995, BankAmount.fill(6000))
     * }</pre>
     *
     * @param slot the equipment slot
     * @return SlotBuilder for chaining
     */
    public static SlotBuilder slot(EquipmentInventorySlot slot) {
        return new SlotBuilder(slot);
    }

    // ---------- lazy resolution ----------

    /**
     * Resolves the item ID from the stored filter or name.
     * Call this once at the start of BankTask.execute() to populate itemId
     * before any bank operations are performed.
     *
     * <p>Safe to call multiple times; subsequent calls are no-ops after the
     * first resolution attempt.</p>
     */
    public void resolve() {
        if (itemId != -1 || resolver == null) {
            return;
        }
        int resolved = resolver.get();
        if (resolved != -1) {
            this.itemId = resolved;
        }
        this.resolver = null;
    }

    // ---------- query methods ----------

    /**
     * @return item ID required (-1 for empty slot or unresolved filter/name)
     */
    public int getItemId() {
        if (itemId == -1 && resolver != null) {
            resolve();
        }
        return itemId;
    }

    /** @return item name, "Empty" if itemId == -1, "Unknown" if not in cache */
    public String getItemName() {
        int id = getItemId();
        if (id == -1) return "Empty";
        Rs2ItemModel equipped = Rs2Equipment.get(slot);
        if (equipped != null && equipped.getId() == id) return equipped.getName();
        Rs2ItemModel bankItem = Rs2Bank.bankItems().stream()
                .filter(i -> i.getId() == id).findFirst().orElse(null);
        if (bankItem != null) return bankItem.getName();
        Rs2ItemModel invItem = Rs2Inventory.get(id);
        if (invItem != null) return invItem.getName();
        return "Unknown";
    }

    /** @return target minimum quantity */
    public int getTargetMin() { return amount.getTargetMin(); }

    /** @return target maximum quantity */
    public int getTargetMax() { return amount.getTargetMax(); }

    /** @return the quantity mode */
    public BankAmount getMode() { return amount.getMode(); }

    /** @return currently equipped item in this slot, or null */
    public Rs2ItemModel getCurrentEquipment() {
        return Rs2Equipment.get(slot);
    }

    /** @return how many of the required item are currently equipped (0 if wrong item or empty) */
    public int getCurrentEquipmentCount() {
        Rs2ItemModel item = getCurrentEquipment();
        if (item == null) return 0;
        int id = getItemId();
        if (id == -1 || item.getId() == id) return item.getQuantity();
        return 0;
    }

    /** @return how many of the required item are currently in the bank */
    public int getCurrentBankCount() {
        return Rs2Bank.count(getItemId());
    }

    /**
     * @return how many of the required item are currently in the inventory
     */
    public int getCurrentInventoryCount() {
        int id = getItemId();
        if (id == -1) return 0;
        return Rs2Inventory.count(id);
    }

    /**
     * @return total count across all containers: bank + inventory + equipped
     */
    public int getTotalAvailableCount() {
        int id = getItemId();
        if (id == -1) return 0;
        return getCurrentBankCount() + getCurrentInventoryCount() + getCurrentEquipmentCount();
    }

    /**
     * @return true if the required item is actually present somewhere (bank, inventory, or equipped).
     */
    public boolean existsInAnyContainer() {
        int id = getItemId();
        if (id == -1) return false;
        return Rs2Bank.count(id) > 0 || Rs2Inventory.count(id) > 0 || getCurrentEquipment() != null;
    }

    /**
     * @return true if total available (bank + inventory + equipped) meets the minimum target
     */
    public boolean hasRequiredItemsInBank() {
        int id = getItemId();
        if (id == -1) return true;
        if (!existsInAnyContainer()) return true;
        return getTotalAvailableCount() >= getTargetMin();
    }

    /** @return true if the equipment slot currently satisfies the requirement */
    public boolean isSatisfiedInEquipment() {
        Rs2ItemModel equipped = getCurrentEquipment();
        int id = getItemId();
        if (equipped == null) return id == -1;
        if (id != -1 && equipped.getId() != id) return false;
        return amount.getMode().isSatisfied(equipped.getQuantity(), getTargetMin());
    }

    /** @return true if we need to withdraw and equip an item */
    public boolean needsEquip() {
        return !isSatisfiedInEquipment() && hasRequiredItemsInBank();
    }

    @Override
    public String toString() {
        String itemDesc = getItemId() == -1 ? "(empty)" : getItemName() + " [ID:" + getItemId() + "]";
        return slot + ": " + itemDesc + " " + amount;
    }

    // ---------- helper lookup methods ----------

    private static int getIdByName(String name) {
        return Rs2Bank.bankItems().stream()
                .filter(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(name))
                .mapToInt(Rs2ItemModel::getId).findFirst()
                .orElseGet(() -> Rs2Inventory.all().stream()
                        .filter(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(name))
                        .mapToInt(Rs2ItemModel::getId).findFirst()
                        .orElseGet(() -> Rs2Equipment.all()
                                .filter(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(name))
                                .mapToInt(Rs2ItemModel::getId).findFirst()
                                .orElse(-1)));
    }

    private static int getFirstIdByFilter(Predicate<Rs2ItemModel> filter) {
        return Rs2Bank.bankItems().stream()
                .filter(i -> i != null && filter.test(i))
                .mapToInt(Rs2ItemModel::getId).findFirst()
                .orElseGet(() -> Rs2Inventory.all().stream()
                        .filter(i -> i != null && filter.test(i))
                        .mapToInt(Rs2ItemModel::getId).findFirst()
                        .orElseGet(() -> Rs2Equipment.all()
                                .filter(i -> i != null && filter.test(i))
                                .mapToInt(Rs2ItemModel::getId).findFirst()
                                .orElse(-1)));
    }

    // ---------- fluent intermediate builder ----------

    /**
     * Partial builder started by {@link #slot(EquipmentInventorySlot)}.
     */
    public static class SlotBuilder {
        private final EquipmentInventorySlot slot;

        SlotBuilder(EquipmentInventorySlot slot) { this.slot = slot; }

        /**
         * Specifies the required item and quantity.
         *
         * <pre>{@code
         * .item(123, BankAmount.of(1))        // exactly 1
         * .item(995, BankAmount.fill(6000))  // fill up to 6000
         * }</pre>
         *
         * @param itemId item ID
         * @param amount quantity specification
         * @return complete EquipmentRequirement
         */
        public EquipmentReq item(int itemId, BankAmount.Amount amount) {
            return new EquipmentReq(slot, itemId, amount);
        }

        /**
         * Specifies the required item by name and quantity.
         * The item ID is resolved lazily at execution time.
         *
         * @param itemName item name (case-insensitive)
         * @param amount   quantity specification
         * @return complete EquipmentRequirement
         */
        public EquipmentReq item(String itemName, BankAmount.Amount amount) {
            return new EquipmentReq(slot, () -> getIdByName(itemName), amount);
        }

        /**
         * Specifies the required item by filter and quantity.
         * The item ID is resolved lazily at execution time.
         *
         * @param filter item filter
         * @param amount quantity specification
         * @return complete EquipmentRequirement
         */
        public EquipmentReq item(Predicate<Rs2ItemModel> filter, BankAmount.Amount amount) {
            return new EquipmentReq(slot, () -> getFirstIdByFilter(filter), amount);
        }

        /**
         * Specifies the required item with an exact quantity.
         *
         * @param itemId item ID
         * @param amount exact quantity
         * @return complete EquipmentRequirement
         */
        public EquipmentReq itemOf(int itemId, int amount) {
            return new EquipmentReq(slot, itemId, BankAmount.of(amount));
        }

        /**
         * Specifies the required item with a range quantity.
         *
         * @param itemId item ID
         * @param min    minimum quantity
         * @param max    maximum quantity
         * @return complete EquipmentRequirement
         */
        public EquipmentReq itemRange(int itemId, int min, int max) {
            return new EquipmentReq(slot, itemId, BankAmount.range(min, max));
        }

        /**
         * Specifies the required item with a fill quantity.
         *
         * @param itemId item ID
         * @param target fill target
         * @return complete EquipmentRequirement
         */
        public EquipmentReq itemFill(int itemId, int target) {
            return new EquipmentReq(slot, itemId, BankAmount.fill(target));
        }

        /**
         * Ensures this slot is empty (no item required).
         *
         * @return complete EquipmentRequirement
         */
        public EquipmentReq empty() {
            return new EquipmentReq(slot, -1, BankAmount.of(0));
        }
    }
}

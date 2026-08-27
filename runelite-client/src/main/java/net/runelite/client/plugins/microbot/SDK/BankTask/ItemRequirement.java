package net.runelite.client.plugins.microbot.SDK.BankTask;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Represents a single inventory item requirement within a {@link BankTask}.
 *
 * <p>Carries the item ID together with a quantity specification
 * ({@link BankAmount.Amount}) that describes the exact, range, or fill mode.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * BankTask task = BankTask.builder()
 *     .addInvItem(995, BankAmount.of(100))           // exactly 100
 *     .addInvItem(995, BankAmount.range(50, 200))    // at least 50
 *     .addInvItem(995, BankAmount.fill(6000))        // top up to 6000
 *     .build();
 * }</pre>
 *
 * @see BankTask
 * @see BankAmount
 * @see BankAmount.Amount
 */
public class ItemRequirement {

    private volatile int itemId = -1;
    private volatile Supplier<Integer> resolver;
    /**
     * -- GETTER --
     *
     */
    @Getter
    private final BankAmount.Amount amount;

    ItemRequirement(int itemId, BankAmount.Amount amount) {
        this.itemId = itemId;
        this.amount = amount;
    }

    private ItemRequirement(Supplier<Integer> resolver, BankAmount.Amount amount) {
        this.resolver = resolver;
        this.amount = amount;
    }

    // ---------- factory methods (int itemId — resolved at construction) ----------

    static ItemRequirement of(int itemId, int exactAmount) {
        return new ItemRequirement(itemId, BankAmount.of(exactAmount));
    }

    static ItemRequirement of(int itemId, BankAmount.Amount amount) {
        return new ItemRequirement(itemId, amount);
    }

    static ItemRequirement range(int itemId, int min, int max) {
        return new ItemRequirement(itemId, BankAmount.range(min, max));
    }

    static ItemRequirement range(int itemId, BankAmount.Amount amount) {
        return new ItemRequirement(itemId, amount);
    }

    static ItemRequirement fill(int itemId, int target) {
        return new ItemRequirement(itemId, BankAmount.fill(target));
    }

    static ItemRequirement fill(int itemId, BankAmount.Amount amount) {
        return new ItemRequirement(itemId, amount);
    }

    static ItemRequirement fillButOne(int itemId, int target) {
        return new ItemRequirement(itemId, BankAmount.fillButOne(target));
    }

    static ItemRequirement fillButOne(int itemId, BankAmount.Amount amount) {
        return new ItemRequirement(itemId, amount);
    }

    // ---------- factory methods (String name — lazy resolution at resolve() time) ----------

    static ItemRequirement of(String itemName, int exactAmount) {
        return new ItemRequirement(() -> getIdByName(itemName), BankAmount.of(exactAmount));
    }

    static ItemRequirement of(String itemName, BankAmount.Amount amount) {
        return new ItemRequirement(() -> getIdByName(itemName), amount);
    }

    static ItemRequirement range(String itemName, int min, int max) {
        return new ItemRequirement(() -> getIdByName(itemName), BankAmount.range(min, max));
    }

    static ItemRequirement range(String itemName, BankAmount.Amount amount) {
        return new ItemRequirement(() -> getIdByName(itemName), amount);
    }

    static ItemRequirement fill(String itemName, int target) {
        return new ItemRequirement(() -> getIdByName(itemName), BankAmount.fill(target));
    }

    static ItemRequirement fill(String itemName, BankAmount.Amount amount) {
        return new ItemRequirement(() -> getIdByName(itemName), amount);
    }

    // ---------- factory methods (Predicate — lazy resolution at resolve() time) ----------

    static ItemRequirement of(Predicate<Rs2ItemModel> filter, int exactAmount) {
        return new ItemRequirement(() -> getFirstIdByFilter(filter), BankAmount.of(exactAmount));
    }

    static ItemRequirement of(Predicate<Rs2ItemModel> filter, BankAmount.Amount amount) {
        return new ItemRequirement(() -> getFirstIdByFilter(filter), amount);
    }

    static ItemRequirement range(Predicate<Rs2ItemModel> filter, int min, int max) {
        return new ItemRequirement(() -> getFirstIdByFilter(filter), BankAmount.range(min, max));
    }

    static ItemRequirement fill(Predicate<Rs2ItemModel> filter, int target) {
        return new ItemRequirement(() -> getFirstIdByFilter(filter), BankAmount.fill(target));
    }

    static ItemRequirement fill(Predicate<Rs2ItemModel> filter, BankAmount.Amount amount) {
        return new ItemRequirement(() -> getFirstIdByFilter(filter), amount);
    }

    static ItemRequirement fillButOne(Predicate<Rs2ItemModel> filter, int target) {
        return new ItemRequirement(() -> getFirstIdByFilter(filter), BankAmount.fillButOne(target));
    }

    // ---------- lazy resolution ----------

    /**
     * Resolves the item ID from the stored filter or name.
     * Call this once at the start of BankTask.execute() to populate itemId
     * from the filter/name before any bank operations are performed.
     *
     * <p>Safe to call multiple times; subsequent calls are no-ops after the
     * first successful resolution.</p>
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
     * @return item ID (resolves lazily on first call if created via filter/name)
     */
    public int getItemId() {
        if (itemId == -1 && resolver != null) {
            resolve();
        }
        return itemId;
    }

    /**
     * @return item name from game cache, "Unknown" if not found
     */
    public String getItemName() {
        int id = getItemId();
        Rs2ItemModel item = Rs2Inventory.get(id);
        if (item != null) return item.getName();
        Rs2ItemModel bankItem = Rs2Bank.bankItems().stream()
                .filter(i -> i.getId() == id).findFirst().orElse(null);
        if (bankItem != null) return bankItem.getName();
        Rs2ItemModel eqItem = Rs2Equipment.get(id);
        if (eqItem != null) return eqItem.getName();
        return "Unknown";
    }

    /**
     * @return target minimum quantity
     */
    public int getTargetMin() {
        return amount.getTargetMin();
    }

    /**
     * @return target maximum quantity
     */
    public int getTargetMax() {
        return amount.getTargetMax();
    }

    /**
     * @return the quantity mode
     */
    public BankAmount getMode() {
        return amount.getMode();
    }

    /**
     * @return current inventory count, 0 if not present
     */
    public int getCurrentInventoryCount() {
        return Rs2Inventory.count(getItemId());
    }

    public int getTotalAvailableCount() {
        int id = getItemId();
        if (id == -1) return 0;
        return getCurrentBankCount() + getCurrentInventoryCount();
    }

    /**
     * @return current bank count, 0 if not present
     */
    public int getCurrentBankCount() {
        return Rs2Bank.count(getItemId());
    }

    /**
     * @return true if inventory satisfies this requirement
     */
    public boolean isSatisfiedInInventory() {
        int current = getCurrentInventoryCount();
        switch (getMode()) {
            case EXACT:
                return current == getTargetMin() && current == getTargetMax();
            case RANGE:
            case FILL:
            case FILL_BUT_ONE:
            case NONE:
                return current >= getTargetMin();
            default:
                return false;
        }
    }

    /**
     * Checks whether the combined count (inventory + equipment) satisfies the requirement.
     *
     * @return true if inventory + equipment together meet the requirement
     */
    public boolean isSatisfied() {
        int invCount = getCurrentInventoryCount();
        int eqCount = getCurrentEquippedCount();
        int total = invCount + eqCount;
        switch (getMode()) {
            case EXACT:
                return total == getTargetMin() && total == getTargetMax();
            case RANGE:
            case FILL:
            case FILL_BUT_ONE:
            case NONE:
                return total >= getTargetMin();
            default:
                return false;
        }
    }

    /**
     * @return current equipped count of the required item (0 if not equipped)
     */
    int getCurrentEquippedCount() {
        int id = getItemId();
        if (id <= 0) return 0;
        Rs2ItemModel item = Rs2Equipment.get(id);
        return item != null ? item.getQuantity() : 0;
    }

    /**
     * @return true if this item is equippable (item ID is known)
     */
    public boolean isEquippable() {
        return getItemId() > 0;
    }

    /**
     * @return true if the required item actually exists somewhere (bank, inventory, or equipped).
     */
    private boolean existsInAnyContainer() {
        int id = getItemId();
        if (id <= 0) return false;
        return Rs2Bank.count(id) > 0 || Rs2Inventory.count(id) > 0 || getCurrentEquippedCount() > 0;
    }

    /**
     * @return true if the total available count (bank + inventory + equipped) is sufficient
     */
    public boolean hasRequiredItemsInBank() {
        int id = getItemId();
        if (id <= 0) return false;
        if (!existsInAnyContainer()) return false;
        int available = getCurrentBankCount() + getCurrentInventoryCount() + getCurrentEquippedCount();
        return available >= getTargetMin();
    }

    /**
     * @return true if we need and can withdraw
     */
    public boolean canWithdraw() {
        return !isSatisfiedInInventory() && getCurrentBankCount() > 0;
    }

    /**
     * How many items should be withdrawn from bank.
     *
     * @return items to withdraw, 0 if already satisfied
     */
    public int getWithdrawAmount() {
        if (isSatisfiedInInventory()) return 0;
        int invCount = getCurrentInventoryCount();
        int eqCount = getCurrentEquippedCount();
        int total = invCount + eqCount;
        int bankCount = getCurrentBankCount();
        switch (getMode()) {
            case EXACT:
                return Math.max(0, getTargetMin() - total);
            case RANGE:
            case FILL:
            case FILL_BUT_ONE:
            case NONE:
                int needMin = getTargetMin() - total;
                if (needMin <= 0) return 0;
                int available = Math.min(getTargetMax() - total, bankCount);
                if (available <= 0) return 0;
                if (available <= needMin) return needMin;
                return Rs2Random.between(needMin, available);
            default:
                return 0;
        }
    }

    /**
     * How many items should be deposited to bank.
     *
     * @return items to deposit, 0 if already satisfied
     */
    public int getDepositAmount() {
        if (getMode() == BankAmount.NONE) return 0;
        int invCount = getCurrentInventoryCount();
        int eqCount = getCurrentEquippedCount();
        int total = invCount + eqCount;
        int targetMax = getTargetMax();
        int overflow = total - targetMax;
        if (overflow <= 0) return 0;
        return Math.min(invCount, overflow);
    }

    @Override
    public String toString() {
        return getItemName() + " x" + amount + " [ID:" + getItemId() + "]";
    }

    /**
     * Finds the first item ID matching the given predicate by scanning bank, inventory, then equipment.
     *
     * @param filter item predicate
     * @return item ID of the first match, or -1 if none found
     */
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

    /**
     * Looks up an item ID by name, scanning bank, inventory, then equipment.
     *
     * @param name item name (case-insensitive)
     * @return item ID, or -1 if not found
     */
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
}

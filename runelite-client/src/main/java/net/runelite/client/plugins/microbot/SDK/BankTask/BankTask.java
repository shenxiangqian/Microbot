package net.runelite.client.plugins.microbot.SDK.BankTask;

import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;


/**
 * A bank execution task that defines what items should be in the inventory
 * and/or equipment when the task completes.
 *
 * <p>This class mirrors the Tribot {@code BankTask} builder pattern:</p>
 * <pre>{@code
 * BankTask task = BankTask.builder()
 *     .addEquipmentItem(EquipmentReq.slot(EquipmentInventorySlot.RING).item(123, BankAmount.of(1)))
 *     .addEquipmentItem(EquipmentReq.slot(EquipmentInventorySlot.AMMO).item(123, BankAmount.fill(6000)))
 *     .addInvItem(123, BankAmount.of(100))
 *     .addInvItem(123, BankAmount.range(100,200))
 *     .build();
 *
 * if (!task.hasRequiredItems()) Log.error("No item");
 * if (!task.isSatisfied()) task.execute();
 * }</pre>
 *
 * <p>Execution phases (in order):</p>
 * <ol>
 *   <li>Walk to nearest bank</li>
 *   <li>Open bank</li>
 *   <li>Equip tasks: unequip wrong items, withdraw needed items, equip</li>
 *   <li>Inventory tasks: withdraw needed items</li>
 *   <li>Close bank</li>
 * </ol>
 *
 * @see BankTask.Builder
 * @see ItemRequirement
 * @see EquipmentReq
 */
@Getter
public class BankTask {

    private final List<EquipmentReq> equipmentRequirements;
    private final List<ItemRequirement> invRequirements;

    private BankTask(Builder builder) {
        this.equipmentRequirements = List.copyOf(builder.equipmentRequirements);
        this.invRequirements = List.copyOf(builder.invRequirements);
    }

    private static final int INVENTORY_SIZE = 28;

    /**
     * Checks whether the inventory has at least one empty slot.
     */
    private boolean hasAvailableInventorySpace() {
        return !Rs2Inventory.isFull();
    }

    /**
     * Guards bank-interaction operations. If the bank is not open when an operation
     * requires it, the task is aborted immediately with a {@code BANK_CLOSED} result.
     *
     * @param operation description of the operation being guarded
     * @return a failure result if the bank is closed, or null if the bank is open (proceed)
     */
    private BankExecuteResult requireBankOpen(String operation) {
        if (sleepUntil(Rs2Bank::isOpen, 3000)) {
            return null;
        }
        Microbot.log("[BankTask] Bank closed or not loaded during: " + operation + " — aborting task.");
        return BankExecuteResult.failure(
                this, BankExecuteResult.Status.BANK_CLOSED,
                "Bank closed during: " + operation
        );
    }

    /**
     * Simple bank-open check using the same sleepUntil pattern.
     *
     * @return true if the bank is open, false otherwise
     */
    private boolean checkBankOpen() {
        return sleepUntil(Rs2Bank::isOpen, 3000);
    }

    /**
     * Returns the set of item IDs that are "required" — i.e. they belong to
     * either an equipment requirement or an inventory requirement.
     */
    private List<Integer> getRequiredItemIds() {
        return Stream.concat(
                equipmentRequirements.stream()
                        .filter(req -> req.getItemId() != -1)
                        .map(EquipmentReq::getItemId),
                invRequirements.stream()
                        .map(ItemRequirement::getItemId)
        ).collect(Collectors.toList());
    }

    /**
     * Checks whether an item is "required" by any of the current requirements.
     */
    private boolean isRequiredItem(Rs2ItemModel item) {
        if (item == null) return false;
        return getRequiredItemIds().contains(item.getId());
    }

    /**
     * Deposits every item in the inventory that is NOT required by any
     * equipment or inventory requirement, and items that exceed the required quantity.
     *
     * @return true if every non-required item was successfully deposited
     */
    private boolean depositNonRequiredInventoryItems() {
        if (!checkBankOpen()) return false;
        Microbot.log("[BankTask] Depositing non-required inventory items...");
        boolean allDeposited = true;
        int freeBefore = Rs2Inventory.emptySlotCount();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (!checkBankOpen()) return false;
            if (item == null) continue;
            if (isRequiredItem(item)) continue;
            int amount = Rs2Inventory.count(item.getId());
            Microbot.log("[BankTask]   Depositing non-required item: " + item.getName() + " x" + amount);
            if (!depositItemSmart(item, amount)) {
                Microbot.log("[BankTask]   Failed to deposit " + item.getName());
                allDeposited = false;
            } else if (amount > 1) {
                final int freeBefore2 = freeBefore;
                sleepUntil(() -> Rs2Inventory.emptySlotCount() > freeBefore2, 3000);
            }
        }
        return allDeposited;
    }

    /**
     * Deposits items from the inventory that are required but present in
     * excess of the target maximum.
     *
     * @return true if every excess item was successfully deposited
     */
    private boolean depositExcessInventoryItems() {
        if (!checkBankOpen()) return false;
        Microbot.log("[BankTask] Depositing excess inventory items...");
        boolean allDeposited = true;
        for (ItemRequirement req : invRequirements) {
            if (!checkBankOpen()) return false;
            int depositAmt = req.getDepositAmount();
            if (depositAmt <= 0) continue;
            Rs2ItemModel item = Rs2Inventory.get(req.getItemId());
            if (item == null) continue;
            Microbot.log("[BankTask]   Depositing " + depositAmt + " excess " + req.getItemName());
            if (!depositItemSmart(item, depositAmt)) {
                Microbot.log("[BankTask]   Failed to deposit excess " + req.getItemName());
                allDeposited = false;
            } else if (depositAmt > 1) {
                final int id = req.getItemId();
                final int qty = depositAmt;
                sleepUntil(() -> Rs2Inventory.count(id) < qty, 3000);
            }
        }
        return allDeposited;
    }

    /**
     * Deposits every inventory item that does NOT appear in any inventory
     * requirement (and is not needed by any equipment requirement).
     *
     * @return true if all such items were deposited
     */
    private boolean depositInventoryItemsNotInRequirements() {
        if (!checkBankOpen()) return false;
        List<Integer> required = getRequiredItemIds();
        boolean allOk = true;
        int freeBefore = Rs2Inventory.emptySlotCount();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (!checkBankOpen()) return false;
            if (item == null) continue;
            if (required.contains(item.getId())) continue;
            int amount = Rs2Inventory.count(item.getId());
            if (amount > 0)
                Microbot.log("[BankTask]   Depositing item not in requirements: " + item.getName() + " x" + amount);
            if (!depositItemSmart(item, amount)) {
                Microbot.log("[BankTask]   Failed to deposit " + item.getName());
                allOk = false;
            } else if (amount > 1) {
                final int freeBefore2 = freeBefore;
                sleepUntil(() -> Rs2Inventory.emptySlotCount() > freeBefore2, 3000);
            }
        }
        return allOk;
    }

    // ---------- query methods ----------

    /**
     * Checks whether the bank currently contains enough items to potentially
     * satisfy all requirements.
     *
     * @return true if any requirement cannot be met
     */
    public boolean missingRequiredItems() {
        return equipmentRequirements.stream().anyMatch(req -> {
            var isMissing = req.getTotalAvailableCount() < req.getTargetMin();
            if (isMissing) Microbot.log("[BankTask] Not enough items for equipment slot " + req.getSlot());
            return isMissing;
        }) || invRequirements.stream().anyMatch(req -> {
            var isMissing = req.getTotalAvailableCount() < req.getTargetMin();
            if (isMissing) Microbot.log("[BankTask] Not enough items for " + req.getItemId());
            return isMissing;
        });
    }

    /**
     * Checks whether the current state (inventory + equipment) already satisfies
     * all requirements — no banking needed.
     *
     * @return true if every requirement is already met
     */
    public boolean isSatisfied() {
        depositInventoryItemsNotInRequirements();

        for (EquipmentReq req : equipmentRequirements) {
            if (!req.isSatisfiedInEquipment()) {
                return false;
            }
        }

        for (ItemRequirement req : invRequirements) {
            if (req.getItemId() <= 0) {
                continue;
            }
            if (!req.isSatisfied()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a list of requirements that are currently not satisfied
     * and cannot be fulfilled.
     *
     * @return list of unsatisfied requirements that cannot be fulfilled
     */
    public List<String> getUnsatisfiedRequirements() {
        List<String> unsatisfied = new ArrayList<>();
        for (EquipmentReq req : equipmentRequirements) {
            if (!req.hasRequiredItemsInBank()) {
                unsatisfied.add(req.toString());
            }
        }
        for (ItemRequirement req : invRequirements) {
            if (req.getItemId() <= 0) {
                continue;
            }
            if (!req.hasRequiredItemsInBank()) {
                unsatisfied.add(req.toString());
            }
        }
        return unsatisfied;
    }

    /**
     * Returns a human-readable summary of the current state vs. requirements.
     *
     * @return formatted status string
     */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== BankTask Status ===\n");
        if (equipmentRequirements.isEmpty() && invRequirements.isEmpty()) {
            sb.append("  (no requirements defined)\n");
        }
        for (EquipmentReq req : equipmentRequirements) {
            int current = req.getCurrentEquipmentCount();
            int target = req.getTargetMin();
            String status = req.isSatisfiedInEquipment() ? "[OK]" : "[MISSING]";
            sb.append("  ").append(status).append(" ")
                    .append(req.getSlot()).append(": ")
                    .append(req.getItemName()).append(" (")
                    .append(current).append("/").append(target).append(")\n");
        }
        for (ItemRequirement req : invRequirements) {
            int current = req.getCurrentInventoryCount();
            int target = req.getTargetMin();
            String status;
            if (req.getItemId() <= 0) {
                status = "[INVALID]";
            } else if (req.isSatisfiedInInventory()) {
                status = "[OK]";
            } else {
                status = "[MISSING]";
            }
            sb.append("  ").append(status).append(" ")
                    .append(req.getItemName()).append(" (")
                    .append(current).append("/").append(target).append(")\n");
        }
        return sb.toString();
    }

    // ---------- execution ----------

    /**
     * Executes the banking task: walks to a bank, opens it, performs all
     * withdrawals and equipment changes, then closes the bank.
     *
     * @return execution result containing success/failure status and details
     * @see BankExecuteResult
     */
    public BankExecuteResult execute() {
        Microbot.log("[BankTask] Starting bank task execution...");

        if (!checkBankOpen()) {
            Microbot.log("[BankTask] Bank is not open");
            return BankExecuteResult.failure(this, BankExecuteResult.Status.BANK_CLOSED, "Bank is not open");
        }

        BankExecuteResult bankCheck = requireBankOpen("initial bank check");
        if (bankCheck != null) return bankCheck;

        equipmentRequirements.forEach(EquipmentReq::resolve);
        invRequirements.forEach(ItemRequirement::resolve);
        Microbot.log("[BankTask] Requirements after resolution:\n" + this.getStatusSummary());

        if (missingRequiredItems()) {
            Microbot.log("[BankTask] Bank does not have required items!");
            return BankExecuteResult.missingItems(this);
        }

        if (!hasAvailableInventorySpace()) {
            Microbot.log("[BankTask] Inventory full — freeing space before main processing...");
            if (!checkBankOpen()) {
                Microbot.log("[BankTask] Bank closed unexpectedly during pre-flight deposit.");
                return BankExecuteResult.failure(
                        this, BankExecuteResult.Status.BANK_CLOSED, "Bank closed during pre-flight deposit"
                );
            }
            depositExcessInventoryItems();
            depositInventoryItemsNotInRequirements();
            Microbot.log("[BankTask] Inventory space cleared. Free slots: " + (INVENTORY_SIZE - Rs2Inventory.all().size()));
        }

        bankCheck = requireBankOpen("equipment deposit phase");
        if (bankCheck != null) return bankCheck;

        Microbot.log("[BankTask] Processing equipment requirements...");
        for (EquipmentReq req : equipmentRequirements) {
            BankExecuteResult result = processEquipmentReq(req);
            if (!result.isSuccess()) return result;
            if (checkBankOpen()) depositNonRequiredInventoryItems();
        }

        Microbot.log("[BankTask] Processing inventory requirements...");
        for (ItemRequirement req : invRequirements) {
            BankExecuteResult result = processInvReq(req);
            if (!result.isSuccess()) return result;
            if (checkBankOpen()) depositNonRequiredInventoryItems();
        }

        Microbot.log("[BankTask] Depositing all non-required inventory items...");
        if (!checkBankOpen()) {
            Microbot.log("[BankTask] Bank closed unexpectedly — aborting final deposit phase.");
        } else if (!depositNonRequiredInventoryItems()) {
            Microbot.log("[BankTask] Some non-required inventory items could not be deposited.");
        }

        if (isSatisfied()) {
            Microbot.log("[BankTask] Bank task completed successfully.");
            return BankExecuteResult.success(this);
        } else {
            Microbot.log("[BankTask] Bank task finished but some requirements are not satisfied:");
            getUnsatisfiedRequirements().forEach(r -> Microbot.log("  - " + r));
            return BankExecuteResult.partial(this, getUnsatisfiedRequirements());
        }
    }


    // ---------- internal processing ----------

    private BankExecuteResult processEquipmentReq(EquipmentReq req) {
        Microbot.log("[BankTask] Processing equipment slot: " + req.getSlot());

        BankExecuteResult bankCheck = requireBankOpen("equipment slot " + req.getSlot());
        if (bankCheck != null) return bankCheck;

        if (req.isSatisfiedInEquipment()) {
            Microbot.log("[BankTask]   Slot " + req.getSlot() + " already satisfied.");
            return BankExecuteResult.success(this);
        }

        if (req.getCurrentInventoryCount() > 0) {
            Microbot.log("[BankTask]   Equipping " + req.getItemName() + " from inventory for " + req.getSlot() + "...");
            if (req.getSlot() == EquipmentInventorySlot.AMMO) {
                Rs2Inventory.interact(req.getItemId(), "Wield");
            } else {
                Rs2Inventory.wear(req.getItemName());
            }
            final EquipmentReq reqFinal = req;
            sleepUntil(reqFinal::isSatisfiedInEquipment, 3000);
            if (req.isSatisfiedInEquipment()) {
                Microbot.log("[BankTask]   Slot " + req.getSlot() + " satisfied via inventory equip.");
                return BankExecuteResult.success(this);
            }
            Microbot.log("[BankTask]   Inventory equip did not fully satisfy the slot — continuing.");
        }

        int invCount = req.getCurrentInventoryCount() + req.getCurrentEquipmentCount();
        int toWithdraw = req.getAmount().withdrawAmount(invCount, req.getCurrentBankCount(), req.getTargetMax());

        if (toWithdraw <= 0) {
            if (!req.hasRequiredItemsInBank()) {
                return BankExecuteResult.missingItems(this);
            }
            Microbot.log("[BankTask]   No withdrawal needed for slot " + req.getSlot());
            return BankExecuteResult.success(this);
        }

        Microbot.log("[BankTask]   Withdrawing " + toWithdraw + " x " + req.getItemName() + " for " + req.getSlot() + "...");
        if (!Rs2Bank.withdrawX(req.getItemId(), toWithdraw)) {
            return BankExecuteResult.failure(
                    this, BankExecuteResult.Status.WITHDRAW_FAILED,
                    "Could not withdraw " + req.getItemName() + " for slot " + req.getSlot()
            );
        }
        final int itemId = req.getItemId();
        sleepUntil(() -> Rs2Inventory.contains(itemId), 3000);
        BankExecuteResult bankCheck2 = requireBankOpen("equipment slot " + req.getSlot() + " after withdraw");
        if (bankCheck2 != null) return bankCheck2;

        Microbot.log("[BankTask]   Equipping " + req.getItemName() + " in " + req.getSlot() + "...");
        if (req.getSlot() == EquipmentInventorySlot.AMMO) {
            Rs2Inventory.interact(req.getItemId(), "Wield");
        } else {
            Rs2Inventory.wear(req.getItemName());
        }
        sleepUntil(req::isSatisfiedInEquipment, 3000);

        if (req.isSatisfiedInEquipment()) {
            Microbot.log("[BankTask]   Slot " + req.getSlot() + " satisfied after processing.");
            return BankExecuteResult.success(this);
        } else {
            Microbot.log("[BankTask]   Slot " + req.getSlot() + " still not satisfied after processing.");
            return BankExecuteResult.failure(
                    this, BankExecuteResult.Status.EQUIP_FAILED,
                    "Equipment slot " + req.getSlot() + " not satisfied after processing"
            );
        }
    }

    private BankExecuteResult processInvReq(ItemRequirement req) {
        Microbot.log("[BankTask] Processing inventory item: " + req.getItemName());

        BankExecuteResult bankCheck = requireBankOpen("inventory item " + req.getItemName());
        if (bankCheck != null) return bankCheck;

        if (req.getItemId() <= 0) {
            Microbot.log("[BankTask]   Skipping invalid requirement: " + req.getItemName());
            return BankExecuteResult.success(this);
        }

        int depositAmt = req.getDepositAmount();
        if (depositAmt > 0) {
            Rs2ItemModel item = Rs2Inventory.get(req.getItemId());
            if (item != null) {
                Microbot.log("[BankTask]   Depositing " + depositAmt + " excess " + req.getItemName());
                if (!depositItemSmart(item, depositAmt)) {
                    return BankExecuteResult.failure(
                            this, BankExecuteResult.Status.DEPOSIT_FAILED,
                            "Could not deposit excess " + req.getItemName()
                    );
                }
                final int id = req.getItemId();
                final int qty = depositAmt;
                if (depositAmt > 1) {
                    sleepUntil(() -> Rs2Inventory.count(id) < qty, 3000);
                    bankCheck = requireBankOpen("deposit for " + req.getItemName());
                    if (bankCheck != null) return bankCheck;
                } else {
                    sleepUntil(() -> Rs2Inventory.count(id) == 0, 3000);
                    bankCheck = requireBankOpen("deposit for " + req.getItemName());
                    if (bankCheck != null) return bankCheck;
                }
            }
        }

        if (req.isSatisfied()) {
            Microbot.log("[BankTask]   Item " + req.getItemName() + " satisfied after excess deposit.");
            return BankExecuteResult.success(this);
        }

        int toWithdraw = req.getWithdrawAmount();
        if (toWithdraw <= 0) {
            if (!req.hasRequiredItemsInBank()) {
                return BankExecuteResult.missingItems(this);
            }
            Microbot.log("[BankTask]   Item " + req.getItemName() + " — no withdrawal needed.");
            return BankExecuteResult.success(this);
        }

        Microbot.log("[BankTask]   Withdrawing " + toWithdraw + " x " + req.getItemName() + "...");
        if (!Rs2Bank.withdrawX(req.getItemId(), toWithdraw)) {
            return BankExecuteResult.failure(
                    this, BankExecuteResult.Status.WITHDRAW_FAILED,
                    "Could not withdraw " + req.getItemName()
            );
        }

        bankCheck = requireBankOpen("inventory item " + req.getItemName() + " after withdraw");
        if (bankCheck != null) return bankCheck;

        sleepUntil(req::isSatisfied, 3000);

        if (req.isSatisfied()) {
            Microbot.log("[BankTask]   Item " + req.getItemName() + " satisfied after withdrawal.");
            return BankExecuteResult.success(this);
        } else {
            Microbot.log("[BankTask]   Item " + req.getItemName() + " still not satisfied after withdrawal.");
            return BankExecuteResult.failure(
                    this, BankExecuteResult.Status.WITHDRAW_INCOMPLETE,
                    "Incomplete withdrawal of " + req.getItemName()
            );
        }
    }

    /**
     * Deposits an item using the most efficient method available.
     *
     * @param item   the item to deposit (must be non-null)
     * @param amount how many to deposit
     * @return true if the deposit succeeded
     */
    private boolean depositItemSmart(Rs2ItemModel item, int amount) {
        if (item == null || amount <= 0) {
            return true;
        }
        if (!checkBankOpen()) {
            return false;
        }
        final int id = item.getId();
        if (amount > 1) {
            Microbot.log("[BankTask]   Using Deposit-All for " + item.getName() + " x" + amount);
            return Rs2Bank.depositAll(id) && sleepUntil(() -> !Rs2Inventory.contains(id), 3000);
        } else {
            int before = Rs2Inventory.count(id);
            return Rs2Bank.depositOne(id) && sleepUntil(() -> Rs2Inventory.count(id) < before, 3000);
        }
    }

    // ---------- builder ----------

    /**
     * Creates a new {@link BankTask} builder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<EquipmentReq> equipmentRequirements = new ArrayList<>();
        private final List<ItemRequirement> invRequirements = new ArrayList<>();

        /**
         * Adds an equipment slot requirement.
         */
        public Builder addEquipmentItem(EquipmentReq requirement) {
            this.equipmentRequirements.add(requirement);
            return this;
        }

        /**
         * Adds an inventory item requirement using a {@link BankAmount.Amount} quantity spec.
         */
        public Builder addInvItem(int itemId, BankAmount.Amount amount) {
            this.invRequirements.add(new ItemRequirement(itemId, amount));
            return this;
        }

        /**
         * Adds an inventory item requirement with an exact quantity.
         */
        public Builder addInvItem(int itemId, int amount) {
            this.invRequirements.add(ItemRequirement.of(itemId, amount));
            return this;
        }

        /**
         * Adds an inventory item requirement with a range quantity.
         */
        public Builder addInvItem(int itemId, int min, int max) {
            this.invRequirements.add(ItemRequirement.range(itemId, min, max));
            return this;
        }

        /**
         * Adds an inventory item requirement with a fill quantity.
         */
        public Builder addInvItemFill(int itemId, int target) {
            this.invRequirements.add(ItemRequirement.fill(itemId, target));
            return this;
        }

        /**
         * Adds an inventory item requirement by name with an exact quantity.
         */
        public Builder addInvItem(String itemName, int amount) {
            this.invRequirements.add(ItemRequirement.of(itemName, amount));
            return this;
        }

        /**
         * Adds an inventory item requirement by name with a range quantity.
         */
        public Builder addInvItem(String itemName, int min, int max) {
            this.invRequirements.add(ItemRequirement.range(itemName, min, max));
            return this;
        }

        /**
         * Adds an inventory item requirement by name with a fill quantity.
         */
        public Builder addInvItemFill(String itemName, int target) {
            this.invRequirements.add(ItemRequirement.fill(itemName, target));
            return this;
        }

        /**
         * Adds an inventory item requirement by name with a {@link BankAmount.Amount} quantity spec.
         */
        public Builder addInvItem(String itemName, BankAmount.Amount amount) {
            this.invRequirements.add(ItemRequirement.of(itemName, amount));
            return this;
        }

        /**
         * Adds an inventory item requirement by filter with a {@link BankAmount.Amount} quantity spec.
         */
        public Builder addInvItem(Predicate<Rs2ItemModel> filter, BankAmount.Amount amount) {
            this.invRequirements.add(ItemRequirement.of(filter, amount));
            return this;
        }

        /**
         * Adds an inventory item requirement by filter with an exact quantity.
         */
        public Builder addInvItem(Predicate<Rs2ItemModel> filter, int amount) {
            this.invRequirements.add(ItemRequirement.of(filter, amount));
            return this;
        }

        /**
         * Adds an inventory item requirement by filter with a range quantity.
         */
        public Builder addInvItem(Predicate<Rs2ItemModel> filter, int min, int max) {
            this.invRequirements.add(ItemRequirement.range(filter, min, max));
            return this;
        }

        /**
         * Adds an inventory item requirement by filter with a fill quantity.
         */
        public Builder addInvItemFill(Predicate<Rs2ItemModel> filter, int target) {
            this.invRequirements.add(ItemRequirement.fill(filter, target));
            return this;
        }

        /**
         * Adds all requirements from another BankTask.
         */
        public Builder addAll(BankTask other) {
            this.equipmentRequirements.addAll(other.equipmentRequirements);
            this.invRequirements.addAll(other.invRequirements);
            return this;
        }

        /**
         * Adds a consumer callback for logging/debugging each processed requirement.
         */
        public Builder onRequirement(Consumer<Object> callback) {
            return this;
        }

        /**
         * Builds the immutable {@link BankTask}.
         *
         * @return new BankTask instance
         * @throws IllegalStateException if the same item ID appears in both
         *                               equipment and inventory requirements
         */
        public BankTask build() {
            detectConflicts();
            Collections.shuffle(this.invRequirements);
            Collections.shuffle(this.equipmentRequirements);
            return new BankTask(this);
        }

        /**
         * Detects the same item appearing in both an equipment requirement and an inventory requirement.
         *
         * @throws IllegalStateException if a conflict is detected
         */
        private void detectConflicts() {
            Set<Integer> equipIds = equipmentRequirements.stream()
                    .filter(req -> req.getItemId() != -1)
                    .map(EquipmentReq::getItemId)
                    .collect(Collectors.toSet());

            invRequirements.stream()
                    .filter(req -> equipIds.contains(req.getItemId()))
                    .findFirst()
                    .ifPresent(req -> {
                        throw new IllegalStateException(
                                "Conflict: item " + req.getItemName() + " [ID:" + req.getItemId()
                                        + "] is required in both an equipment slot and an inventory requirement. "
                                        + "The same item cannot appear in both categories."
                        );
                    });
        }
    }

    // ---------- Tri-style static entry-point: BankTask.EquipmentReq(slot) ----------

    /**
     * Static factory entry-point matching Tribot's {@code EquipmentReq.slot()} syntax.
     *
     * @param slot the equipment slot
     * @return SlotBuilder for chaining
     * @see EquipmentReq#slot(EquipmentInventorySlot)
     */
    public static EquipmentReq.SlotBuilder EquipmentReq(EquipmentInventorySlot slot) {
        return EquipmentReq.slot(slot);
    }
}

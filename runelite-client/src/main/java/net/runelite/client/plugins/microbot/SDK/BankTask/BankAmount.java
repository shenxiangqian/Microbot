package net.runelite.client.plugins.microbot.SDK.BankTask;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Specifies the required quantity for a bank item requirement, with factory
 * methods that mirror Tribot's {@code Amount} API.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * BankAmount.of(100)            // exactly 100  -> Amount(EXACT,    100, 100)
 * BankAmount.range(50, 200)     // 50-200       -> Amount(RANGE,     50, 200)
 * BankAmount.fill(6000)        // fill up to   -> Amount(FILL,   6000, 6000)
 * BankAmount.fillButOne(100)    // fill but 1   -> Amount(FILL_BUT_ONE, 99, 99)
 * }</pre>
 *
 * <p>For inventory requirements use {@link BankAmount#of(int)} / {@link BankAmount#range(int, int)}
 * / {@link BankAmount#fill(int)}. For equipment requirements use
 * {@link BankAmount#of(int)} (amount is always 1 for equipment).</p>
 *
 * @see ItemRequirement
 * @see EquipmentReq
 */
public enum BankAmount {

    /**
     * Exactly N items -- inventory must hold exactly that many.
     */
    EXACT {
        @Override
        public boolean isSatisfied(int current, int target) {
            return current == target;
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int target) {
            int needed = target - invCount;
            if (needed <= 0) return 0;
            return Math.min(needed, bankCount);
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int targetMin, int targetMax) {
            return withdrawAmount(invCount, bankCount, targetMin);
        }

        @Override
        public int depositAmount(int invCount, int target) {
            return Math.max(0, invCount - target);
        }
    },

    /**
     * A range of items: at least min, up to max.
     *
     * <p>Withdraws a random amount between the minimum needed and the maximum
     * available (up to targetMax). This provides more natural, human-like banking
     * behaviour rather than always withdrawing the bare minimum.</p>
     *
     * <pre>{@code
     * BankAmount.range(1000, 2000)  // withdraw 1000~2000 at a time
     * }</pre>
     */
    RANGE {
        @Override
        public boolean isSatisfied(int current, int target) {
            return current >= target;
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int target, int targetMax) {
            int needMin = target - invCount;
            if (needMin <= 0) return 0;
            int available = Math.min(targetMax - invCount, bankCount);
            if (available <= needMin) return needMin;
            return Rs2Random.between(needMin, available);
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int target) {
            int needMin = target - invCount;
            if (needMin <= 0) return 0;
            return Math.min(needMin, bankCount);
        }

        @Override
        public int depositAmount(int invCount, int target) {
            return Math.max(0, invCount - target);
        }
    },

    /**
     * Fill up to N items. The target is the maximum; withdraw however many are
     * needed to reach it, capped at what's available in the bank.
     */
    FILL {
        @Override
        public boolean isSatisfied(int current, int target) {
            return current >= target;
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int target) {
            int needed = target - invCount;
            if (needed <= 0) return 0;
            return Math.min(needed, bankCount);
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int targetMin, int targetMax) {
            return withdrawAmount(invCount, bankCount, targetMin);
        }

        @Override
        public int depositAmount(int invCount, int target) {
            return Math.max(0, invCount - target);
        }
    },

    /**
     * Fill but leave one in the bank. For inventory use this is identical to
     * {@link #FILL}. The target represents the count that should remain in the
     * bank after withdrawal (i.e., withdraw {@code bankCount - target}).
     */
    FILL_BUT_ONE {
        @Override
        public boolean isSatisfied(int current, int target) {
            return current >= target;
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int target) {
            int needed = bankCount - target;
            if (needed <= 0) return 0;
            return Math.min(needed, bankCount);
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int targetMin, int targetMax) {
            return withdrawAmount(invCount, bankCount, targetMin);
        }

        @Override
        public int depositAmount(int invCount, int target) {
            return Math.max(0, invCount - target);
        }
    },

    /**
     * No requirement -- used when a factory method receives 0, meaning the
     * condition is already satisfied and no withdrawal is needed.
     */
    NONE {
        @Override
        public boolean isSatisfied(int current, int target) {
            return true;
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int target) {
            return 0;
        }

        @Override
        public int withdrawAmount(int invCount, int bankCount, int targetMin, int targetMax) {
            return 0;
        }

        @Override
        public int depositAmount(int invCount, int target) {
            return 0;
        }
    };

    /**
     * Whether the current count satisfies this quantity requirement.
     *
     * @param current current item count (inventory or equipment)
     * @param target target amount from the requirement
     * @return true if satisfied
     */
    public abstract boolean isSatisfied(int current, int target);

    /**
     * How many items to withdraw from the bank to satisfy a withdrawal requirement.
     *
     * @param invCount  current inventory count
     * @param bankCount available in bank
     * @param target    target amount
     * @return items to withdraw, 0 if satisfied, never negative
     */
    public abstract int withdrawAmount(int invCount, int bankCount, int target);

    /**
     * How many items to withdraw from the bank, respecting a maximum bound.
     *
     * @param invCount  current inventory count
     * @param bankCount available in bank
     * @param targetMin target minimum amount
     * @param targetMax target maximum amount (informational / upper bound)
     * @return items to withdraw, 0 if satisfied, never negative
     */
    public abstract int withdrawAmount(int invCount, int bankCount, int targetMin, int targetMax);

    /**
     * How many items to deposit back to the bank to satisfy a deposit requirement.
     *
     * @param invCount current inventory count
     * @param target  target inventory count after deposit
     * @return items to deposit, 0 if already satisfied
     */
    public abstract int depositAmount(int invCount, int target);

    // ---------- factory methods (mirror Tribot's Amount.of / Amount.range / Amount.fill / Amount.fillButOne) ----------

    /**
     * Exactly N items. The target is N for both min and max.
     *
     * <pre>{@code
     * BankAmount.of(100)          // exactly 100
     * }</pre>
     *
     * @param amount target quantity
     * @return Amount wrapper holding the EXACT mode and value
     */
    public static Amount of(int amount) {
        if (amount == 0) return new Amount(NONE, 0, 0);
        return new Amount(EXACT, amount, amount);
    }

    /**
     * A range of items: at least min, no upper limit enforced.
     *
     * <pre>{@code
     * BankAmount.range(100, 200)  // at least 100 (max is informational only)
     * }</pre>
     *
     * @param min minimum quantity (treated as the target)
     * @param max maximum quantity (informational)
     * @return Amount wrapper holding the RANGE mode and values
     */
    public static Amount range(int min, int max) {
        if (min == 0) return new Amount(NONE, 0, 0);
        return new Amount(RANGE, min, max);
    }

    /**
     * Fill up to N items: withdraw however many are needed to reach N.
     *
     * <pre>{@code
     * BankAmount.fill(6000)       // top up to 6000
     * }</pre>
     *
     * @param amount fill target
     * @return Amount wrapper holding the FILL mode and value
     */
    public static Amount fill(int amount) {
        return new Amount(FILL, amount, amount);
    }

    /**
     * Fill but leave one in the bank.
     *
     * <pre>{@code
     * BankAmount.fillButOne(100) // leave 1 in bank (withdraw all but 1)
     * }</pre>
     *
     * @param amount slot total after withdrawal (leaves 1 in bank)
     * @return Amount wrapper holding the FILL_BUT_ONE mode and value
     */
    public static Amount fillButOne(int amount) {
        return new Amount(FILL_BUT_ONE, amount, amount);
    }

    /**
     * An immutable holder for a quantity specification, combining the
     * {@link BankAmount} mode with its target values.
     *
     * <p>Mirrors Tribot's {@code Amount} class.</p>
     */
    @Getter
    public static final class Amount {
        private final BankAmount mode;
        private final int targetMin;
        private final int targetMax;

        private Amount(BankAmount mode, int targetMin, int targetMax) {
            this.mode = mode;
            this.targetMin = targetMin;
            this.targetMax = targetMax;
        }

        /** @return true if the inventory currently satisfies this amount spec */
        public boolean isSatisfiedInInventory(int itemId) {
            int current = Rs2Inventory.count(itemId);
            return mode.isSatisfied(current, targetMin);
        }

        /** @return true if the bank currently has enough to satisfy this amount spec */
        public boolean hasItemsInBank(int itemId) {
            return Rs2Bank.count(itemId) >= targetMin;
        }

        /**
         * How many items to withdraw from bank.
         *
         * @param invCount  current inventory count
         * @param bankCount available in bank
         * @return items to withdraw, 0 if satisfied
         */
        public int withdrawAmount(int invCount, int bankCount) {
            return mode.withdrawAmount(invCount, bankCount, targetMin);
        }

        /**
         * How many items to withdraw from bank, respecting the target maximum.
         *
         * @param invCount  current inventory count
         * @param bankCount available in bank
         * @param targetMax target maximum (used by RANGE mode for randomised withdrawal)
         * @return items to withdraw, 0 if satisfied
         */
        public int withdrawAmount(int invCount, int bankCount, int targetMax) {
            return mode.withdrawAmount(invCount, bankCount, targetMin, targetMax);
        }

        /**
         * How many items to deposit back to bank.
         *
         * @param invCount current inventory count
         * @return items to deposit, 0 if satisfied
         */
        public int depositAmount(int invCount) {
            return mode.depositAmount(invCount, targetMin);
        }

        @Override
        public String toString() {
            switch (mode) {
                case EXACT:        return "exactly " + targetMin;
                case RANGE:        return targetMin + "-" + targetMax;
                case FILL:         return "fill up to " + targetMin;
                case FILL_BUT_ONE: return "fill " + targetMin + " (leave 1 in bank)";
                case NONE:         return "none (skip)";
                default:           return String.valueOf(targetMin);
            }
        }
    }
}

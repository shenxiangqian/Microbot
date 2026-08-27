package net.runelite.client.plugins.microbot.SDK.BankTask;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Collections;
import java.util.List;

/**
 * The result of a {@link BankTask#execute()} operation.
 *
 * <p>Provides the final outcome (success, partial, or failure) along with
 * details about which requirements were not met and why.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * BankExecuteResult result = task.execute();
 * if (result.isSuccess()) {
 *     Microbot.log("Bank task completed successfully.");
 * } else if (result.isPartial()) {
 *     Microbot.log("Partially completed. Missing: " + result.getUnsatisfiedRequirements());
 * } else {
 *     Microbot.log("Failed: " + result.getStatus() + " -- " + result.getMessage());
 * }
 * }</pre>
 *
 * @see BankTask
 */
@Getter
public class BankExecuteResult {

    // ---------- factory methods ----------

    /**
     * Creates a successful result.
     *
     * @param task the completed task
     * @return success result
     */
    public static BankExecuteResult success(BankTask task) {
        return new BankExecuteResult(Status.SUCCESS, task, null, Collections.emptyList());
    }

    /**
     * Creates a result indicating the bank lacks required items.
     *
     * @param task the failed task
     * @return missing items result
     */
    public static BankExecuteResult missingItems(BankTask task) {
        List<String> missing = task.getUnsatisfiedRequirements();
        Microbot.log("[BankTask] Missing items in bank: " + missing);
        return new BankExecuteResult(Status.MISSING_ITEMS, task, "Bank lacks required items: " + missing, missing);
    }

    /**
     * Creates a result for an unexpected failure.
     *
     * @param task   the task that failed
     * @param status the failure status code
     * @param reason human-readable reason
     * @return failure result
     */
    public static BankExecuteResult failure(BankTask task, Status status, String reason) {
        Microbot.log("[BankTask] Failed (" + status + "): " + reason);
        return new BankExecuteResult(status, task, reason, Collections.emptyList());
    }

    /**
     * Creates a result for a partially completed task.
     *
     * @param task the task
     * @param unsatisfied list of unsatisfied requirement descriptions
     * @return partial result
     */
    public static BankExecuteResult partial(BankTask task, List<String> unsatisfied) {
        Microbot.log("[BankTask] Partial completion. Unsatisfied: " + unsatisfied);
        return new BankExecuteResult(Status.PARTIAL, task, "Partially satisfied", unsatisfied);
    }

    // ---------- status codes ----------

    /**
     * Execution status codes.
     */
    public enum Status {
        /** All requirements were satisfied. */
        SUCCESS,
        /** Some requirements were not satisfied. */
        PARTIAL,
        /** Bank lacks the items needed to fulfill requirements. */
        MISSING_ITEMS,
        /** Could not walk to a bank. */
        WALK_FAILED,
        /** Could not open the bank interface. */
        OPEN_FAILED,
        /** Could not close the bank interface. */
        CLOSE_FAILED,
        /** Could not withdraw an item from the bank. */
        WITHDRAW_FAILED,
        /** Withdrawal completed but inventory still does not satisfy the requirement. */
        WITHDRAW_INCOMPLETE,
        /** Could not deposit an item to the bank. */
        DEPOSIT_FAILED,
        /** Could not equip an item. */
        EQUIP_FAILED,
        /** Could not unequip an item. */
        UNEQUIP_FAILED,
        /** Bank was closed unexpectedly during execution. */
        BANK_CLOSED,
    }

    // ---------- fields ----------

    /**
     * -- GETTER --
     *
     * @return the execution status code
     */
    private final Status status;
    /**
     * -- GETTER --
     *
     * @return the task that was executed
     */
    private final BankTask task;
    /**
     * -- GETTER --
     *
     * @return human-readable message describing the result
     */
    private final String message;
    /**
     * -- GETTER --
     *
     * @return list of requirement descriptions that were not satisfied
     */
    private final List<String> unsatisfiedRequirements;

    private BankExecuteResult(Status status, BankTask task, String message, List<String> unsatisfiedRequirements) {
        this.status = status;
        this.task = task;
        this.message = message;
        this.unsatisfiedRequirements = List.copyOf(unsatisfiedRequirements);
    }

    // ---------- query methods ----------

    /**
     * @return true if all requirements were fully satisfied
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * @return true if execution completed but some requirements were not satisfied
     */
    public boolean isPartial() {
        return status == Status.PARTIAL;
    }

    /**
     * @return true if execution failed (including missing items)
     */
    public boolean isFailure() {
        return status != Status.SUCCESS && status != Status.PARTIAL;
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "[BankTask] SUCCESS";
        }
        if (isPartial()) {
            return "[BankTask] PARTIAL: " + message + " -- unsatisfied: " + unsatisfiedRequirements;
        }
        return "[BankTask] " + status + ": " + message;
    }
}

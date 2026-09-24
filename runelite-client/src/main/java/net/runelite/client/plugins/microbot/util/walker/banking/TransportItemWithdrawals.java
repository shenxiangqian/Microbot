package net.runelite.client.plugins.microbot.util.walker.banking;

import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import java.util.EnumMap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Plans against exact item IDs: a different charge variant is a different withdrawable bank row. */
public final class TransportItemWithdrawals {
    private TransportItemWithdrawals() { }

    public static Optional<Map<Integer, Integer>> teleports(List<Transport> transports,
            Map<Integer, Integer> bank, Map<Integer, Integer> inventory, Set<Integer> equipment) {
        Map<Integer, Integer> carried = new HashMap<>(inventory);
        Map<Integer, Integer> withdrawals = new LinkedHashMap<>();
        for (Transport transport : transports) {
            if (transport.getType() != TransportType.TELEPORTATION_ITEM) {
                continue;
            }
            List<Integer> alternatives = transport.getItemIdRequirements().stream()
                    .flatMap(Set::stream).distinct().sorted().collect(Collectors.toList());
            if (alternatives.isEmpty()) {
                continue;
            }
            Integer selected = alternatives.stream()
                    .filter(id -> carried.getOrDefault(id, 0) > 0 || equipment.contains(id))
                    .findFirst().orElse(null);
            if (selected == null) {
                selected = alternatives.stream()
                        .filter(id -> bank.getOrDefault(id, 0) > withdrawals.getOrDefault(id, 0))
                        .findFirst().orElse(null);
                if (selected == null) {
                    return Optional.empty();
                }
                withdrawals.merge(selected, 1, Integer::sum);
                carried.merge(selected, 1, Integer::sum);
            }
            if (transport.isConsumable() && !equipment.contains(selected)) {
                carried.computeIfPresent(selected, (id, count) -> count - 1);
            }
        }
        return Optional.of(withdrawals);
    }

    /** Resolve missing base runes to real bank stacks, including combination runes. */
    public static Optional<Map<Integer, Integer>> addRunes(Map<Integer, Integer> items,
            Map<Runes, Integer> missing, Map<Integer, Integer> bank) {
        Map<Integer, Integer> withdrawals = new LinkedHashMap<>(items);
        Map<Runes, Integer> remaining = new EnumMap<>(Runes.class);
        remaining.putAll(missing);
        // Prefer ordinary runes; a combination stack can then cover multiple remaining elements at once.
        for (Runes rune : Runes.values()) {
            if (rune.getBaseRunes().length == 0) allocateRuneStack(rune, remaining, bank, withdrawals);
        }
        for (Runes rune : Runes.values()) {
            if (rune.getBaseRunes().length > 0) allocateRuneStack(rune, remaining, bank, withdrawals);
        }
        return remaining.values().stream().anyMatch(quantity -> quantity > 0)
                ? Optional.empty() : Optional.of(withdrawals);
    }

    private static void allocateRuneStack(Runes stack, Map<Runes, Integer> remaining,
            Map<Integer, Integer> bank, Map<Integer, Integer> withdrawals) {
        int required = remaining.entrySet().stream().filter(entry -> stack.providesRune(entry.getKey()))
                .mapToInt(Map.Entry::getValue).max().orElse(0);
        int available = Math.max(0, bank.getOrDefault(stack.getItemId(), 0)
                - withdrawals.getOrDefault(stack.getItemId(), 0));
        int amount = Math.min(required, available);
        if (amount <= 0) return;
        withdrawals.merge(stack.getItemId(), amount, Integer::sum);
        remaining.replaceAll((rune, quantity) -> stack.providesRune(rune) ? Math.max(0, quantity - amount) : quantity);
    }

    public static boolean availableAndFits(Map<Integer, Integer> withdrawals, Map<Integer, Integer> bank,
            Map<Integer, Integer> inventory, Set<Integer> stackable, int emptySlots) {
        long slots = 0;
        for (Map.Entry<Integer, Integer> entry : withdrawals.entrySet()) {
            int id = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0 || bank.getOrDefault(id, 0) < amount) {
                return false;
            }
            slots += stackable.contains(id) ? (inventory.getOrDefault(id, 0) > 0 ? 0 : 1) : amount;
        }
        return slots <= emptySlots;
    }
}

package net.runelite.client.plugins.microbot.walkerfst;

/** Pure decision used after the walker gives a transient logged-out sample a short grace window. */
final class LoginStabilityPolicyFst {
    private LoginStabilityPolicyFst() {
    }

    static boolean shouldExit(boolean initiallyLoggedIn,
                              boolean loggedInAfterGrace,
                              boolean walkCancelled) {
        return !walkCancelled && !initiallyLoggedIn && !loggedInAfterGrace;
    }
}

package net.runelite.client.plugins.microbot.util.security;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles direct session-based login using session_id and character_id.
 * Used when launching client with --session-id and --character-id parameters.
 */
@Slf4j
public final class DirectSessionLogin {

    private static String sessionId = null;
    private static String characterId = null;
    private static final AtomicBoolean loginAttempted = new AtomicBoolean(false);

    private DirectSessionLogin() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Sets session credentials from command line arguments.
     * Must be called before client initialization.
     */
    public static void setSessionCredentials(String sessionIdArg, String characterIdArg) {
        sessionId = sessionIdArg;
        characterId = characterIdArg;
        log.info("Direct session login credentials configured");
    }

    /**
     * Returns true if session credentials have been provided via command line.
     */
    public static boolean hasSessionCredentials() {
        return sessionId != null && characterId != null;
    }

    /**
     * Attempts to perform a direct session login.
     * This should be called after client is initialized and on LOGIN_SCREEN state.
     *
     * @return true if login was attempted successfully
     */
    public static boolean attemptLogin() {
        if (!hasSessionCredentials()) {
            log.debug("No session credentials available for direct login");
            return false;
        }

        if (loginAttempted.getAndSet(true)) {
            log.debug("Direct session login already attempted");
            return false;
        }

        Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Cannot perform direct session login - client not initialized");
            return false;
        }

        GameState state = client.getGameState();
        if (state != GameState.LOGIN_SCREEN && state != GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            log.debug("Cannot perform direct session login - not on login screen (state: {})", state);
            return false;
        }

        try {
            log.info("Attempting direct session login with session_id: {}, character_id: {}",
                maskSessionId(sessionId), characterId);

            // Use reflection to call Client.setAccountHash method
            // This method is provided by the injected-client for setting session directly
            boolean success = setAccountHashViaReflection(client, sessionId, characterId);

            if (success) {
                log.info("Direct session login initiated successfully");
                return true;
            } else {
                log.error("Failed to set account hash via reflection");
                return false;
            }
        } catch (Exception e) {
            log.error("Error during direct session login", e);
            return false;
        }
    }

    /**
     * Uses reflection to call Client.setAccountHash() method.
     * This method is provided by the injected-client and directly sets the session.
     */
    private static boolean setAccountHashViaReflection(Client client, String sessionId, String characterId) {
        try {
            // Method 1: Try to call setAccountHash(String sessionId)
            var method = client.getClass().getMethod("setAccountHash", String.class);
            method.invoke(client, sessionId);

            // Method 2: If character ID is needed, may require additional call
            // var setCharMethod = client.getClass().getMethod("setCharacterId", String.class);
            // setCharMethod.invoke(client, characterId);

            log.debug("Successfully invoked setAccountHash with session_id");
            return true;
        } catch (NoSuchMethodException e) {
            log.error("setAccountHash method not found on Client - injected-client may not support this", e);
            return false;
        } catch (Exception e) {
            log.error("Failed to invoke setAccountHash via reflection", e);
            return false;
        }
    }

    /**
     * Masks session ID for safe logging (shows first 8 chars only).
     */
    private static String maskSessionId(@Nullable String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 8) + "***";
    }

    /**
     * Clears stored session credentials.
     */
    public static void clearCredentials() {
        sessionId = null;
        characterId = null;
        loginAttempted.set(false);
    }
}

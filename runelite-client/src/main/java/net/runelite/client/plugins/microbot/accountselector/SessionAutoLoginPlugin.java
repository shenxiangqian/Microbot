package net.runelite.client.plugins.microbot.accountselector;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.DirectSessionLogin;
import net.runelite.client.plugins.microbot.util.security.LoginManager;

import javax.inject.Inject;

/**
 * Hidden, always-on plugin that automatically attempts session-based login
 * when --session-id and --character-id command line parameters are provided.
 */
@PluginDescriptor(
    name = "Session Auto Login",
    description = "Automatically logs in using session-id and character-id from command line",
    tags = {"microbot", "login", "session"},
    enabledByDefault = true,
    hidden = true
)
@Slf4j
public class SessionAutoLoginPlugin extends Plugin {

    @Inject
    private Client client;

    private boolean loginAttempted = false;

    @Override
    protected void startUp() {
        log.debug("SessionAutoLoginPlugin started");
        loginAttempted = false;
    }

    @Override
    protected void shutDown() {
        log.debug("SessionAutoLoginPlugin stopped");
        loginAttempted = false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (loginAttempted) {
            return;
        }

        if (!DirectSessionLogin.hasSessionCredentials()) {
            // No session credentials provided, do nothing
            return;
        }

        GameState newState = event.getGameState();

        // Attempt login when we reach the login screen
        if (newState == GameState.LOGIN_SCREEN || newState == GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            log.info("Login screen detected, attempting direct session login");

            // Small delay to ensure client is ready
            Microbot.getClientThread().invokeLater(() -> {
                try {
                    Thread.sleep(500);
                    boolean success = LoginManager.attemptDirectSessionLogin();
                    if (success) {
                        log.info("Direct session login initiated");
                        loginAttempted = true;
                    } else {
                        log.warn("Direct session login failed");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted during session login delay", e);
                }
            });
        } else if (newState == GameState.LOGGED_IN) {
            log.info("Successfully logged in via session");
            loginAttempted = true;
            LoginManager.markLoggedIn();
        }
    }

    @Provides
    AutoLoginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoLoginConfig.class);
    }
}

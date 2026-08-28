package net.runelite.client.plugins.microbot.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.microbot.InputSelector;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager.SideLoadedScript;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager.SideLoadedScriptOperationResult;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager.SideLoadedScriptReference;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class ScriptToolbarController {
    private static final BufferedImage START_ICON = ImageUtil.loadImageResource(
            ScriptToolbarController.class,
            "/util/ks.png");
    private static final BufferedImage STOP_ICON = ImageUtil.loadImageResource(
            ScriptToolbarController.class,
            "/util/tz.png");
    private static final BufferedImage RESTART_ICON = ImageUtil.loadImageResource(
            ScriptToolbarController.class,
            "/util/cq.png");

    private final ClientToolbar clientToolbar;
    private final EventBus eventBus;
    private final MicrobotPluginManager microbotPluginManager;
    private final InputSelector inputSelector;
    private final Provider<MicrobotTopLevelConfigPanel> topLevelConfigPanelProvider;

    private final NavigationButton startButton = NavigationButton.builder()
            .tab(false)
            .priority(90)
            .icon(START_ICON)
            .tooltip("Start external script")
            .onClick(this::showScriptSelection)
            .build();
    private final NavigationButton stopButton = NavigationButton.builder()
            .tab(false)
            .priority(91)
            .icon(STOP_ICON)
            .tooltip("Stop external script")
            .onClick(this::stopScripts)
            .build();
    private final NavigationButton restartButton = NavigationButton.builder()
            .tab(false)
            .priority(90)
            .icon(RESTART_ICON)
            .tooltip("Restart last external script")
            .onClick(this::restartScript)
            .build();
    private final NavigationButton busyButton = NavigationButton.builder()
            .tab(false)
            .priority(90)
            .icon(RESTART_ICON)
            .tooltip("External script operation in progress")
            .build();

    private NavigationButton configurationNavigationButton;
    private ToolbarState toolbarState;
    private boolean started;
    private boolean operationInProgress;

    @Inject
    private ScriptToolbarController(
            ClientToolbar clientToolbar,
            EventBus eventBus,
            MicrobotPluginManager microbotPluginManager,
            InputSelector inputSelector,
            Provider<MicrobotTopLevelConfigPanel> topLevelConfigPanelProvider) {
        this.clientToolbar = clientToolbar;
        this.eventBus = eventBus;
        this.microbotPluginManager = microbotPluginManager;
        this.inputSelector = inputSelector;
        this.topLevelConfigPanelProvider = topLevelConfigPanelProvider;
    }

    public void startUp(NavigationButton configurationNavigationButton) {
        if (started) {
            return;
        }
        this.configurationNavigationButton = configurationNavigationButton;
        started = true;
        eventBus.register(this);
        inputSelector.startUp();
        syncInputControllerState();
        refreshToolbar();
    }

    public void shutDown() {
        if (!started) {
            return;
        }
        started = false;
        eventBus.unregister(this);
        removeAllButtons();
        inputSelector.shutDown();
        toolbarState = null;
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (microbotPluginManager.isManagedSideLoadedPlugin(event.getPlugin())) {
            syncInputControllerState();
            refreshToolbar();
        }
    }

    @Subscribe
    public void onExternalPluginsChanged(ExternalPluginsChanged event) {
        syncInputControllerState();
        refreshToolbar();
    }

    private void showScriptSelection() {
        if (operationInProgress) {
            return;
        }
        ScriptSelectionDialog.Selection selection = ScriptSelectionDialog.showDialog(
                activeWindow(), microbotPluginManager);
        if (selection == null) {
            return;
        }
        SideLoadedScript script = selection.getScript();
        if (selection.getAction() == ScriptSelectionDialog.Action.CONFIGURE) {
            openConfiguration(script);
            return;
        }
        beginOperation(microbotPluginManager.startSideLoadedScript(
                script.getInternalName(), script.getClassName()));
    }

    private void stopScripts() {
        if (!operationInProgress) {
            beginOperation(microbotPluginManager.stopActiveSideLoadedScripts());
        }
    }

    private void restartScript() {
        if (operationInProgress) {
            return;
        }
        SideLoadedScriptReference script = microbotPluginManager.getLastStartedSideLoadedScript().orElse(null);
        if (script == null) {
            showOperationError("No external script has been started during this client session.");
            refreshToolbar();
            return;
        }
        beginOperation(microbotPluginManager.restartSideLoadedScript(
                script.getInternalName(), script.getClassName()));
    }

    private void openConfiguration(SideLoadedScript script) {
        if (configurationNavigationButton == null || script.getPlugin() == null) {
            showOperationError("The selected script configuration is unavailable.");
            return;
        }
        clientToolbar.openPanel(configurationNavigationButton);
        topLevelConfigPanelProvider.get().openConfigurationPanel(script.getPlugin());
    }

    private void beginOperation(CompletableFuture<SideLoadedScriptOperationResult> operation) {
        operationInProgress = true;
        refreshToolbar();
        operation.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            operationInProgress = false;
            if (error != null) {
                log.warn("External script operation failed", error);
                showOperationError("The external script operation failed. Check the client log for details.");
            } else if (!result.isSuccessful()) {
                showOperationError(result.getMessage());
            }
            syncInputControllerState();
            refreshToolbar();
        }));
    }

    private void syncInputControllerState() {
        int activeCount = microbotPluginManager.getActiveSideLoadedScripts().size();
        inputSelector.setInputEnabled(determineInputEnabled(activeCount));
    }

    static boolean determineInputEnabled(int activeCount) {
        return activeCount == 0;
    }

    private void refreshToolbar() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshToolbar);
            return;
        }
        if (!started) {
            return;
        }

        int activeCount = microbotPluginManager.getActiveSideLoadedScripts().size();
        boolean hasLastStartedScript = microbotPluginManager.getLastStartedSideLoadedScript().isPresent();
        ToolbarState nextState = determineToolbarState(
                operationInProgress, activeCount, hasLastStartedScript);
        if (nextState == toolbarState) {
            return;
        }

        removeAllButtons();
        toolbarState = nextState;
        switch (nextState) {
            case IDLE:
                clientToolbar.addNavigation(startButton);
                break;
            case IDLE_WITH_RESTART:
                clientToolbar.addNavigation(startButton);
                clientToolbar.addNavigation(restartButton);
                break;
            case RUNNING:
                clientToolbar.addNavigation(restartButton);
                clientToolbar.addNavigation(stopButton);
                break;
            case MULTIPLE_RUNNING:
                clientToolbar.addNavigation(restartButton);
                clientToolbar.addNavigation(stopButton);
                break;
            case BUSY:
                clientToolbar.addNavigation(busyButton);
                break;
            default:
                throw new IllegalStateException("Unknown toolbar state: " + nextState);
        }
    }

    static ToolbarState determineToolbarState(
            boolean operationInProgress,
            int activeCount,
            boolean hasLastStartedScript) {
        if (operationInProgress) {
            return ToolbarState.BUSY;
        }
        if (activeCount == 0) {
            return hasLastStartedScript ? ToolbarState.IDLE_WITH_RESTART : ToolbarState.IDLE;
        }
        return activeCount == 1 ? ToolbarState.RUNNING : ToolbarState.MULTIPLE_RUNNING;
    }

    private void removeAllButtons() {
        clientToolbar.removeNavigation(startButton);
        clientToolbar.removeNavigation(stopButton);
        clientToolbar.removeNavigation(restartButton);
        clientToolbar.removeNavigation(busyButton);
    }

    private static Window activeWindow() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    private static void showOperationError(String message) {
        JOptionPane.showMessageDialog(
                activeWindow(),
                message,
                "External Script Operation Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    enum ToolbarState {
        IDLE,
        IDLE_WITH_RESTART,
        RUNNING,
        MULTIPLE_RUNNING,
        BUSY
    }
}

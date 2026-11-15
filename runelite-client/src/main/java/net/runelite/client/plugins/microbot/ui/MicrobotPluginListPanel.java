/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.html.HtmlEscapers;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginNames;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginSearch;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.ContainableFrame;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class MicrobotPluginListPanel extends MicrobotPluginPanel {
    private static final long BLOCKED_NOTICE_WAIT_MS = 300_000;
    static final int LIST_VIEW_WIDTH = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH;
    static final int LIST_ITEM_WIDTH = LIST_VIEW_WIDTH - 20;

    static final String RUNELITE_GROUP_NAME = MicrobotConfig.class.getAnnotation(ConfigGroup.class).value();
    private final Set<String> updating = new HashSet<>();
    private static final String PINNED_PLUGINS_CONFIG_KEY = "pinnedPlugins";
    private static final ImageIcon REFRESH_ICON;
    private static final ImmutableList<String> CATEGORY_TAGS = ImmutableList.of(
            "Combat",
            "Chat",
            "Item",
            "Minigame",
            "Notification",
            "Plugin Hub",
            "Skilling",
            "XP"
    );

    static {
        BufferedImage refreshIcon = ImageUtil.loadImageResource(
                MicrobotPluginListPanel.class,
                "/net/runelite/client/plugins/microbot/inventorysetups/update_icon.png");
        REFRESH_ICON = new ImageIcon(refreshIcon);
    }

    private static final int TOP_BUTTON_SIZE = 30;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final MicrobotPluginManager microbotPluginManager;
    private final Provider<MicrobotConfigPanel> configPanelProvider;
    @Getter
    private final MicrobotBugReporter bugReporter;
    private final List<MicrobotPluginConfigurationDescriptor> fakePlugins = new ArrayList<>();

    private final Provider<LocalPluginsPanel> localPluginsPanelProvider;

    @Getter
    private final ExternalPluginManager externalPluginManager;

    @Getter
    private final MicrobotMultiplexingPluginPanel muxer;

    private final IconTextField searchBar;
    private final JButton refreshScriptsButton;
    private final JScrollPane scrollPane;
    private final MicrobotFixedWidthPanel mainPanel;
    private final Map<Component, Boolean> refreshEnabledStates = new IdentityHashMap<>();
    private List<MicrobotPluginListItem> pluginList;

    @Setter
    private Consumer<String> openHub = query -> { };

    @Inject
    public MicrobotPluginListPanel(
            ConfigManager configManager,
            PluginManager pluginManager,
            MicrobotPluginManager microbotPluginManager,
            ExternalPluginManager externalPluginManager,
            EventBus eventBus,
            Provider<MicrobotConfigPanel> configPanelProvider,
            Provider<LocalPluginsPanel> localPluginsPanelProvider,
            MicrobotBugReporter bugReporter) {
        super(false);

        this.bugReporter = bugReporter;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.externalPluginManager = externalPluginManager;
        this.configPanelProvider = configPanelProvider;
        this.microbotPluginManager = microbotPluginManager;
        this.localPluginsPanelProvider = null;

        muxer = new MicrobotMultiplexingPluginPanel(this) {
            @Override
            protected void onAdd(MicrobotPluginPanel p) {
                eventBus.register(p);
            }

            @Override
            protected void onRemove(MicrobotPluginPanel p) {
                eventBus.unregister(p);
            }
        };

        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 55, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearchBarChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearchBarChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearchBarChanged();
            }
        });
        CATEGORY_TAGS.forEach(searchBar.getSuggestionListModel()::addElement);

        refreshScriptsButton = new JButton(REFRESH_ICON);
        refreshScriptsButton.setPreferredSize(new Dimension(30, 30));
        refreshScriptsButton.setMinimumSize(new Dimension(30, 30));
        refreshScriptsButton.setMaximumSize(new Dimension(30, 30));
        refreshScriptsButton.setToolTipText("Reload script JARs");
        SwingUtil.removeButtonDecorations(refreshScriptsButton);
        refreshScriptsButton.addActionListener(event -> refreshSideLoadedPlugins());

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel();
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        topPanel.setLayout(new BorderLayout(0, BORDER_OFFSET));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchBar, BorderLayout.CENTER);
        searchPanel.add(refreshScriptsButton, BorderLayout.EAST);
        topPanel.add(searchPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        mainPanel = new MicrobotFixedWidthPanel(LIST_VIEW_WIDTH);
        mainPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
        mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel northPanel = new MicrobotFixedWidthPanel(LIST_VIEW_WIDTH);
        northPanel.setLayout(new BorderLayout());
        northPanel.add(mainPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(northPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void refreshSideLoadedPlugins() {
        refreshScriptsButton.setEnabled(false);
        refreshScriptsButton.setToolTipText("Reloading script JARs...");
        disablePluginControls(mainPanel);

        microbotPluginManager.refreshSideLoadedPlugins().whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> {
                    refreshScriptsButton.setEnabled(true);
                    restorePluginControls();
                    if (error != null) {
                        refreshScriptsButton.setToolTipText("Script refresh failed");
                        log.warn("Unable to refresh side-loaded plugins", error);
                        JOptionPane.showMessageDialog(
                                this,
                                "Unable to refresh script JARs. Check the client log for details.",
                                "Script Refresh Failed",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    refreshScriptsButton.setToolTipText(result.getSummary());
                    if (!result.isSuccessful()) {
                        String failures = result.getFailures().entrySet().stream()
                                .limit(8)
                                .map(entry -> entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n"));
                        JOptionPane.showMessageDialog(
                                this,
                                result.getSummary() + "\n\n" + failures,
                                "Script Refresh Completed With Errors",
                                JOptionPane.WARNING_MESSAGE);
                    }
                }));
    }

    private void disablePluginControls(Component component) {
        refreshEnabledStates.put(component, component.isEnabled());
        component.setEnabled(false);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                disablePluginControls(child);
            }
        }
    }

    private void restorePluginControls() {
        refreshEnabledStates.forEach(Component::setEnabled);
        refreshEnabledStates.clear();
    }

    // A fixed width makes the label report its wrapped height, so DynamicGridLayout does not clip it.
    private static String wrapped(String html) {
        return wrapped(html, LIST_ITEM_WIDTH - 50, "center");
    }

    private static String wrapped(String html, int width, String align) {
        return "<div style='width:" + width + "px;text-align:" + align + "'>" + html + "</div>";
    }

    /** An underlined, wrapping line of plain text, for the row status line and the health banner links. */
    static String linkHtml(String text) {
        return "<html>" + wrapped("<u>" + HtmlEscapers.htmlEscaper().escape(text) + "</u>", LIST_ITEM_WIDTH - 70, "left");
    }

    void uninstall(MicrobotPluginConfigurationDescriptor plugin) {
        String hubName = plugin.getMicrobotHubName();
        if (hubName == null) {
            externalPluginManager.remove(plugin.getInternalPluginHubName());
            return;
        }

        // Built by hand: the Hub manifest may not have loaded, and removal only needs the name.
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName(hubName);
        microbotPluginManager.removePlugin(manifest).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> showOperationResult("Couldn't uninstall plugin", result, error)));
    }

    private void showOperationResult(String failureTitle, LivePluginManager.OperationResult result, Throwable error) {
        if (error == null && result.isSuccess()) {
            if (result.isNeedsRestart()) {
                JOptionPane.showMessageDialog(this, result.getMessage(), "Restart needed", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        Throwable cause = error == null || error.getCause() == null ? error : error.getCause();
        String message = cause == null ? result.getMessage() : cause.getMessage();
        showError(failureTitle, message);
    }

    void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * A menu button: one radio item per sort, then {@code extra}. {@code name} says everything the menu holds, not
     * just "Sort". Tab reaches it; a click does not focus it.
     */
    static <S> JButton createSortButton(String name, JPopupMenu menu, Map<S, JMenuItem> items, S[] options, Function<S, String> label,
                                        S selected, Consumer<S> onSort, JMenuItem extra) {
        ButtonGroup group = new ButtonGroup();
        for (S option : options) {
            JMenuItem item = new JRadioButtonMenuItem(label.apply(option), option == selected);
            item.addActionListener(e -> onSort.accept(option));
            group.add(item);
            items.put(option, item);
            menu.add(item);
        }
        menu.addSeparator();
        menu.add(extra);

        JButton button = new JButton(createSortIcon());
        SwingUtil.removeButtonDecorations(button);
        button.setPreferredSize(new Dimension(TOP_BUTTON_SIZE, TOP_BUTTON_SIZE));
        button.setRequestFocusEnabled(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.addActionListener(e -> menu.show(button, button.getWidth() - menu.getPreferredSize().width, button.getHeight()));
        return button;
    }

    private static ImageIcon createSortIcon() {
        BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        graphics.fillPolygon(new int[]{0, 3, 7}, new int[]{5, 1, 5}, 3);
        graphics.fillRect(3, 4, 1, 9);
        graphics.fillPolygon(new int[]{7, 11, 14}, new int[]{9, 13, 9}, 3);
        graphics.fillRect(10, 1, 1, 9);
        graphics.dispose();
        return new ImageIcon(image);
    }

    void onStatusClicked(MicrobotPluginListItem row) {
        MicrobotPluginRowStatus status = row.getStatus();
        Plugin plugin = row.getPluginConfig().getPlugin();
        String name = MicrobotPluginNames.plainName(row.getPluginConfig().getName());
        if (status == null) {
            return;
        }
        switch (status.getKind()) {
            case FAILED:
                Throwable failure = MicrobotPluginFailures.failure(plugin);
                // shortcut: the bug reporter takes no description, so the stack trace reaches it only through its log section.
                if (failure == null) {
                    break;
                }
                if (askToReport(name, MicrobotPluginFailures.describe(failure), row.isAlwaysOn())) {
                    bugReporter.open(this, plugin);
                } else {
                    // Seen and closed: the switch already shows it is off, and an always-on plugin has no switch to clear it.
                    MicrobotPluginFailures.clear(plugin);
                }
                break;
            case HEALTH:
                // The Hub searches its own display name, which may differ from the plugin's.
                MicrobotPluginManifest manifest = microbotPluginManager.getManifestMap().get(row.getPluginConfig().getMicrobotHubName());
                String hubName = manifest == null ? "" : MicrobotPluginNames.plainName(manifest.getDisplayName());
                openHub.accept(hubName.isEmpty() ? name : hubName);
                break;
            case BLOCKED:
                if (row.getPluginConfig().getMicrobotHubName() != null) {
                    openHub.accept(name);
                } else {
                    localPluginsPanelProvider.get().open(this);
                }
                break;
            case UPDATE:
                update(plugin);
                break;
        }
    }

    // Same checks, dialogs and path as the Hub's Update.
    private void update(Plugin plugin) {
        MicrobotPluginManager.OutdatedPluginUpdate update = microbotPluginManager.findUpdate(plugin).orElse(null);
        MicrobotPluginManifest manifest = update == null ? null : microbotPluginManager.getManifestMap().get(update.getInternalName());
        if (manifest == null || updating.contains(update.getInternalName()) || !confirmInstall(manifest, update.getLatestVersion())
                || !updating.add(update.getInternalName())) {
            return;
        }
        refresh();
        microbotPluginManager.updatePlugin(manifest, update.getLatestVersion()).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> {
                    updating.remove(update.getInternalName());
                    refresh();
                    showOperationResult("Couldn't update " + update.getDisplayName(), result, error);
                }));
    }

    boolean askToReport(String name, String reason, boolean alwaysOn) {
        Object[] options = {"Report", "Close"};
        return JOptionPane.showOptionDialog(this, wrappedMessage(failureMessage(name, reason, alwaysOn)), name + " failed to start",
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[1]) == 0;
    }

    // What happened and what to do first; the raw error last, for a report. An always-on plugin has no switch to flip.
    static String failureMessage(String name, String reason, boolean alwaysOn) {
        return name + (alwaysOn
                ? " hit an error while starting. Restart the client to try again, or press Report to send a bug report. Close hides this message."
                : " hit an error while starting and is switched off. Switch it on to try again, or press Report to send a bug report.")
                + "\n\nError: " + reason;
    }

    private static JTextArea wrappedMessage(String message) {
        JTextArea area = new JTextArea(message, 6, 42);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        return area;
    }

    // The Hub panel's own pre-install checks and dialogs, so no safety prompt is skipped here.
    boolean confirmInstall(MicrobotPluginManifest manifest, String version) {
        return MicrobotPluginHubPanel.confirmInstall(this, microbotPluginManager, manifest, version);
    }

    public void rebuildPluginList() {
        List<String> pinnedPlugins = getPinnedPluginNames();

        // External JAR scripts are launched from the title-bar script selector.
        pluginList = Stream.concat(
                        fakePlugins.stream(),
                        pluginManager.getPlugins().stream()
                                .filter(MicrobotPluginListPanel::isVisibleInternalPlugin)
                                .map(this::createPluginConfigurationDescriptor)
                )
                .map(desc ->
                {
                    MicrobotPluginListItem listItem = new MicrobotPluginListItem(this, desc);
                    listItem.setPinned(pinnedPlugins.contains(desc.getName()));
                    return listItem;
                })
                .sorted(Comparator.comparing(p -> p.getPluginConfig().getName()))
                .collect(Collectors.toList());

        mainPanel.removeAll();
        refresh();
        SwingUtilities.invokeLater(this::showBlockedPluginsNotice);
    }

    public void addFakePlugin(MicrobotPluginConfigurationDescriptor... descriptor) {
        Collections.addAll(fakePlugins, descriptor);
    }

    void refresh() {
        // update enabled / disabled status of all items
        pluginList.forEach(listItem ->
        {
            final Plugin plugin = listItem.getPluginConfig().getPlugin();
            if (plugin != null) {
                listItem.setPluginEnabled(pluginManager.isPluginEnabled(plugin));
            }
        });

        int scrollBarPosition = scrollPane.getVerticalScrollBar().getValue();

        onSearchBarChanged();
        searchBar.requestFocusInWindow();
        validate();

        scrollPane.getVerticalScrollBar().setValue(scrollBarPosition);
    }

    void openWithFilter(String filter) {
        searchBar.setText(filter);
        onSearchBarChanged();
        muxer.pushState(this);
    }

    private void onSearchBarChanged() {
        final String text = searchBar.getText();
        pluginList.forEach(mainPanel::remove);
        MicrobotPluginSearch.search(pluginList, text).forEach(mainPanel::add);
        revalidate();
    }

    void openConfigurationPanel(String configGroup) {
        for (MicrobotPluginListItem pluginListItem : pluginList) {
            if (pluginListItem.getPluginConfig().getName().equals(configGroup)) {
                openConfigurationPanel(pluginListItem.getPluginConfig());
                break;
            }
        }
    }

    void openConfigurationPanel(Plugin plugin) {
        for (MicrobotPluginListItem pluginListItem : pluginList) {
            if (pluginListItem.getPluginConfig().getPlugin() == plugin) {
                openConfigurationPanel(pluginListItem.getPluginConfig());
                return;
            }
        }

        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor != null && !descriptor.hidden()) {
            openConfigurationPanel(createPluginConfigurationDescriptor(plugin));
        }
    }

    private MicrobotPluginConfigurationDescriptor createPluginConfigurationDescriptor(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        Config config = pluginManager.getPluginConfigProxy(plugin);
        ConfigDescriptor configDescriptor = config == null ? null : configManager.getConfigDescriptor(config);
        List<String> conflicts = pluginManager.conflictsForPlugin(plugin).stream()
                .map(Plugin::getName)
                .collect(Collectors.toList());

        return new MicrobotPluginConfigurationDescriptor(
                descriptor.name(),
                descriptor.description(),
                descriptor.tags(),
                plugin,
                config,
                configDescriptor,
                conflicts);
    }

    static boolean isVisibleInternalPlugin(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        Package pluginPackage = plugin.getClass().getPackage();
        return descriptor != null
                && pluginPackage != null
                && pluginPackage.getName().contains("microbot")
                && !descriptor.hidden()
                && !descriptor.isExternal();
    }

    void openConfigurationPanel(MicrobotPluginConfigurationDescriptor plugin) {
        if (plugin.getConfigDescriptor() == null) {
            return;
        }

        MicrobotConfigPanel panel = configPanelProvider.get();
        panel.init(plugin);
        muxer.pushState(this);
        muxer.pushState(panel);
    }

    void startPlugin(Plugin plugin) {
        microbotPluginManager.getOutdatedPluginUpdate(plugin)
                .ifPresent(this::showOutdatedPluginNotification);

        pluginManager.setPluginEnabled(plugin, true);

        try {
            pluginManager.startPlugin(plugin);
        } catch (PluginInstantiationException ex) {
            log.warn("Error when starting plugin {}", plugin.getClass().getSimpleName(), ex);
        }
    }

    void stopPlugin(Plugin plugin) {
        pluginManager.setPluginEnabled(plugin, false);

        try {
            pluginManager.stopPlugin(plugin);
        } catch (PluginInstantiationException ex) {
            log.warn("Error when stopping plugin {}", plugin.getClass().getSimpleName(), ex);
        }
    }

    private List<String> getPinnedPluginNames() {
        final String config = configManager.getConfiguration(RUNELITE_GROUP_NAME, PINNED_PLUGINS_CONFIG_KEY);

        if (config == null) {
            return Collections.emptyList();
        }

        return Text.fromCSV(config);
    }

    private void showOutdatedPluginNotification(MicrobotPluginManager.OutdatedPluginUpdate outdatedPluginUpdate) {
        String message = "<html>\""
                + HtmlEscapers.htmlEscaper().escape(outdatedPluginUpdate.getDisplayName())
                + "\" is out of date.<br><br>Installed version: "
                + HtmlEscapers.htmlEscaper().escape(outdatedPluginUpdate.getInstalledVersion())
                + "<br>Latest version: "
                + HtmlEscapers.htmlEscaper().escape(outdatedPluginUpdate.getLatestVersion())
                + "<br><br>Update it from the <strong>Microbot Plugin Hub</strong> to get the latest fixes.</html>";

        JOptionPane.showMessageDialog(
                this,
                message,
                "Microbot Plugin Update Available",
                JOptionPane.INFORMATION_MESSAGE
        );

        microbotPluginManager.rememberOutdatedPluginUpdateNotification(outdatedPluginUpdate);
    }

    private void showBlockedPluginsNotice() {
        Map<String, String> blocked = microbotPluginManager.takeBlockedPluginsToNotify();
        if (blocked.isEmpty()) {
            return;
        }

        Map<String, MicrobotPluginManifest> manifests = microbotPluginManager.getManifestMap();
        String message = blockedPluginsMessage(blocked, name -> {
            MicrobotPluginManifest manifest = manifests.get(name);
            return manifest == null || manifest.getDisplayName() == null ? name : manifest.getDisplayName();
        });

        long deadline = System.currentTimeMillis() + BLOCKED_NOTICE_WAIT_MS;
        Timer timer = new Timer(1000, null);
        timer.setInitialDelay(0);
        timer.addActionListener(e -> {
            Window window = Arrays.stream(Window.getWindows())
                    .filter(w -> w instanceof ContainableFrame && w.isShowing())
                    .findFirst()
                    .orElse(null);
            if (window == null) {
                if (System.currentTimeMillis() > deadline) {
                    timer.stop();
                }
                return;
            }
            timer.stop();
            JDialog dialog = new JOptionPane(message, JOptionPane.WARNING_MESSAGE)
                    .createDialog(window, "Microbot Plugins Not Loaded");
            dialog.setModal(false);
            dialog.setVisible(true);
        });
        timer.start();
    }

    static String blockedPluginsMessage(Map<String, String> blocked, Function<String, String> displayName) {
        StringBuilder message = new StringBuilder("<html>These Microbot Plugin Hub plugins were not loaded:<br>");
        blocked.forEach((name, reason) -> message.append("<br>&bull; <b>")
                .append(HtmlEscapers.htmlEscaper().escape(displayName.apply(name)))
                .append("</b>: ")
                .append(HtmlEscapers.htmlEscaper().escape(reason)));
        return message.append("<br><br>Open the <strong>Microbot Plugin Hub</strong> to see details, remove them or install an unaffected version. For a JAR you added yourself, fix or remove the JAR in <strong>Local Plugins</strong>.</html>")
                .toString();
    }

    void savePinnedPlugins() {
        final String value = pluginList.stream()
                .filter(MicrobotPluginListItem::isPinned)
                .map(p -> p.getPluginConfig().getName())
                .collect(Collectors.joining(","));

        configManager.setConfiguration(RUNELITE_GROUP_NAME, PINNED_PLUGINS_CONFIG_KEY, value);
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 24);
        return d;
    }

    @Override
    public void onActivate() {
        super.onActivate();

        if (searchBar.getParent() != null) {
            searchBar.requestFocusInWindow();
        }
    }

    @Subscribe
    private void onExternalPluginsChanged(ExternalPluginsChanged ev) {
        SwingUtilities.invokeLater(this::rebuildPluginList);
    }

    @Subscribe
    private void onProfileChanged(ProfileChanged ev) {
        SwingUtilities.invokeLater(this::rebuildPluginList);
    }
}

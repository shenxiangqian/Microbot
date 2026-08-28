package net.runelite.client.plugins.microbot.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager.SideLoadRefreshResult;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager.SideLoadedScript;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
final class ScriptSelectionDialog extends JDialog {
    private static final Dimension DIALOG_SIZE = new Dimension(420, 440);
    private static final ImageIcon REFRESH_ICON = new ImageIcon(ImageUtil.loadImageResource(
            ScriptSelectionDialog.class,
            "/net/runelite/client/plugins/microbot/inventorysetups/update_icon.png"));

    private final MicrobotPluginManager microbotPluginManager;
    private final List<SideLoadedScript> scripts;
    private final DefaultListModel<SideLoadedScript> listModel = new DefaultListModel<>();
    private final JList<SideLoadedScript> scriptList = new JList<>(listModel);
    private final IconTextField searchField = new IconTextField();
    private final JButton refreshButton = new JButton(REFRESH_ICON);
    private final JButton configureButton = new JButton("Configure");
    private final JButton startButton = new JButton("Start");
    private final JLabel countLabel = new JLabel();

    private Selection selection;
    private boolean refreshInProgress;

    private ScriptSelectionDialog(Window owner, MicrobotPluginManager microbotPluginManager) {
        super(owner, "Start External Script", ModalityType.APPLICATION_MODAL);
        this.microbotPluginManager = microbotPluginManager;
        this.scripts = new ArrayList<>(microbotPluginManager.getSideLoadedScripts());

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(DIALOG_SIZE);
        setPreferredSize(DIALOG_SIZE);
        setResizable(true);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(owner);
        refreshList("");
    }

    static Selection showDialog(Window owner, MicrobotPluginManager microbotPluginManager) {
        ScriptSelectionDialog dialog = new ScriptSelectionDialog(owner, microbotPluginManager);
        dialog.setVisible(true);
        return dialog.selection;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setPreferredSize(new Dimension(0, 30));
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshList(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshList(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshList(searchField.getText());
            }
        });

        refreshButton.setPreferredSize(new Dimension(30, 30));
        refreshButton.setMinimumSize(new Dimension(30, 30));
        refreshButton.setMaximumSize(new Dimension(30, 30));
        refreshButton.setToolTipText("Reload script JARs");
        SwingUtil.removeButtonDecorations(refreshButton);
        refreshButton.addActionListener(event -> refreshScripts());

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(refreshButton, BorderLayout.EAST);
        header.add(searchPanel, BorderLayout.NORTH);
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.add(countLabel, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        scriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scriptList.setFixedCellHeight(32);
        scriptList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scriptList.setCellRenderer(new ScriptCellRenderer());
        scriptList.addListSelectionListener(event -> updateActions());
        scriptList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    choose(Action.START);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(scriptList);
        scrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        content.add(scrollPane, BorderLayout.CENTER);

        configureButton.setEnabled(false);
        configureButton.addActionListener(event -> choose(Action.CONFIGURE));
        startButton.setEnabled(false);
        startButton.addActionListener(event -> choose(Action.START));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dispose());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(configureButton, BorderLayout.WEST);
        JPanel commands = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        commands.setOpaque(false);
        commands.add(cancelButton);
        commands.add(startButton);
        actions.add(commands, BorderLayout.EAST);
        content.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(startButton);
        return content;
    }

    private void refreshScripts() {
        if (refreshInProgress) {
            return;
        }
        refreshInProgress = true;
        refreshButton.setEnabled(false);
        refreshButton.setToolTipText("Reloading script JARs...");
        scriptList.setEnabled(false);
        updateActions();

        microbotPluginManager.refreshSideLoadedPlugins().whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> finishRefresh(result, error)));
    }

    private void finishRefresh(SideLoadRefreshResult result, Throwable error) {
        if (!isDisplayable()) {
            return;
        }
        refreshInProgress = false;
        refreshButton.setEnabled(true);
        scriptList.setEnabled(true);
        scripts.clear();
        scripts.addAll(microbotPluginManager.getSideLoadedScripts());
        refreshList(searchField.getText());

        if (error != null) {
            refreshButton.setToolTipText("Script refresh failed");
            log.warn("Unable to refresh side-loaded plugins", error);
            JOptionPane.showMessageDialog(
                    this,
                    "Unable to refresh script JARs. Check the client log for details.",
                    "Script Refresh Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        refreshButton.setToolTipText(result.getSummary());
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
    }

    private void refreshList(String searchText) {
        SideLoadedScript selected = scriptList.getSelectedValue();
        String normalizedSearch = searchText.trim().toLowerCase(Locale.ROOT);
        listModel.clear();
        scripts.stream()
                .filter(script -> matchesSearch(script, normalizedSearch))
                .forEach(listModel::addElement);

        if (selected != null) {
            for (int index = 0; index < listModel.size(); index++) {
                SideLoadedScript candidate = listModel.get(index);
                if (candidate.getInternalName().equals(selected.getInternalName())
                        && candidate.getClassName().equals(selected.getClassName())) {
                    scriptList.setSelectedIndex(index);
                    break;
                }
            }
        }
        if (scriptList.getSelectedIndex() < 0 && !listModel.isEmpty()) {
            scriptList.setSelectedIndex(0);
        }
        countLabel.setText(listModel.size() + " external script(s)");
        updateActions();
    }

    private static boolean matchesSearch(SideLoadedScript script, String searchText) {
        if (searchText.isEmpty()) {
            return true;
        }
        return script.getDisplayName().toLowerCase(Locale.ROOT).contains(searchText)
                || script.getInternalName().toLowerCase(Locale.ROOT).contains(searchText)
                || script.getDescription().toLowerCase(Locale.ROOT).contains(searchText);
    }

    private void updateActions() {
        SideLoadedScript selected = scriptList.getSelectedValue();
        startButton.setEnabled(!refreshInProgress && selected != null);
        configureButton.setEnabled(!refreshInProgress && selected != null && selected.isConfigurable());
    }

    private void choose(Action action) {
        SideLoadedScript selected = scriptList.getSelectedValue();
        if (refreshInProgress || selected == null
                || action == Action.CONFIGURE && !selected.isConfigurable()) {
            return;
        }
        selection = new Selection(action, selected);
        dispose();
    }

    enum Action {
        START,
        CONFIGURE
    }

    static final class Selection {
        private final Action action;
        private final SideLoadedScript script;

        private Selection(Action action, SideLoadedScript script) {
            this.action = action;
            this.script = script;
        }

        Action getAction() {
            return action;
        }

        SideLoadedScript getScript() {
            return script;
        }
    }

    private static final class ScriptCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            SideLoadedScript script = (SideLoadedScript) value;
            String version = script.getVersion().isEmpty() ? "" : "  v" + script.getVersion();
            label.setText(script.getDisplayName() + version);
            label.setToolTipText(script.getDescription());
            label.setBorder(new EmptyBorder(0, 8, 0, 8));
            return label;
        }
    }
}

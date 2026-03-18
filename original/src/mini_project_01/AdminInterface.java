package mini_project_01;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Admin Interface for the Server Redundancy Management System (Step 7).
 *
 * Provides a graphical dashboard that allows an administrator to:
 *   - View real-time logs from the client and system events.
 *   - Check server health and see which server is the current primary.
 *   - Send PROCESS requests to the primary for testing.
 *   - Trigger manual failover or other administrative actions.
 *   - Monitor heartbeat status and cluster topology.
 *
 * Built with Java Swing. Designed to integrate with the existing
 * Client, Monitor, and Message classes in the project.
 */
public class AdminInterface extends JFrame {

    // ── Palette & Theming ───────────────────────────────────────────────
    // Dark, industrial/terminal-inspired palette
    private static final Color BG_DARK       = new Color(18, 18, 24);
    private static final Color BG_PANEL      = new Color(26, 27, 38);
    private static final Color BG_INPUT      = new Color(34, 36, 50);
    private static final Color BORDER_COLOR  = new Color(48, 50, 68);
    private static final Color TEXT_PRIMARY   = new Color(220, 224, 240);
    private static final Color TEXT_SECONDARY = new Color(130, 136, 165);
    private static final Color ACCENT_GREEN   = new Color(80, 220, 140);
    private static final Color ACCENT_RED     = new Color(240, 90, 90);
    private static final Color ACCENT_AMBER   = new Color(240, 190, 60);
    private static final Color ACCENT_BLUE    = new Color(100, 160, 255);
    private static final Color ACCENT_CYAN    = new Color(80, 210, 230);

    private static final Font FONT_MONO       = new Font("JetBrains Mono", Font.PLAIN, 13);
    private static final Font FONT_MONO_SMALL = new Font("JetBrains Mono", Font.PLAIN, 11);
    private static final Font FONT_TITLE      = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font FONT_HEADING    = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_BODY       = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BUTTON     = new Font("Segoe UI", Font.BOLD, 12);

    // ── Networking ──────────────────────────────────────────────────────
    private Client client;
    private String monitorHost;
    private int    monitorPort;
    private final JsonMessageSerializer serializer = new JsonMessageSerializer();

    // ── UI Components ───────────────────────────────────────────────────
    private JTextArea   logArea;
    private JTable      serverTable;
    private DefaultTableModel serverTableModel;
    private JLabel      primaryLabel;
    private JLabel      statusIndicator;
    private JLabel      uptimeLabel;
    private JTextField  requestField;
    private JTextArea   responseArea;
    private JLabel      connStatusLabel;

    // ── Background tasks ────────────────────────────────────────────────
    private ScheduledExecutorService scheduler;
    private long startTime;

    // ────────────────────────────────────────────────────────────────────
    // Construction & Layout
    // ────────────────────────────────────────────────────────────────────

    public AdminInterface(String monitorHost, int monitorPort) {
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.client      = new Client(monitorHost, monitorPort);
        this.startTime   = System.currentTimeMillis();

        // Wire client logs into the GUI log area
        client.setLogListener(entry -> SwingUtilities.invokeLater(() -> appendLog(entry)));

        initLookAndFeel();
        initFrame();
        buildUI();
        startBackgroundTasks();
    }

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        UIManager.put("ToolTip.background", BG_INPUT);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER_COLOR));
        UIManager.put("ToolTip.font", FONT_BODY);
    }

    private void initFrame() {
        setTitle("SRMS Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        // ── Top Bar ─────────────────────────────────────────────────────
        add(createTopBar(), BorderLayout.NORTH);

        // ── Main Content: left = servers + request, right = logs ────────
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createLeftPanel(), createLogPanel());
        mainSplit.setDividerLocation(560);
        mainSplit.setDividerSize(3);
        mainSplit.setBorder(null);
        mainSplit.setBackground(BG_DARK);
        styleScrollSplitPane(mainSplit);
        add(mainSplit, BorderLayout.CENTER);

        // ── Bottom Status Bar ───────────────────────────────────────────
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    // ────────────────────────────────────────────────────────────────────
    // Top bar
    // ────────────────────────────────────────────────────────────────────

    private JPanel createTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(10, 18, 10, 18)));

        // Left: title + status dot
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        statusIndicator = new JLabel("●");
        statusIndicator.setForeground(ACCENT_AMBER);
        statusIndicator.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        left.add(statusIndicator);

        JLabel title = new JLabel("SRMS  Admin Dashboard");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);
        left.add(title);

        bar.add(left, BorderLayout.WEST);

        // Right: connection info + uptime
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);

        connStatusLabel = new JLabel("Connecting...");
        connStatusLabel.setFont(FONT_BODY);
        connStatusLabel.setForeground(TEXT_SECONDARY);
        right.add(connStatusLabel);

        uptimeLabel = new JLabel("Uptime: 0s");
        uptimeLabel.setFont(FONT_BODY);
        uptimeLabel.setForeground(TEXT_SECONDARY);
        right.add(uptimeLabel);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ────────────────────────────────────────────────────────────────────
    // Left panel (server table + request panel + action buttons)
    // ────────────────────────────────────────────────────────────────────

    private JPanel createLeftPanel() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(BG_DARK);
        left.setBorder(new EmptyBorder(8, 10, 8, 4));

        // Cluster overview section
        left.add(createClusterPanel());
        left.add(Box.createVerticalStrut(8));

        // Request/response section
        left.add(createRequestPanel());
        left.add(Box.createVerticalStrut(8));

        // Admin actions section
        left.add(createActionsPanel());

        return left;
    }

    // ── Cluster Overview ────────────────────────────────────────────────

    private JPanel createClusterPanel() {
        JPanel panel = createSectionPanel("Cluster Overview");

        // Primary info row
        JPanel primaryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        primaryRow.setOpaque(false);

        JLabel lbl = new JLabel("Primary:");
        lbl.setFont(FONT_HEADING);
        lbl.setForeground(TEXT_SECONDARY);
        primaryRow.add(lbl);

        primaryLabel = new JLabel("discovering...");
        primaryLabel.setFont(FONT_MONO);
        primaryLabel.setForeground(ACCENT_CYAN);
        primaryRow.add(primaryLabel);

        panel.add(primaryRow);
        panel.add(Box.createVerticalStrut(6));

        // Server table
        String[] columns = {"ID", "Address", "Role", "Status", "Last Heartbeat"};
        serverTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        serverTable = new JTable(serverTableModel);
        styleTable(serverTable);

        JScrollPane tableScroll = new JScrollPane(serverTable);
        tableScroll.setPreferredSize(new Dimension(520, 150));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        styleScrollPane(tableScroll);
        panel.add(tableScroll);

        return panel;
    }

    // ── Request / Response Panel ────────────────────────────────────────

    private JPanel createRequestPanel() {
        JPanel panel = createSectionPanel("Send Request");

        // Input row
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        requestField = new JTextField();
        requestField.setFont(FONT_MONO);
        requestField.setBackground(BG_INPUT);
        requestField.setForeground(TEXT_PRIMARY);
        requestField.setCaretColor(ACCENT_CYAN);
        requestField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(6, 10, 6, 10)));
        requestField.setToolTipText("Enter payload for PROCESS request (e.g. job-42)");
        requestField.addActionListener(e -> sendRequest());  // Enter key
        inputRow.add(requestField, BorderLayout.CENTER);

        JButton sendBtn = createStyledButton("Send", ACCENT_BLUE);
        sendBtn.addActionListener(e -> sendRequest());
        inputRow.add(sendBtn, BorderLayout.EAST);

        panel.add(inputRow);
        panel.add(Box.createVerticalStrut(6));

        // Response display
        responseArea = new JTextArea(4, 40);
        responseArea.setEditable(false);
        responseArea.setFont(FONT_MONO_SMALL);
        responseArea.setBackground(BG_INPUT);
        responseArea.setForeground(ACCENT_GREEN);
        responseArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);

        JScrollPane respScroll = new JScrollPane(responseArea);
        respScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        styleScrollPane(respScroll);
        panel.add(respScroll);

        return panel;
    }

    // ── Admin Actions ───────────────────────────────────────────────────

    private JPanel createActionsPanel() {
        JPanel panel = createSectionPanel("Admin Actions");

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.setOpaque(false);

        JButton discoverBtn = createStyledButton("Discover Primary", ACCENT_CYAN);
        discoverBtn.addActionListener(e -> runAsync(() -> {
            boolean found = client.discoverPrimary();
            SwingUtilities.invokeLater(() -> {
                if (found) {
                    primaryLabel.setText(client.getPrimaryHost() + ":" + client.getPrimaryPort());
                    updateStatusIndicator(true);
                } else {
                    primaryLabel.setText("none");
                    updateStatusIndicator(false);
                }
            });
        }));
        buttonRow.add(discoverBtn);

        JButton failoverBtn = createStyledButton("Manual Failover", ACCENT_AMBER);
        failoverBtn.addActionListener(e -> runAsync(() -> {
            Message resp = client.triggerManualFailover();
            SwingUtilities.invokeLater(() -> {
                if (resp != null) {
                    responseArea.setText("Failover response: " + resp.getPayload());
                } else {
                    responseArea.setText("Failover request failed. Check logs.");
                }
            });
            // Re-discover after failover
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            client.discoverPrimary();
            SwingUtilities.invokeLater(() -> refreshPrimaryLabel());
        }));
        buttonRow.add(failoverBtn);

        JButton pingBtn = createStyledButton("Ping Primary", ACCENT_GREEN);
        pingBtn.addActionListener(e -> runAsync(() -> {
            Message resp = client.ping();
            SwingUtilities.invokeLater(() -> {
                if (resp != null) {
                    responseArea.setText("Ping → " + resp.getPayload());
                    updateStatusIndicator(true);
                } else {
                    responseArea.setText("Ping failed. Primary may be down.");
                    updateStatusIndicator(false);
                }
            });
        }));
        buttonRow.add(pingBtn);

        JButton refreshBtn = createStyledButton("Refresh Status", TEXT_SECONDARY);
        refreshBtn.addActionListener(e -> runAsync(this::refreshClusterStatus));
        buttonRow.add(refreshBtn);

        panel.add(buttonRow);
        return panel;
    }

    // ────────────────────────────────────────────────────────────────────
    // Log Panel (right side)
    // ────────────────────────────────────────────────────────────────────

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(8, 4, 8, 10));

        // Header with clear button
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 4, 6, 0));

        JLabel logTitle = new JLabel("Event Log");
        logTitle.setFont(FONT_HEADING);
        logTitle.setForeground(TEXT_PRIMARY);
        header.add(logTitle, BorderLayout.WEST);

        JButton clearBtn = createSmallButton("Clear");
        clearBtn.addActionListener(e -> logArea.setText(""));
        header.add(clearBtn, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        // Log text area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(FONT_MONO_SMALL);
        logArea.setBackground(BG_PANEL);
        logArea.setForeground(TEXT_PRIMARY);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane scroll = new JScrollPane(logArea);
        styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ────────────────────────────────────────────────────────────────────
    // Status Bar (bottom)
    // ────────────────────────────────────────────────────────────────────

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(6, 18, 6, 18)));

        JLabel monitorInfo = new JLabel("Monitor: " + monitorHost + ":" + monitorPort);
        monitorInfo.setFont(FONT_MONO_SMALL);
        monitorInfo.setForeground(TEXT_SECONDARY);
        bar.add(monitorInfo, BorderLayout.WEST);

        JLabel version = new JLabel("SRMS v1.0  |  COMP 370 Mini Project 1");
        version.setFont(FONT_MONO_SMALL);
        version.setForeground(new Color(80, 84, 108));
        bar.add(version, BorderLayout.EAST);

        return bar;
    }

    // ────────────────────────────────────────────────────────────────────
    // Actions
    // ────────────────────────────────────────────────────────────────────

    private void sendRequest() {
        String payload = requestField.getText().trim();
        if (payload.isEmpty()) return;
        requestField.setText("");

        runAsync(() -> {
            Message resp = client.sendRequest(payload);
            SwingUtilities.invokeLater(() -> {
                if (resp != null) {
                    responseArea.setText("Type: " + resp.getType()
                            + "\nFrom Server: " + resp.getSenderId()
                            + "\nPayload: " + resp.getPayload());
                    updateStatusIndicator(true);
                } else {
                    responseArea.setText("No response. Primary may be down.");
                    updateStatusIndicator(false);
                }
            });
        });
    }

    private void refreshClusterStatus() {
        // Try to get status from the monitor
        Message status = client.getClusterStatus();
        SwingUtilities.invokeLater(() -> {
            if (status != null && status.getPayload() != null) {
                parseAndDisplayStatus(status.getPayload());
                appendLog("[ADMIN] Cluster status refreshed.");
            } else {
                appendLog("[ADMIN] Could not fetch cluster status from monitor.");
            }
            refreshPrimaryLabel();
        });
    }

    /**
     * Parse a status payload from the monitor and populate the server table.
     * Expected payload format (one server per line):
     *   id:host:port:role:lastHeartbeatMs
     * 
     * If the monitor doesn't support GET_STATUS yet, the table remains
     * as-is and we just update the primary label.
     */
    private void parseAndDisplayStatus(String payload) {
        serverTableModel.setRowCount(0);
        if (payload == null || payload.isEmpty()) return;

        String[] lines = payload.split(";");
        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length >= 4) {
                String id        = parts[0].trim();
                String address   = parts[1].trim();
                String role      = parts[2].trim();
                String status    = parts[3].trim();
                String heartbeat = parts.length >= 5 ? parts[4].trim() : "—";
                serverTableModel.addRow(new Object[]{id, address, role, status, heartbeat});
            }
        }
    }

    private void refreshPrimaryLabel() {
        if (client.isConnected() && client.getPrimaryHost() != null) {
            primaryLabel.setText(client.getPrimaryHost() + ":" + client.getPrimaryPort());
            primaryLabel.setForeground(ACCENT_CYAN);
            updateStatusIndicator(true);
        } else {
            primaryLabel.setText("none");
            primaryLabel.setForeground(ACCENT_RED);
            updateStatusIndicator(false);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Background scheduled tasks
    // ────────────────────────────────────────────────────────────────────

    private void startBackgroundTasks() {
        scheduler = Executors.newScheduledThreadPool(2);

        // Initial discovery
        scheduler.schedule(() -> {
            client.discoverPrimary();
            SwingUtilities.invokeLater(this::refreshPrimaryLabel);
        }, 500, TimeUnit.MILLISECONDS);

        // Periodic status refresh every 10s
        scheduler.scheduleAtFixedRate(() -> {
            refreshClusterStatus();
            SwingUtilities.invokeLater(this::updateUptime);
        }, 5, 10, TimeUnit.SECONDS);

        // Uptime counter every second
        scheduler.scheduleAtFixedRate(() ->
            SwingUtilities.invokeLater(this::updateUptime),
        1, 1, TimeUnit.SECONDS);
    }

    private void updateUptime() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long h = elapsed / 3600;
        long m = (elapsed % 3600) / 60;
        long s = elapsed % 60;
        uptimeLabel.setText(String.format("Uptime: %02d:%02d:%02d", h, m, s));
    }

    private void updateStatusIndicator(boolean healthy) {
        statusIndicator.setForeground(healthy ? ACCENT_GREEN : ACCENT_RED);
        connStatusLabel.setText(healthy
                ? "Connected to cluster"
                : "Disconnected");
        connStatusLabel.setForeground(healthy ? ACCENT_GREEN : ACCENT_RED);
    }

    // ────────────────────────────────────────────────────────────────────
    // Logging
    // ────────────────────────────────────────────────────────────────────

    private void appendLog(String entry) {
        if (logArea != null) {
            logArea.append(entry + "\n");
            // Auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Styling helpers
    // ────────────────────────────────────────────────────────────────────

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 14, 12, 14)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(title);
        lbl.setFont(FONT_HEADING);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setBorder(new EmptyBorder(0, 0, 6, 0));
        panel.add(lbl);

        return panel;
    }

    private JButton createStyledButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(accent.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
                } else {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 20));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                // Border
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BUTTON);
        btn.setForeground(accent);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 24, 32));
        return btn;
    }

    private JButton createSmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_MONO_SMALL);
        btn.setForeground(TEXT_SECONDARY);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_INPUT);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(FONT_MONO_SMALL);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(new Color(60, 64, 90));
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setRowHeight(28);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        // Header styling
        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_PANEL);
        header.setForeground(TEXT_SECONDARY);
        header.setFont(FONT_HEADING);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        header.setReorderingAllowed(false);

        // Custom cell renderer for role/status coloring
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                c.setBackground(isSelected ? new Color(60, 64, 90) : BG_INPUT);

                String text = value != null ? value.toString() : "";
                if (col == 2) { // Role column
                    c.setForeground(text.equalsIgnoreCase("PRIMARY") ? ACCENT_CYAN : TEXT_SECONDARY);
                } else if (col == 3) { // Status column
                    if (text.equalsIgnoreCase("ALIVE") || text.equalsIgnoreCase("ACTIVE")) {
                        c.setForeground(ACCENT_GREEN);
                    } else if (text.equalsIgnoreCase("DEAD") || text.equalsIgnoreCase("DOWN")) {
                        c.setForeground(ACCENT_RED);
                    } else {
                        c.setForeground(ACCENT_AMBER);
                    }
                } else {
                    c.setForeground(TEXT_PRIMARY);
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return c;
            }
        });
    }

    private void styleScrollPane(JScrollPane sp) {
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        sp.getViewport().setBackground(BG_INPUT);
        sp.setBackground(BG_INPUT);

        // Style scrollbars
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = BORDER_COLOR;
                trackColor = BG_PANEL;
            }
            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }
            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }
            private JButton createZeroButton() {
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(0, 0));
                return btn;
            }
        });
    }

    private void styleScrollSplitPane(JSplitPane sp) {
        sp.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(BORDER_COLOR);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────
    // Async helper
    // ────────────────────────────────────────────────────────────────────

    private void runAsync(Runnable task) {
        CompletableFuture.runAsync(task).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                appendLog("[ERROR] " + ex.getMessage()));
            return null;
        });
    }

    // ────────────────────────────────────────────────────────────────────
    // Cleanup
    // ────────────────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        if (scheduler != null) scheduler.shutdownNow();
        super.dispose();
    }

    // ────────────────────────────────────────────────────────────────────
    // Entry Point
    // ────────────────────────────────────────────────────────────────────

    /**
     * Launch the Admin Dashboard.
     * Usage:  java mini_project_01.AdminInterface <monitorHost> <monitorPort>
     *
     * Defaults to localhost:9000 if no arguments are provided.
     */
    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "localhost";
        int port    = args.length >= 2 ? Integer.parseInt(args[1]) : 9000;

        SwingUtilities.invokeLater(() -> {
            AdminInterface admin = new AdminInterface(host, port);
            admin.setVisible(true);
        });
    }
}

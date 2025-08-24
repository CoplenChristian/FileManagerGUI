package CoplenChristian.FileManagerGUI.ui;

import CoplenChristian.FileManagerGUI.scan.FolderScanner;
import CoplenChristian.FileManagerGUI.scan.FolderScanner.Item;
import CoplenChristian.FileManagerGUI.util.HumanSize;
import CoplenChristian.FileManagerGUI.util.Settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FileFolderGui {

    /** Table model for Items. */
    private static final class ItemTableModel extends AbstractTableModel {
        private final List<Item> items = new ArrayList<>();
        private final String[] cols = {"Name", "Type", "Size", "Source"};
        @Override public int getRowCount() { return items.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c){
                case 0,1,3 -> String.class;
                case 2      -> Long.class; // sort numerically by bytes
                default     -> Object.class;
            };
        }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int r, int c) {
            Item it = items.get(r);
            return switch (c) {
                case 0 -> it.name;
                case 1 -> it.isDirectory ? "Folder" : "File";
                case 2 -> it.sizeBytes;
                case 3 -> it.fromCache ? "Cache" : "Fresh";
                default -> "";
            };
        }
        public Item getItem(int r) { return items.get(r); }
        public void setItems(List<Item> newItems){ items.clear(); items.addAll(newItems); fireTableDataChanged(); }
    }

    // ---- UI state ----
    private JFrame frame;
    private JTextField pathField;
    private JTable table;
    private ItemTableModel model;
    private TableRowSorter<ItemTableModel> sorter;
    private JLabel status;
    private JProgressBar progress;
    private JComboBox<File> drivesCombo;
    private JLabel driveInfo; // live free/total space
    private JButton listBtn, calcBtn, top5Btn, deleteBtn, upBtn, refreshBtn, clearCacheBtn;

    // theme colors (computed by theme)
    private Color BG, FG, STRIPE, SEL_BG, SEL_FG, GRID, HOT, WARM;

    // Settings (JSON persisted)
    private final Settings settings = Settings.load();

    // worker + cancel flag
    private final FolderScanner scanner = new FolderScanner();
    private volatile Future<?> currentTask;
    private volatile AtomicBoolean cancelFlag;

    // ---- Launch ----
    public static void launch() { EventQueue.invokeLater(FileFolderGui::new); }

    public FileFolderGui() {
        frame = new JFrame("File Manager GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1300, 820);
        frame.setLayout(new BorderLayout());

        // ===== LEFT SIDE: Tabbed (Explorer / Settings) =====
        JTabbedPane leftTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        leftTabs.setBorder(new EmptyBorder(10,10,10,10));

        // ---------- Explorer tab (old left control column) ----------
        JPanel explorerPanel = new JPanel();
        explorerPanel.setLayout(new BoxLayout(explorerPanel, BoxLayout.Y_AXIS));

        pathField = new JTextField(28);
        JButton browseBtn = new JButton("Browse…");
        listBtn = new JButton("List Contents");
        calcBtn = new JButton("Calculate Sizes");
        top5Btn = new JButton("Top 5 in Drive");
        deleteBtn = new JButton("Delete Selected…");
        upBtn = new JButton("⬆ Up");
        refreshBtn = new JButton("Refresh");
        clearCacheBtn = new JButton("Clear Cache");

        drivesCombo = new JComboBox<>(File.listRoots());
        JPanel drivePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        drivePanel.add(new JLabel("Drive:"));
        drivePanel.add(drivesCombo);
        drivePanel.add(top5Btn);

        // live drive info label
        driveInfo = new JLabel(" ");
        driveInfo.setBorder(new EmptyBorder(4, 0, 0, 0));

        explorerPanel.add(new JLabel("Folder:"));
        explorerPanel.add(pathField);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(browseBtn);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(listBtn);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(calcBtn);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(upBtn);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(deleteBtn);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(refreshBtn);
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(clearCacheBtn);
        explorerPanel.add(Box.createVerticalStrut(12));
        explorerPanel.add(new JSeparator());
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(drivePanel);
        explorerPanel.add(driveInfo);

        // Common Folders panel (quick nav)
        JPanel quick = new JPanel(new GridLayout(0, 1, 5, 5));
        quick.setBorder(BorderFactory.createTitledBorder("Common Folders"));

        Map<String, String> common = new LinkedHashMap<>();
        String home = System.getProperty("user.home");
        common.put("Documents", home + File.separator + "Documents");
        common.put("Desktop",   home + File.separator + "Desktop");
        common.put("Downloads", home + File.separator + "Downloads");
        common.put("AppData",   home + File.separator + "AppData"); // Windows

        for (var e : common.entrySet()) {
            JButton b = new JButton(e.getKey());
            b.addActionListener(a -> {
                pathField.setText(e.getValue());
                executeList();
            });
            quick.add(b);
        }
        explorerPanel.add(Box.createVerticalStrut(8));
        explorerPanel.add(quick);

        // ---------- Settings tab ----------
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(new EmptyBorder(6,6,6,6));

        // Theme group
        JPanel themePanel = new JPanel(new GridLayout(0,1,4,4));
        themePanel.setBorder(BorderFactory.createTitledBorder("Theme"));
        JRadioButton rbLight = new JRadioButton("Light");
        JRadioButton rbDark  = new JRadioButton("Dark");
        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(rbLight); themeGroup.add(rbDark);
        rbLight.setSelected(settings.theme == Settings.Theme.LIGHT);
        rbDark.setSelected(settings.theme == Settings.Theme.DARK);
        themePanel.add(rbLight);
        themePanel.add(rbDark);

        rbLight.addActionListener(e -> {
            settings.theme = Settings.Theme.LIGHT;
            settings.save();
            applyTheme(settings.theme);
        });
        rbDark.addActionListener(e -> {
            settings.theme = Settings.Theme.DARK;
            settings.save();
            applyTheme(settings.theme);
        });

        // Delete behavior
        JPanel deletePanel = new JPanel(new GridLayout(0,1,4,4));
        deletePanel.setBorder(BorderFactory.createTitledBorder("Delete Behavior"));
        JRadioButton rbRecycleFirst = new JRadioButton("Use Recycle Bin / Trash if available (fallback to permanent on confirm)");
        JRadioButton rbPermanent    = new JRadioButton("Always permanently delete");
        ButtonGroup delGroup = new ButtonGroup();
        delGroup.add(rbRecycleFirst); delGroup.add(rbPermanent);
        rbRecycleFirst.setSelected(!settings.alwaysPermanentDelete);
        rbPermanent.setSelected(settings.alwaysPermanentDelete);

        JCheckBox cbConfirm = new JCheckBox("Confirm before permanent delete", true);
        cbConfirm.setSelected(settings.confirmPermanentDelete);

        deletePanel.add(rbRecycleFirst);
        deletePanel.add(rbPermanent);
        deletePanel.add(cbConfirm);

        rbRecycleFirst.addActionListener(e -> { settings.alwaysPermanentDelete = false; settings.save(); });
        rbPermanent.addActionListener(e -> { settings.alwaysPermanentDelete = true;  settings.save(); });
        cbConfirm.addActionListener(e -> { settings.confirmPermanentDelete = cbConfirm.isSelected(); settings.save(); });

        settingsPanel.add(themePanel);
        settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(deletePanel);
        settingsPanel.add(Box.createVerticalGlue());

        // Add tabs
        leftTabs.addTab("Explorer", explorerPanel);
        leftTabs.addTab("Settings", settingsPanel);

        // ===== CENTER: table =====
        model = new ItemTableModel();
        table = new JTable(model) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r,row,col);
                if (!isRowSelected(row)) {
                    c.setBackground((row%2==0)? BG : STRIPE);
                    c.setForeground(FG);
                }
                return c;
            }
        };
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);

        // Column widths for better readability
        table.getColumnModel().getColumn(0).setPreferredWidth(520); // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(80);  // Type
        table.getColumnModel().getColumn(2).setPreferredWidth(120); // Size
        table.getColumnModel().getColumn(3).setPreferredWidth(80);  // Source

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        sorter.toggleSortOrder(2); sorter.toggleSortOrder(2); // size desc

        // Double-click / Enter to open folder
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount()==2 && SwingUtilities.isLeftMouseButton(e)) {
                    int vr = table.rowAtPoint(e.getPoint());
                    if (vr>=0) {
                        int mr = table.convertRowIndexToModel(vr);
                        Item it = model.getItem(mr);
                        if (it.isDirectory) { pathField.setText(it.path.toString()); executeList(); }
                    }
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
             .put(KeyStroke.getKeyStroke("ENTER"), "openFolder");
        table.getActionMap().put("openFolder", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int vr = table.getSelectedRow(); if (vr<0) return;
                int mr = table.convertRowIndexToModel(vr);
                Item it = model.getItem(mr);
                if (it.isDirectory) { pathField.setText(it.path.toString()); executeList(); }
            }
        });

        // Pretty size renderer: human-readable + color emphasis
        DefaultTableCellRenderer sizeRenderer = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                if (value instanceof Long l) {
                    setText(HumanSize.format(l));
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setFont(getFont().deriveFont(Font.PLAIN));
                    setForeground(FG); // default
                    final long HOT_GIB  = 1L << 30;    // 1 GiB
                    final long WARM_MIB = 256L << 20;  // 256 MiB
                    if (l >= HOT_GIB) {
                        setFont(getFont().deriveFont(Font.BOLD));
                        setForeground(HOT);
                    } else if (l >= WARM_MIB) {
                        setFont(getFont().deriveFont(Font.BOLD));
                        setForeground(WARM);
                    }
                } else {
                    super.setValue(value);
                }
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(sizeRenderer);

        // Center "Source" (Fresh/Cache)
        DefaultTableCellRenderer srcRenderer = new DefaultTableCellRenderer() {
            { setHorizontalAlignment(SwingConstants.CENTER); }
            @Override protected void setValue(Object value) {
                setText(Objects.toString(value, ""));
                setForeground(FG);
            }
        };
        table.getColumnModel().getColumn(3).setCellRenderer(srcRenderer);

        JScrollPane scroll = new JScrollPane(table);

        // ===== BOTTOM: status bar =====
        JPanel bottom = new JPanel(new BorderLayout(10,0));
        status = new JLabel("Idle");
        progress = new JProgressBar(0,100); progress.setStringPainted(true);
        bottom.setBorder(new EmptyBorder(6,10,6,10));
        bottom.add(status, BorderLayout.WEST);
        bottom.add(progress, BorderLayout.CENTER);

        // Layout
        frame.add(leftTabs, BorderLayout.WEST);
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        // Theme everything
        applyTheme(settings.theme);

        // actions
        browseBtn.addActionListener(a -> browse());
        listBtn.addActionListener(a -> executeList());
        calcBtn.addActionListener(a -> executeCalc());
        top5Btn.addActionListener(a -> executeTop5());
        deleteBtn.addActionListener(a -> deleteSelected());
        upBtn.addActionListener(a -> goUp());
        refreshBtn.addActionListener(a -> { scanner.invalidate(Path.of(pathField.getText())); executeList(); });
        clearCacheBtn.addActionListener(a -> { scanner.clearCache(); setStatus("Cache cleared"); updateDriveInfo(); });

        // drive selector behavior: update path to root + drive info
        drivesCombo.addActionListener(e -> {
            File root = (File) drivesCombo.getSelectedItem();
            if (root != null) {
                pathField.setText(root.getAbsolutePath());
                executeList();
            }
            updateDriveInfo();
        });

        // initial drive info
        updateDriveInfo();

        frame.setVisible(true);
    }

    // ---- actions (EDT wrappers call background tasks) ----
    private void executeList() {
        Path p = Path.of(pathField.getText());
        if (!Files.isDirectory(p)) { msg("Invalid folder path!", JOptionPane.ERROR_MESSAGE); return; }
        cancelRunning();

        File[] files = p.toFile().listFiles();
        int total = (files == null) ? 0 : files.length;
        setProgressMax(total);
        final int totalCount = total; // for lambda

        cancelFlag = new AtomicBoolean(false);
        setStatus("Listing...");
        currentTask = CompletableFuture.runAsync(() -> {
            try {
                List<Item> items = scanner.listFolderContents(p, cancelFlag);
                SwingUtilities.invokeLater(() -> model.setItems(items));
                if (totalCount > 0) setProgressDone(totalCount);
                setStatus("Done");
            } catch (Exception e) {
                setStatus("Failed");
            } finally {
                updateDriveInfo(); // refresh free space view
            }
        });
    }

    private void executeCalc() {
        Path p = Path.of(pathField.getText());
        if (!Files.isDirectory(p)) { msg("Invalid folder path!", JOptionPane.ERROR_MESSAGE); return; }
        cancelRunning();

        int total = 0;
        try {
            File[] arr = p.toFile().listFiles(File::isDirectory);
            total = (arr == null) ? 0 : arr.length;
        } catch (Exception ignored) {}
        setProgressMax(total);
        final int totalCount = total; // for lambda

        cancelFlag = new AtomicBoolean(false);
        setStatus("Calculating...");
        currentTask = CompletableFuture.runAsync(() -> {
            try {
                List<Item> items = scanner.listFoldersAndSizes(p, cancelFlag);
                SwingUtilities.invokeLater(() -> model.setItems(items));
                if (totalCount > 0) setProgressDone(totalCount);
                setStatus("Done");
            } catch (Exception e) {
                setStatus("Failed");
            } finally {
                updateDriveInfo();
            }
        });
    }

    private void executeTop5() {
        File root = (File) drivesCombo.getSelectedItem();
        if (root == null) { msg("No drive selected.", JOptionPane.ERROR_MESSAGE); return; }
        cancelRunning();
        cancelFlag = new AtomicBoolean(false);
        setStatus("Scanning top 5…");
        setProgressIndeterminate(true);
        currentTask = CompletableFuture.runAsync(() -> {
            try {
                List<Item> items = scanner.topKLargestFoldersInDrive(root.toPath(), 5, cancelFlag);
                items.sort(Comparator.comparingLong((Item i)->i.sizeBytes).reversed());
                SwingUtilities.invokeLater(() -> model.setItems(items));
                setStatus("Done");
            } catch (Exception e) {
                setStatus("Failed");
            } finally {
                setProgressIndeterminate(false);
                updateDriveInfo();
            }
        });
    }

    private void deleteSelected() {
        int[] rows = table.getSelectedRows();
        if (rows==null || rows.length==0) { msg("Select one or more rows to delete.", JOptionPane.INFORMATION_MESSAGE); return; }

        List<Item> targets = new ArrayList<>(rows.length);
        for (int vr : rows) {
            int mr = table.convertRowIndexToModel(vr);
            targets.add(model.getItem(mr));
        }

        StringBuilder preview = new StringBuilder();
        int max = Math.min(10, targets.size());
        for (int i=0;i<max;i++) preview.append("• ").append(targets.get(i).path).append("\n");
        if (targets.size() > max) preview.append("… and ").append(targets.size()-max).append(" more");

        int opt = JOptionPane.showConfirmDialog(frame,
                "Delete the following " + targets.size() + " item(s)?\n\n" + preview,
                "Confirm Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opt != JOptionPane.OK_OPTION) return;

        cancelRunning();

        setProgressMax(targets.size());
        AtomicInteger counter = new AtomicInteger(0);
        setStatus("Deleting…");
        CompletableFuture.runAsync(() -> {
            for (Item it : targets) {
                boolean deleted = false;

                if (!settings.alwaysPermanentDelete) {
                    deleted = scanner.delete(it.path, false);
                }

                if (!deleted) {
                    boolean proceed = true;
                    if (settings.confirmPermanentDelete) {
                        int perm = JOptionPane.showConfirmDialog(
                                frame,
                                "Permanently delete?\n\n" + it.path,
                                "Permanent Delete",
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.ERROR_MESSAGE
                        );
                        proceed = (perm == JOptionPane.OK_OPTION);
                    }
                    if (proceed) {
                        scanner.delete(it.path, true);
                    }
                }

                incProgress(counter, targets.size());
                updateDriveInfo(); // live free-space update after each item
            }
        }).whenComplete((v,t)->{
            setStatus("Done");
            executeList();
        });
    }

    private void goUp() {
        try {
            Path current = Path.of(pathField.getText()).toAbsolutePath().normalize();
            Path parent = current.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                pathField.setText(parent.toString());
                executeList();
            } else msg("No parent directory.", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) { msg("Invalid path.", JOptionPane.ERROR_MESSAGE); }
    }

    // ---- util UI helpers ----
    private void browse() {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (ch.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(ch.getSelectedFile().getAbsolutePath());
            executeList();
        }
    }

    private void setStatus(String s){ SwingUtilities.invokeLater(() -> status.setText(s)); }

    // Progress helpers
    private void setProgressMax(int max){
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(false);
            progress.setMaximum(Math.max(1, max));
            progress.setValue(0);
            progress.setString("0 / " + Math.max(1, max));
        });
    }
    private void incProgress(AtomicInteger counter, int max){
        int v = counter.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            progress.setValue(v);
            progress.setString(v + " / " + max);
        });
    }
    private void setProgressDone(int max){
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(false);
            progress.setMaximum(Math.max(1, max));
            progress.setValue(Math.max(1, max));
            progress.setString(max + " / " + max);
        });
    }
    private void setProgressIndeterminate(boolean on){
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(on);
            progress.setString(on ? "Working…" : "");
        });
    }

    private void cancelRunning(){
        Future<?> f = currentTask; if (f!=null) f.cancel(true);
        AtomicBoolean cf = cancelFlag; if (cf!=null) cf.set(true);
    }
    private void msg(String m, int type){ JOptionPane.showMessageDialog(frame, m, "Info", type); }

    // ---- DRIVE INFO ----
    private void updateDriveInfo() {
        SwingUtilities.invokeLater(() -> {
            File selected = (File) drivesCombo.getSelectedItem();
            if (selected == null) {
                driveInfo.setText(" ");
                return;
            }
            try {
                long free = selected.getFreeSpace();
                long total = selected.getTotalSpace();
                long used = total - free;
                int pct = (total > 0) ? (int)Math.round(used * 100.0 / total) : 0;
                driveInfo.setText(
                    selected.getPath() + "  —  Free: " + HumanSize.format(free) +
                    " / Total: " + HumanSize.format(total) + " (" + pct + "% used)"
                );
            } catch (Exception e) {
                driveInfo.setText(selected.getPath() + "  —  Space: n/a");
            }
        });
    }

    // ---- THEME UTILITIES ----
    private void applyTheme(Settings.Theme t){
        if (t == Settings.Theme.LIGHT) {
            BG = Color.white; FG = new Color(20,20,20);
            STRIPE = new Color(245,245,245);
            SEL_BG = new Color(33,150,243); SEL_FG = Color.white;
            GRID = new Color(225,225,225);
            HOT = new Color(176,0,32); WARM = new Color(178,98,0);
        } else {
            BG = new Color(20,24,28); FG = new Color(235,235,235);
            STRIPE = new Color(28,32,36);
            SEL_BG = new Color(66,139,202); SEL_FG = Color.white;
            GRID = new Color(50,55,60);
            HOT = new Color(255,99,99); WARM = new Color(255,170,86);
        }

        UIManager.put("TitledBorder.titleColor", FG); // border titles follow FG

        if (frame != null) {
            frame.getContentPane().setBackground(BG);
            rethemeTree(frame.getContentPane());
            frame.repaint();
        }
    }

    private void rethemeTree(Component c) {
        if (c == null) return;

        // Base colors
        c.setBackground(BG);
        c.setForeground(FG);

        // Per-type fixes
        if (c instanceof JTable t) {
            t.setBackground(BG);
            t.setForeground(FG);
            t.setGridColor(GRID);
            t.setSelectionBackground(SEL_BG);
            t.setSelectionForeground(SEL_FG);
            if (t.getTableHeader() != null) {
                t.getTableHeader().setBackground(BG);
                t.getTableHeader().setForeground(FG);
            }
        } else if (c instanceof JScrollPane sp) {
            sp.getViewport().setBackground(BG);
            sp.setBackground(BG);
            if (sp.getVerticalScrollBar() != null) {
                sp.getVerticalScrollBar().setBackground(BG);
                sp.getVerticalScrollBar().setForeground(FG);
            }
            if (sp.getHorizontalScrollBar() != null) {
                sp.getHorizontalScrollBar().setBackground(BG);
                sp.getHorizontalScrollBar().setForeground(FG);
            }
        } else if (c instanceof JTabbedPane tp) {
            tp.setBackground(BG);
            tp.setForeground(FG);
            tp.setOpaque(true);
        } else if (c instanceof JLabel l) {
            l.setBackground(BG);
            l.setForeground(FG);
        } else if (c instanceof JButton b) {
            Color btn = new Color(
                Math.max(0, (int)(BG.getRed()*0.95)),
                Math.max(0, (int)(BG.getGreen()*0.95)),
                Math.max(0, (int)(BG.getBlue()*0.95))
            );
            b.setBackground(btn);
            b.setForeground(FG);
            b.setOpaque(true);
            b.setBorderPainted(true);
            b.setFocusPainted(false);
        } else if (c instanceof JTextField tf) {
            tf.setBackground(BG.darker());
            tf.setForeground(FG);
            tf.setCaretColor(FG);
            tf.setSelectionColor(SEL_BG);
            tf.setSelectedTextColor(SEL_FG);
        } else if (c instanceof JComboBox<?> cb) {
            cb.setBackground(BG.darker());
            cb.setForeground(FG);
        } else if (c instanceof JProgressBar pb) {
            pb.setBackground(BG);
            pb.setForeground(SEL_BG);
            pb.setStringPainted(true);
        } else if (c instanceof JSeparator sep) {
            sep.setForeground(GRID);
            sep.setBackground(GRID);
        } else if (c instanceof JComponent jc) {
            var border = jc.getBorder();
            if (border instanceof javax.swing.border.TitledBorder tb) {
                tb.setTitleColor(FG);
                var inner = tb.getBorder();
                if (!(inner instanceof javax.swing.border.LineBorder)) {
                    tb.setBorder(new javax.swing.border.LineBorder(GRID));
                }
            }
        }

        // Recurse
        if (c instanceof Container p) {
            for (Component child : p.getComponents()) {
                rethemeTree(child);
            }
        }
    }
}

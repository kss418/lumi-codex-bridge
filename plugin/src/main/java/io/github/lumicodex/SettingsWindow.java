package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class SettingsWindow extends JFrame {
    private static final String DEFAULT = "Codex 기본값 사용";
    private final PluginContext context;
    private final JComboBox<ModelCatalog.Model> models = new JComboBox<>();
    private final JComboBox<String> efforts = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final JButton save = new JButton("저장");
    private final JButton refresh = new JButton("목록 새로고침");
    private boolean updating;
    private boolean loaded;
    private ModelCatalog catalog;
    private SwingWorker<List<ModelCatalog.Model>, Void> worker;

    public SettingsWindow(PluginContext context) {
        super("Lumi Codex 설정");
        this.context = context;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        models.addActionListener(event -> { if (!updating) updateEfforts(""); });
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6); c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("모델"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(models, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; form.add(new JLabel("추론 강도"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(efforts, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        form.add(new JLabel("저장한 선택은 다음 실행에도 유지됩니다."), c);
        c.gridy = 3; form.add(status, c);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("닫기");
        close.addActionListener(event -> dispose());
        save.addActionListener(event -> saveSelection());
        refresh.addActionListener(event -> refreshModels());
        buttons.add(refresh); buttons.add(save); buttons.add(close);
        c.gridy = 4; form.add(buttons, c);
        setContentPane(form); setMinimumSize(new Dimension(580, 260)); pack(); setLocationRelativeTo(null);
        refreshModels();
    }

    private void refreshModels() {
        if (worker != null && !worker.isDone()) return;
        ModelCatalog.Model selected = (ModelCatalog.Model) models.getSelectedItem();
        String preferredModel = selected == null ? context.prefs().get("model", "") : selected.id();
        String preferredEffort = selected == null ? context.prefs().get("effort", "") : selectedEffort();
        loaded = false;
        save.setEnabled(false); models.setEnabled(false); efforts.setEnabled(false); refresh.setEnabled(false);
        status.setText("Codex에서 모델 목록을 조회하고 있습니다…");
        catalog = new ModelCatalog();
        ModelCatalog current = catalog;
        worker = new SwingWorker<>() {
            @Override protected List<ModelCatalog.Model> doInBackground() throws Exception {
                return current.fetch(line -> context.log().info(line));
            }
            @Override protected void done() {
                if (!isDisplayable() || isCancelled()) return;
                refresh.setEnabled(true);
                try {
                    List<ModelCatalog.Model> list = get();
                    updating = true;
                    models.removeAllItems();
                    models.addItem(new ModelCatalog.Model("", DEFAULT, List.of(), true));
                    list.forEach(models::addItem);
                    boolean found = false;
                    for (int i=0; i<models.getItemCount(); i++) {
                        if (models.getItemAt(i).id().equals(preferredModel)) { models.setSelectedIndex(i); found=true; break; }
                    }
                    if (!found) {
                        models.addItem(new ModelCatalog.Model(preferredModel, preferredModel + " (사용 불가)", List.of(), false));
                        models.setSelectedIndex(models.getItemCount()-1);
                    }
                    updating = false; loaded = true; models.setEnabled(true);
                    updateEfforts(preferredEffort);
                    if (!found) status.setText("이전에 선택한 모델을 사용할 수 없습니다. 다시 선택해 주세요.");
                } catch (CancellationException ignored) {
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    status.setText("조회 실패 — 저장된 설정은 유지됩니다. 다시 시도해 주세요.");
                    status.setToolTipText(cause.getMessage());
                    context.log().warning(cause.toString());
                    refresh.setText("다시 시도");
                }
            }
        };
        worker.execute();
    }

    private String selectedEffort() {
        return efforts.getSelectedIndex() <= 0 ? "" : (String) efforts.getSelectedItem();
    }
    private void updateEfforts(String preferred) {
        ModelCatalog.Model model = (ModelCatalog.Model) models.getSelectedItem();
        if (model == null) return;
        efforts.removeAllItems(); efforts.addItem(DEFAULT);
        model.efforts().forEach(efforts::addItem);
        save.setEnabled(loaded && model.available());
        efforts.setEnabled(loaded && !model.efforts().isEmpty());
        status.setText(" "); refresh.setText("목록 새로고침");
        if (!preferred.isEmpty()) {
            if (model.efforts().contains(preferred)) efforts.setSelectedItem(preferred);
            else status.setText("저장된 추론 강도를 지원하지 않습니다. 다시 선택해 주세요.");
        }
    }
    private void saveSelection() {
        ModelCatalog.Model model = (ModelCatalog.Model) models.getSelectedItem();
        if (!loaded || model == null || !model.available()) return;
        context.prefs().set("model", model.id()); context.prefs().set("effort", selectedEffort()); context.prefs().save();
        status.setText("모델과 추론 강도를 저장했습니다.");
    }
    @Override public void dispose() {
        if (catalog != null) catalog.close();
        if (worker != null) worker.cancel(true);
        super.dispose();
    }
}

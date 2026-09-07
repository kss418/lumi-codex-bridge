package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class ModelSettingsPanel extends JPanel implements AutoCloseable {
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

    public ModelSettingsPanel(PluginContext context) {
        super(new BorderLayout());
        this.context = context;

        models.setPreferredSize(new Dimension(320,models.getPreferredSize().height));
        efforts.setPreferredSize(new Dimension(320,efforts.getPreferredSize().height));
        models.addActionListener(event -> { if (!updating) updateEfforts(""); });
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6); c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("모델"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.NONE; form.add(models, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; form.add(new JLabel("추론 강도"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.NONE; form.add(efforts, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(new JLabel("저장한 선택은 다음 실행에도 유지됩니다."), c);
        c.gridy = 3; form.add(status, c);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        save.addActionListener(event -> saveSelection());
        refresh.addActionListener(event -> refreshModels());
        buttons.add(refresh); buttons.add(save);
        c.gridy = 4; form.add(buttons, c);
        ScreenWatchSettings screenSettings=ScreenWatchSettings.load(context.prefs());
        JCheckBox automatic=new JCheckBox("자동 화면 같이 보기",screenSettings.enabled());
        JSpinner interval=new JSpinner(new SpinnerNumberModel(screenSettings.intervalSeconds(),ScreenWatchSettings.MIN_SECONDS,ScreenWatchSettings.MAX_SECONDS,1));
        interval.setEditor(new JSpinner.NumberEditor(interval,"0"));
        JPanel autoRow=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        autoRow.add(automatic); autoRow.add(new JLabel("간격")); autoRow.add(interval); autoRow.add(new JLabel("초 (10~3600)"));
        c.gridy=5; c.gridwidth=2; c.fill=GridBagConstraints.HORIZONTAL; form.add(new JSeparator(),c);
        c.gridy=6; form.add(autoRow,c);
        c.gridy=7; form.add(new JLabel("켜면 주기적으로 보이는 화면을 Codex로 전송합니다."),c);
        JLabel autoStatus=new JLabel("기본은 꺼짐이며, 첫 촬영은 설정한 간격 후에 시작합니다.");
        c.gridy=8; form.add(autoStatus,c);
        JButton saveAuto=new JButton("자동 화면 설정 저장");
        saveAuto.addActionListener(event -> {
            try {
                interval.commitEdit();
                new ScreenWatchSettings(automatic.isSelected(),((Number)interval.getValue()).intValue()).save(context.prefs());
                autoStatus.setText("저장했습니다. 재시작 후에도 유지됩니다.");
            } catch(Exception error) { autoStatus.setText("간격은 10~3600초 사이의 정수로 입력해 주세요."); }
        });
        c.gridy=9; c.anchor=GridBagConstraints.EAST; c.fill=GridBagConstraints.NONE; form.add(saveAuto,c);
        SelfTalkSettings selfSettings=SelfTalkSettings.load(context.prefs());
        JCheckBox selfEnabled=new JCheckBox("AI 자동 혼잣말",selfSettings.enabled());
        JSpinner selfInterval=new JSpinner(new SpinnerNumberModel(selfSettings.intervalSeconds(),SelfTalkSettings.MIN_SECONDS,SelfTalkSettings.MAX_SECONDS,1));
        selfInterval.setEditor(new JSpinner.NumberEditor(selfInterval,"0"));
        JPanel selfRow=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));selfRow.add(selfEnabled);selfRow.add(new JLabel("간격"));selfRow.add(selfInterval);selfRow.add(new JLabel("초 (30~3600)"));
        c.gridy=10;c.anchor=GridBagConstraints.WEST;c.fill=GridBagConstraints.HORIZONTAL;form.add(new JSeparator(),c);
        c.gridy=11;form.add(selfRow,c);
        JLabel selfStatus=new JLabel("기본 꺼짐 · 화면 캡처 없이 말을 생성하며 Codex 사용량이 적용됩니다.");
        c.gridy=12;form.add(selfStatus,c);
        JButton selfSave=new JButton("혼잣말 설정 저장");
        selfSave.addActionListener(event->{try{selfInterval.commitEdit();new SelfTalkSettings(selfEnabled.isSelected(),((Number)selfInterval.getValue()).intValue()).save(context.prefs());selfStatus.setText("저장했습니다. 설정한 간격 후부터 한가할 때 먼저 말을 겁니다.");}catch(Exception error){selfStatus.setText("간격은 30~3600초 사이의 정수로 입력해 주세요.");}});
        c.gridy=13;c.anchor=GridBagConstraints.EAST;c.fill=GridBagConstraints.NONE;form.add(selfSave,c);
        add(form,BorderLayout.NORTH);
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
    @Override public void close() {
        if (catalog != null) catalog.close();
        if (worker != null) worker.cancel(true);

    }
}

package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.Json;
import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 모델과 추론 강도만 선택하고 플러그인 전용 설정에 저장합니다. */
public final class SettingsWindow extends JFrame {
    record Model(String id, String label, List<String> efforts) {
        @Override public String toString() { return label; }
    }
    private final PluginContext context;
    private final JComboBox<Model> models = new JComboBox<>();
    private final JComboBox<String> efforts = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final JButton save = new JButton("저장");
    private static final String DEFAULT = "Codex 기본값 사용";

    public SettingsWindow(PluginContext context) throws IOException {
        super("Lumi Codex 설정");
        this.context = context;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        models.addItem(new Model("", DEFAULT, List.of()));
        for (Model model : loadModels()) models.addItem(model);
        models.addActionListener(event -> updateEfforts(""));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("모델"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(models, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        form.add(new JLabel("추론 강도"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(efforts, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        form.add(new JLabel("저장한 선택은 다음 실행에도 유지됩니다."), c);
        c.gridy = 3; form.add(status, c);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("닫기");
        close.addActionListener(event -> dispose());
        save.addActionListener(event -> saveSelection());
        buttons.add(save); buttons.add(close);
        c.gridy = 4; form.add(buttons, c);
        setContentPane(form);
        setMinimumSize(new Dimension(510, 250));
        pack();
        setLocationRelativeTo(null);

        String savedModel = context.prefs().get("model", "");
        String savedEffort = context.prefs().get("effort", "");
        boolean found = false;
        for (int i = 0; i < models.getItemCount(); i++) {
            if (models.getItemAt(i).id().equals(savedModel)) {
                models.setSelectedIndex(i); found = true; break;
            }
        }
        if (!found) {
            models.addItem(new Model(savedModel, savedModel + " (현재 목록에 없음)", List.of()));
            models.setSelectedIndex(models.getItemCount() - 1);
        }
        updateEfforts(savedEffort);
        if (!found) status.setText("저장된 모델을 찾을 수 없습니다. 다른 모델을 선택해 주세요.");
    }

    static List<Model> loadModels() throws IOException {
        try (var stream = SettingsWindow.class.getResourceAsStream("/models.json")) {
            if (stream == null) throw new IOException("모델 목록 파일이 없습니다.");
            Object parsed = Json.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (!(parsed instanceof List<?> list)) throw new IOException("모델 목록 형식이 잘못되었습니다.");
            List<Model> result = new ArrayList<>();
            for (Object item : list) {
                List<String> levels = new ArrayList<>();
                Object supported = Json.get(item, "supportedReasoningEfforts");
                if (supported instanceof List<?> entries) {
                    for (Object entry : entries) levels.add(Json.getString(entry, "reasoningEffort"));
                }
                result.add(new Model(Json.getString(item, "model"), Json.getString(item, "displayName"), List.copyOf(levels)));
            }
            return result;
        }
    }

    private void updateEfforts(String preferred) {
        Model model = (Model) models.getSelectedItem();
        efforts.removeAllItems();
        efforts.addItem(DEFAULT);
        for (String level : model.efforts()) efforts.addItem(level);
        boolean valid = model.id().isEmpty() || !model.efforts().isEmpty();
        save.setEnabled(valid);
        efforts.setEnabled(!model.efforts().isEmpty());
        status.setText(" ");
        if (!preferred.isEmpty()) {
            if (model.efforts().contains(preferred)) efforts.setSelectedItem(preferred);
            else status.setText("저장된 추론 강도를 지원하지 않습니다. 다시 선택해 주세요.");
        }
    }

    private void saveSelection() {
        Model model = (Model) models.getSelectedItem();
        String effort = efforts.getSelectedIndex() == 0 ? "" : (String) efforts.getSelectedItem();
        context.prefs().set("model", model.id());
        context.prefs().set("effort", effort);
        context.prefs().save();
        status.setText("모델과 추론 강도를 저장했습니다.");
    }
}

package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.LumiPlugin;
import com.group_finity.mascot.lumi.plugin.PluginContext;
import com.group_finity.mascot.lumi.plugin.PluginUi;
import javax.swing.JOptionPane;

public final class CodexPlugin implements LumiPlugin {
    public static final String ID = "lumi.codex";
    private PluginContext context;
    private SettingsWindow window;
    private PluginUi.MenuHandle trayItem;
    private PluginUi.MenuHandle settingsButton;

    @Override
    public void start(PluginContext context) {
        this.context = context;
        trayItem = context.addTrayItem("Codex 모델 설정", this::openSettings);
        settingsButton = context.addSettingsButton("Codex 모델 설정", this::openSettings);
        context.log().info("Lumi Codex settings plugin started.");
    }

    private void openSettings() {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            try {
                if (window == null || !window.isDisplayable()) window = new SettingsWindow(active);
                window.setVisible(true);
                window.toFront();
            } catch (Exception error) {
                active.log().warning(error.toString());
                JOptionPane.showMessageDialog(null, error.getMessage(), "설정창 오류", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @Override
    public void stop() {
        PluginContext active = context;
        context = null;
        if (active == null) return;
        if (trayItem != null) trayItem.remove();
        if (settingsButton != null) settingsButton.remove();
        active.onEdt(() -> { if (window != null) { window.dispose(); window = null; } });
        active.log().info("Lumi Codex settings plugin stopped.");
    }
}

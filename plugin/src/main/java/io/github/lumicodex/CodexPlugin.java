package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.LumiPlugin;
import com.group_finity.mascot.lumi.plugin.PluginContext;
import com.group_finity.mascot.lumi.plugin.PluginUi;
import javax.swing.JOptionPane;

public final class CodexPlugin implements LumiPlugin {
    public static final String ID = "lumi.codex";
    private PluginContext context;
    private SettingsWindow window;
    private LocalTtsService voice;
    private ExternalSpeech externalSpeech;
    private javax.swing.Timer screenWatchTimer;
    private AutoScreenWatch screenWatch;
    private final java.util.Map<Integer, ChatWindow> chats = new java.util.HashMap<>();
    private PluginUi.MenuHandle trayItem;
    private PluginUi.MenuHandle settingsButton;
    private PluginUi.MenuHandle chatItem;
    private PluginUi.MenuHandle cancelItem;
    private PluginUi.MenuHandle desktopItem;

    @Override
    public void start(PluginContext context) {
        this.context = context;
        voice = new LocalTtsService(context);
        externalSpeech = new ExternalSpeech(context,voice::speak);
        trayItem = context.addTrayItem("Lumi Codex 설정", this::openSettings);
        settingsButton = context.addSettingsButton("Lumi Codex 설정", this::openSettings);
        chatItem = context.addCharacterMenuItem("대화하기", context::isCharacter,
                this::openChat);
        cancelItem = context.addCharacterMenuItem("생성 취소", context::isCharacter, (imageSet, mascotId) -> {
            PluginContext active = this.context;
            if (active != null) active.onEdt(() -> {
                ChatWindow chat = chats.get(mascotId);
                if (chat != null) chat.cancelGeneration();
            });
        });
        cancelItem.setEnabled(false);
        desktopItem = context.addCharacterMenuItem("화면 같이 보기", context::isCharacter, this::inspectDesktop);
        context.onEdt(() -> {
            if(this.context!=context) return;
            screenWatch=new AutoScreenWatch(() -> ScreenWatchSettings.load(context.prefs()),
                    () -> !context.focusActive() && !context.charactersHidden() && !context.bubbleVisible()
                            && !context.mascots().isEmpty()
                            && chats.values().stream().noneMatch(chat -> chat.isBusy() || chat.isVisible()),
                    () -> {
                        // One character/monitor per interval, preferring Lumi.
                        var candidates=context.mascots().stream().filter(m -> context.isCharacter(m.imageSet())).toList();
                        if(candidates.isEmpty()) return;
                        var target=candidates.stream().filter(m -> "Lumi".equalsIgnoreCase(m.imageSet())).findFirst().orElse(candidates.getFirst());
                        inspectDesktop(target.imageSet(),target.id(),true);
                    });
            screenWatchTimer=new javax.swing.Timer(1000,event -> {
                if(this.context!=context) return;
                try { screenWatch.tick(); } catch(Exception error) { context.log().warning(error.toString()); }
            });
            screenWatchTimer.start();
        });
        context.log().info("Lumi Codex settings plugin started.");
    }

    private void refreshCancelMenu() {
        if (cancelItem != null && !cancelItem.removed())
            cancelItem.setEnabled(chats.values().stream().anyMatch(ChatWindow::canCancel));
    }

    private void inspectDesktop(String imageSet, Integer mascotId) { inspectDesktop(imageSet, mascotId, false); }

    private void inspectDesktop(String imageSet, Integer mascotId, boolean automatic) {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            ChatWindow chat = chats.get(mascotId);
            if (chat == null || !chat.isDisplayable()) {
                chat = new ChatWindow(active, imageSet, mascotId, this::openSettings, this::refreshCancelMenu, voice);
                chats.put(mascotId, chat);
            }
            chat.inspectDesktop(automatic);
        });
    }

    private void openChat(String imageSet, Integer mascotId) {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            ChatWindow chat = chats.get(mascotId);
            if (chat == null || !chat.isDisplayable()) {
                chat = new ChatWindow(active, imageSet, mascotId, this::openSettings, this::refreshCancelMenu, voice);
                chats.put(mascotId, chat);
            }
            chat.showNearMascot();
        });
    }

    private void openSettings() {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            try {
                if (window == null || !window.isDisplayable()) window = new SettingsWindow(active,voice);
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
        if (chatItem != null) chatItem.remove();
        if (cancelItem != null) cancelItem.remove();
        if (desktopItem != null) desktopItem.remove();
        if (externalSpeech != null) externalSpeech.close();
        if (voice != null) voice.close();
        active.onEdt(() -> { if(screenWatchTimer!=null) screenWatchTimer.stop(); if(screenWatch!=null) screenWatch.stop(); chats.values().forEach(ChatWindow::dispose); chats.clear(); if (window != null) { window.dispose(); window = null; } });
        active.log().info("Lumi Codex settings plugin stopped.");
    }
}







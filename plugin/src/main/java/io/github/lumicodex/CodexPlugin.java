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
    private TtsSettingsWindow ttsWindow;
    private PluginUi.MenuHandle ttsItem;
    private javax.swing.Timer screenWatchTimer;
    private AutoScreenWatch screenWatch;
    private final java.util.Map<Integer, ChatWindow> chats = new java.util.HashMap<>();
    private PluginUi.MenuHandle trayItem;
    private PluginUi.MenuHandle settingsButton;
    private PluginUi.MenuHandle chatItem;
    private PluginUi.MenuHandle cancelItem;
    private PluginUi.MenuHandle desktopItem;
    private PluginUi.MenuHandle personaItem;
    private final java.util.Map<String, PersonaWindow> personas = new java.util.HashMap<>();

    @Override
    public void start(PluginContext context) {
        this.context = context;
        voice = new LocalTtsService(context);
        externalSpeech = new ExternalSpeech(context,voice::speak);
        trayItem = context.addTrayItem("Codex 모델 설정", this::openSettings);
        personaItem = context.addTrayItem("페르소나 설정", this::choosePersona);
        ttsItem = context.addTrayItem("TTS 설정", this::openTtsSettings);
        settingsButton = context.addSettingsButton("Codex 모델 설정", this::openSettings);
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
                        inspectDesktop(target.imageSet(),target.id());
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

    private void inspectDesktop(String imageSet, Integer mascotId) {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            ChatWindow chat = chats.get(mascotId);
            if (chat == null || !chat.isDisplayable()) {
                chat = new ChatWindow(active, imageSet, mascotId, this::openSettings, this::refreshCancelMenu, voice);
                chats.put(mascotId, chat);
            }
            chat.inspectDesktop();
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

    private void choosePersona() {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            java.util.List<String> characters = new java.util.ArrayList<>(active.activeImageSets());
            if (characters.isEmpty()) characters.add("Lumi");
            String selected = characters.getFirst();
            if (characters.size() > 1) {
                Object choice = JOptionPane.showInputDialog(null,
                        "페르소나를 설정할 캐릭터를 선택해 주세요.",
                        "페르소나 설정", JOptionPane.PLAIN_MESSAGE,
                        null, characters.toArray(), selected);
                if (choice == null) return;
                selected = choice.toString();
            }
            openPersona(selected);
        });
    }

    private void openPersona(String imageSet) {
        PluginContext active = context;
        if (active == null) return;
        active.onEdt(() -> {
            if (context != active) return;
            try {
                PersonaWindow editor = personas.get(imageSet);
                if (editor == null || !editor.isDisplayable()) {
                    editor = new PersonaWindow(active, imageSet);
                    personas.put(imageSet, editor);
                }
                editor.setVisible(true); editor.toFront();
            } catch (Exception error) {
                JOptionPane.showMessageDialog(null, error.getMessage(), "페르소나 설정 오류", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openTtsSettings() {
        PluginContext active=context;
        if(active==null)return;
        active.onEdt(() -> {
            if(context!=active)return;
            if(ttsWindow==null || !ttsWindow.isDisplayable())ttsWindow=new TtsSettingsWindow(active,voice);
            ttsWindow.setVisible(true);ttsWindow.toFront();
        });
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
        if (chatItem != null) chatItem.remove();
        if (cancelItem != null) cancelItem.remove();
        if (desktopItem != null) desktopItem.remove();
        if (personaItem != null) personaItem.remove();
        if (ttsItem != null) ttsItem.remove();
        if (externalSpeech != null) externalSpeech.close();
        if (voice != null) voice.close();
        active.onEdt(() -> { if(ttsWindow!=null) ttsWindow.dispose(); if(screenWatchTimer!=null) screenWatchTimer.stop(); if(screenWatch!=null) screenWatch.stop(); personas.values().forEach(PersonaWindow::dispose); personas.clear(); chats.values().forEach(ChatWindow::dispose); chats.clear(); if (window != null) { window.dispose(); window = null; } });
        active.log().info("Lumi Codex settings plugin stopped.");
    }
}







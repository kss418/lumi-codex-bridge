package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.CancellationException;

/** 원본처럼 캐릭터 근처에 표시하고 전송 뒤 숨기는 작은 입력창입니다. */
public final class ChatWindow extends JDialog {
    private final PluginContext context;
    private final LocalTtsService voice;
    private final String imageSet;
    private final int mascotId;
    private ChatBridge bridge;
    private String selectedModel;
    private String selectedEffort;
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("보내기");
    private final JLabel status = new JLabel("Enter로 전송 · Esc로 닫기");
    private SwingWorker<String,Void> worker;
    private boolean everFocused;
    private boolean disposed;
    private boolean cancelPending;
    private final Runnable onStateChanged;

    public ChatWindow(PluginContext context, String imageSet, int mascotId, Runnable openSettings, Runnable onStateChanged, LocalTtsService voice) {
        super((Frame)null, false);
        this.context=context; this.imageSet=imageSet; this.mascotId=mascotId; this.onStateChanged=onStateChanged; this.voice=voice;
        setUndecorated(true); setAlwaysOnTop(true); setDefaultCloseOperation(HIDE_ON_CLOSE);
        JPanel panel=new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(context.theme().line()), BorderFactory.createEmptyBorder(8,10,8,10)));
        context.theme().colour(panel, () -> context.theme().cardBackground());
        JLabel title=new JLabel("대화하기");
        context.theme().colour(title, () -> context.theme().inkSoft());
        JButton settings=new JButton("설정"); settings.setFocusable(false);
        settings.addActionListener(event -> { setVisible(false); openSettings.run(); });
        input.setFont(context.theme().font("",18f)); input.setPreferredSize(new Dimension(260,34));
        context.theme().style(input,"arc: 999; margin: 3,12,3,12");
        Dimension buttonSize=new Dimension(Math.max(90,Math.max(settings.getPreferredSize().width,send.getPreferredSize().width)),34);
        settings.setPreferredSize(buttonSize); send.setPreferredSize(buttonSize);
        send.setFocusable(false); send.setEnabled(false);

        // Both rows share the same columns, with exactly one 8px gutter.
        GridBagConstraints c=new GridBagConstraints();
        c.fill=GridBagConstraints.BOTH; c.anchor=GridBagConstraints.WEST;
        c.gridx=0; c.gridy=0; c.weightx=1; c.insets=new Insets(0,0,6,8);
        panel.add(title,c);
        c.gridx=1; c.weightx=0; c.insets=new Insets(0,0,6,0);
        panel.add(settings,c);
        c.gridx=0; c.gridy=1; c.weightx=1; c.insets=new Insets(0,0,6,8);
        panel.add(input,c);
        c.gridx=1; c.weightx=0; c.insets=new Insets(0,0,6,0);
        panel.add(send,c);
        context.theme().colour(status, () -> context.theme().inkFaint());
        c.gridx=0; c.gridy=2; c.gridwidth=2; c.weightx=1; c.insets=new Insets(0,0,0,0);
        panel.add(status,c);
        setContentPane(panel); pack();
        try { setShape(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),18,18)); } catch (UnsupportedOperationException ignored) { }
        send.addActionListener(event -> { if (worker!=null && !worker.isDone()) cancelGeneration(); else submit(); }); input.addActionListener(event -> submit());
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { syncSend(); }
            public void removeUpdate(DocumentEvent event) { syncSend(); }
            public void changedUpdate(DocumentEvent event) { syncSend(); }
        });
        input.addInputMethodListener(new InputMethodListener() {
            public void inputMethodTextChanged(InputMethodEvent event) { repaint(); }
            public void caretPositionChanged(InputMethodEvent event) { repaint(); }
        });
        getRootPane().registerKeyboardAction(event -> setVisible(false), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent event) { everFocused=true; }
            public void windowLostFocus(WindowEvent event) { if(everFocused) setVisible(false); }
        });
    }

    private void syncSend() {
        boolean busy=worker!=null && !worker.isDone();
        send.setText(busy ? "생성 취소" : "보내기");
        send.setEnabled(busy ? !cancelPending : !input.getText().isBlank());
        onStateChanged.run();
    }

    public boolean isBusy() { return !disposed && worker!=null && !worker.isDone(); }

    public boolean canCancel() { return !disposed && !cancelPending && worker!=null && !worker.isDone(); }

    public void cancelGeneration() {
        if (!canCancel()) return;
        cancelPending=true;
        onStateChanged.run();
        ChatBridge current=bridge;
        if(current==null) return;
        send.setEnabled(false); status.setText("취소하고 있습니다…");
        Thread.startVirtualThread(() -> {
            try { current.cancel(); }
            catch (Exception error) {
                context.log().warning(error.toString());
                SwingUtilities.invokeLater(() -> { if(!disposed) { status.setText("취소 요청에 실패했습니다."); cancelPending=false; syncSend(); } });
            }
        });
    }

    public void showNearMascot() {
        var mascot=context.mascotById(mascotId);
        Point anchor=mascot==null?null:mascot.anchor();
        GraphicsConfiguration gc=getGraphicsConfiguration();
        for(var device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if(anchor!=null && device.getDefaultConfiguration().getBounds().contains(anchor)) gc=device.getDefaultConfiguration();
        }
        Rectangle area=new Rectangle(gc.getBounds()); Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(gc);
        area.x+=insets.left; area.y+=insets.top; area.width-=insets.left+insets.right; area.height-=insets.top+insets.bottom;
        int x=anchor==null?area.x+(area.width-getWidth())/2:anchor.x-getWidth()/2;
        int y=anchor==null?area.y+(area.height-getHeight())/2:anchor.y-getHeight()-24;
        setLocation(Math.max(area.x,Math.min(x,area.x+area.width-getWidth())),Math.max(area.y,Math.min(y,area.y+area.height-getHeight())));
        syncSend();
        everFocused=false; setVisible(true); toFront();
        SwingUtilities.invokeLater(() -> input.requestFocusInWindow());
    }

    private void submit() { submit(input.getText().strip(), false); }

    public void selfTalk(){submit(SelfTalkSettings.prompt(),false,true);}

    public void inspectDesktop() { inspectDesktop(false); }

    public void inspectDesktop(boolean automatic) {
        submit(ScreenReaction.prompt(automatic), true, automatic);
    }

    private void submit(String text, boolean inspectScreen) { submit(text, inspectScreen, false); }

    private void submit(String text, boolean inspectScreen, boolean automatic) {
        if(text.isEmpty() || (worker!=null && !worker.isDone())) return;
        final String persona;
        final ConversationStore.Snapshot history;
        ConversationStore records=new ConversationStore(context);
        try { persona = new PersonaStore(context).effective(imageSet); history=records.snapshot(imageSet); }
        catch (Exception error) {
            status.setText("페르소나 또는 대화 기록을 읽지 못했습니다."); status.setToolTipText(error.getMessage());
            context.log().warning(error.toString()); return;
        }
        String model=context.prefs().get("model",""); String effort=context.prefs().get("effort","");
        if(bridge==null || !model.equals(selectedModel) || !effort.equals(selectedEffort)) {
            if(bridge!=null) bridge.close();
            bridge=new ChatBridge(line -> context.log().info(line)); selectedModel=model; selectedEffort=effort;
        }
        var mascot=context.mascotById(mascotId);
        Point captureAnchor=mascot==null?null:new Point(mascot.anchor());
        ChatBridge current=bridge;
        cancelPending=false;
        current.prepareTurn();
        input.setText(""); input.setEnabled(false); send.setEnabled(false); setVisible(false);
        status.setText("답변을 준비하고 있습니다…");
        // Screen replies wait for existing bubbles; a busy bubble here would block our own reply.
        if(!automatic && !inspectScreen) context.showBusyFor(mascotId,"생각 중…");
        worker=new SwingWorker<>() {
            protected String doInBackground() throws Exception {
                String image=inspectScreen?DesktopCapture.capture(captureAnchor):null;
                return current.chat(text,model,effort,persona,image,history);
            }
            protected void done() {
                if(disposed || isCancelled()) return;
                try {
                    String reply=ScreenReaction.visibleReply(get(),automatic);
                    if(automatic && !inspectScreen && (!SelfTalkSettings.load(context.prefs()).enabled() || context.focusActive() || context.charactersHidden())) {
                        input.setEnabled(true);syncSend();return;
                    }
                    // Show voiced replies when playback starts; text-only replies remain immediate.
                    if(!reply.isBlank() && records.current(imageSet,history.session())) {
                        try {records.append(imageSet,history.session(),inspectScreen?"[화면 같이 보기]":automatic?"[AI 자동 혼잣말]":text,reply);}
                        catch(java.io.IOException error){context.log().warning("대화 기록 저장 실패: "+error);status.setToolTipText("답변은 생성했지만 기록 저장에 실패했습니다.");}
                        if(inspectScreen || automatic)voice.screenReply(reply,imageSet,mascotId);
                        else voice.reply(reply,imageSet,mascotId);
                    }
                    status.setText("Enter로 전송 · Esc로 닫기");
                } catch(CancellationException ignored) {
                } catch(Exception error) {
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    if (cause instanceof ChatBridge.Cancelled) {
                        status.setText("생성을 취소했습니다.");
                        if(context.mascotById(mascotId)!=null) context.sayTo(mascotId,imageSet,"생성을 취소했어요.",3000L);
                        input.setEnabled(true); syncSend(); return;
                    }
                    context.log().warning(cause.toString()); current.close(); bridge=null;
                    status.setText("연결에 실패했습니다. 다시 보내 주세요."); status.setToolTipText(cause.getMessage());
                    if(!automatic && context.mascotById(mascotId)!=null) context.sayTo(mascotId,imageSet,"답변을 받지 못했어요. 다시 말 걸어 주세요.",8000L);
                }
                input.setEnabled(true); syncSend();
            }
        };
        worker.execute();
        syncSend();
    }

    @Override public void dispose() {
        disposed=true;
        if(bridge!=null) bridge.close();
        if(worker!=null) worker.cancel(true);
        super.dispose();
        onStateChanged.run();
    }
}



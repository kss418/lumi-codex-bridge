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

    public ChatWindow(PluginContext context, String imageSet, int mascotId, Runnable openSettings) {
        super((Frame)null, false);
        this.context=context; this.imageSet=imageSet; this.mascotId=mascotId;
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
        Dimension buttonSize=new Dimension(Math.max(76,Math.max(settings.getPreferredSize().width,send.getPreferredSize().width)),34);
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
        send.addActionListener(event -> submit()); input.addActionListener(event -> submit());
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

    private void syncSend() { send.setEnabled(!input.getText().isBlank() && (worker==null || worker.isDone())); }

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
        everFocused=false; setVisible(true); toFront();
        SwingUtilities.invokeLater(() -> input.requestFocusInWindow());
    }

    private void submit() {
        String text=input.getText().strip();
        if(text.isEmpty() || (worker!=null && !worker.isDone())) return;
        String model=context.prefs().get("model",""); String effort=context.prefs().get("effort","");
        if(bridge==null || !model.equals(selectedModel) || !effort.equals(selectedEffort)) {
            if(bridge!=null) bridge.close();
            bridge=new ChatBridge(line -> context.log().info(line)); selectedModel=model; selectedEffort=effort;
        }
        ChatBridge current=bridge;
        input.setText(""); input.setEnabled(false); send.setEnabled(false); setVisible(false);
        status.setText("답변을 준비하고 있습니다…");
        context.showBusyFor(mascotId,"생각 중…");
        worker=new SwingWorker<>() {
            protected String doInBackground() throws Exception { return current.chat(text,model,effort); }
            protected void done() {
                if(disposed || isCancelled()) return;
                try {
                    String reply=get();
                    if(context.mascotById(mascotId)!=null) context.sayTo(mascotId,imageSet,reply,15000L);
                    status.setText("Enter로 전송 · Esc로 닫기");
                } catch(CancellationException ignored) {
                } catch(Exception error) {
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    context.log().warning(cause.toString()); current.close(); bridge=null;
                    status.setText("연결에 실패했습니다. 다시 보내 주세요."); status.setToolTipText(cause.getMessage());
                    if(context.mascotById(mascotId)!=null) context.sayTo(mascotId,imageSet,"답변을 받지 못했어요. 다시 말 걸어 주세요.",8000L);
                }
                input.setEnabled(true); syncSend();
            }
        };
        worker.execute();
    }

    @Override public void dispose() {
        disposed=true;
        if(bridge!=null) bridge.close();
        if(worker!=null) worker.cancel(true);
        super.dispose();
    }
}



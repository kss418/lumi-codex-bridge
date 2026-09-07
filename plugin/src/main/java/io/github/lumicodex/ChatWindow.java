package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.CancellationException;

public final class ChatWindow extends JFrame {
    private final PluginContext context;
    private final String imageSet;
    private final int mascotId;
    private final ChatBridge bridge;
    private final String selectedModel;
    private final String selectedEffort;
    private final JTextArea history = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("보내기");
    private final JLabel status = new JLabel("메시지를 입력해 주세요. 창을 닫으면 대화가 종료됩니다.");
    private SwingWorker<String,Void> worker;

    public ChatWindow(PluginContext context, String imageSet, int mascotId) {
        super("꼬미와 대화 — " + imageSet);
        this.context=context; this.imageSet=imageSet; this.mascotId=mascotId;
        selectedModel=context.prefs().get("model", "");
        selectedEffort=context.prefs().get("effort", "");
        bridge=new ChatBridge(line -> context.log().info(line));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        history.setEditable(false); history.setLineWrap(true); history.setWrapStyleWord(true);
        JPanel panel=new JPanel(new BorderLayout(8,8));
        panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        panel.add(new JScrollPane(history),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(6,6));
        bottom.add(status,BorderLayout.NORTH); bottom.add(input,BorderLayout.CENTER); bottom.add(send,BorderLayout.EAST);
        panel.add(bottom,BorderLayout.SOUTH); setContentPane(panel);
        send.addActionListener(event -> submit()); input.addActionListener(event -> submit());
        setSize(540,380); setLocationRelativeTo(null);
    }

    private void submit() {
        String text=input.getText().strip();
        if(text.isEmpty() || (worker!=null && !worker.isDone())) return;
        String model=selectedModel;
        String effort=selectedEffort;
        input.setText(""); input.setEnabled(false); send.setEnabled(false);
        history.append("나: " + text + "\n\n"); status.setText("꼬미가 답변을 준비하고 있습니다…");
        worker=new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception { return bridge.chat(text,model,effort); }
            @Override protected void done() {
                if(!isDisplayable() || isCancelled()) return;
                try {
                    String reply=get();
                    history.append("꼬미: " + reply + "\n\n");
                    if(context.mascotById(mascotId)!=null) context.sayTo(mascotId,imageSet,reply,15000L);
                    status.setText("답변을 받았습니다."); input.setEnabled(true); send.setEnabled(true); input.requestFocusInWindow();
                } catch(CancellationException ignored) {
                } catch(Exception error) {
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    history.append("오류: " + cause.getMessage() + "\n\n");
                    status.setText("창을 닫고 다시 열어 주세요.");
                    context.log().warning(cause.toString());
                }
                history.setCaretPosition(history.getDocument().getLength());
            }
        };
        worker.execute();
    }

    @Override public void dispose() {
        bridge.close();
        if(worker!=null) worker.cancel(true);
        super.dispose();
    }
}

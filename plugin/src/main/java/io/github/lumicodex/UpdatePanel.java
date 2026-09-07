package io.github.lumicodex;

import javax.swing.*;
import java.awt.*;

public final class UpdatePanel extends JPanel {
    private SwingWorker<ReleaseUpdate,Void> worker;
    private SwingWorker<Void,String> installer;
    private boolean closed;
    public UpdatePanel(String current) {
        super(new BorderLayout(6,6));
        JLabel status=new JLabel("현재 버전 "+current);
        JButton check=new JButton("업데이트 확인");
        JButton download=new JButton("업데이트 설치");download.setVisible(false);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));buttons.add(check);buttons.add(download);
        add(status,BorderLayout.NORTH);add(buttons,BorderLayout.CENTER);
        add(new JLabel("설치 버튼을 누르면 꼬미가 종료되고, 업데이트 후 다시 실행됩니다."),BorderLayout.SOUTH);
        final ReleaseUpdate[] latest={null};
        check.addActionListener(event -> {
            check.setEnabled(false);download.setVisible(false);status.setText("업데이트 확인 중…");
            worker=new SwingWorker<>() {
                protected ReleaseUpdate doInBackground() throws Exception {return ReleaseUpdate.latest();}
                protected void done() {
                    if(isCancelled())return;
                    check.setEnabled(true);
                    try {
                        latest[0]=get();boolean newer=latest[0].newerThan(current);
                        status.setText("현재 "+current+" · 최신 "+latest[0].version()+(newer?" — 업데이트할 수 있습니다.":" — 최신 버전입니다."));
                        download.setVisible(newer);
                    }catch(Exception error){status.setText("확인 실패. 다시 시도해 주세요.");status.setToolTipText(error.getCause()==null?error.getMessage():error.getCause().getMessage());}
                    revalidate();
                }
            };
            worker.execute();
        });
        download.addActionListener(event -> {
            if(latest[0]==null)return;
            download.setEnabled(false);check.setEnabled(false);status.setText("업데이트 준비 중…");
            ReleaseUpdate selected=latest[0];
            installer=new SwingWorker<>() {
                protected Void doInBackground()throws Exception {
                    var folder=UpdateInstaller.prepare(selected,this::publish);
                    if(isCancelled())return null;
                    UpdateInstaller.launch(folder);
                    return null;
                }
                protected void process(java.util.List<String> messages){if(!closed)status.setText(messages.getLast());}
                protected void done(){
                    if(isCancelled() || closed)return;
                    try {get();com.group_finity.mascot.Main.getInstance().exit();}
                    catch(Exception error){download.setEnabled(true);check.setEnabled(true);status.setText("업데이트 준비 실패. 꼬미는 종료하지 않았습니다.");status.setToolTipText(error.getCause()==null?error.getMessage():error.getCause().getMessage());}
                }
            };
            installer.execute();
        });
    }
    public void close(){closed=true;if(worker!=null)worker.cancel(true);if(installer!=null)installer.cancel(true);}
}

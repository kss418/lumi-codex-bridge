package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import com.group_finity.mascot.lumi.plugin.Json;
import javax.swing.*;
import java.awt.*;

public final class TtsSettingsWindow extends JFrame {
    public TtsSettingsWindow(PluginContext context,LocalTtsService voice) {
        super("루미 로컬 TTS 설정");setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JCheckBox enabled=new JCheckBox("TTS 켜기",voice.enabled());
        JComboBox<String> device=new JComboBox<>(new String[]{"auto","cpu","cuda"});device.setSelectedItem(context.prefs().get("tts.device","auto"));
        JSlider volume=new JSlider(0,100,context.prefs().getInt("tts.volume",80));
        JLabel status=new JLabel(voice.installed()?"설치됨 — 켜면 다음 답변부터 읽습니다.":"미설치 — 설치 버튼을 눌렀을 때만 다운로드합니다.");
        JProgressBar progress=new JProgressBar(0,100);progress.setStringPainted(true);progress.setString("대기 중");
        JTextArea log=new JTextArea(8,48);log.setEditable(false);log.setLineWrap(true);
        JButton install=new JButton("로컬 TTS 설치");install.setEnabled(!voice.installed());
        JButton preview=new JButton("미리듣기");preview.setEnabled(voice.installed());
        JButton save=new JButton("설정 저장");JButton stop=new JButton("재생 중지");
        stop.setEnabled(voice.canStop());
        Timer playbackState=new Timer(200,event -> stop.setEnabled(voice.canStop()));
        playbackState.start();
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel fields=new JPanel(new GridLayout(0,1,4,4));fields.add(enabled);
        JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT));row.add(new JLabel("실행 장치"));row.add(device);row.add(new JLabel("음량"));row.add(volume);fields.add(row);
        fields.add(new JLabel("설치 시 약 6~9GB 다운로드 · 여유 공간 30GB 필요"));
        fields.add(new JLabel("한국어 루미 보이스팩 필요 · 모델은 로컬에만 설치됩니다."));fields.add(status);fields.add(progress);
        panel.add(fields,BorderLayout.NORTH);panel.add(new JScrollPane(log),BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(install);buttons.add(preview);buttons.add(stop);buttons.add(save);panel.add(buttons,BorderLayout.SOUTH);
        save.addActionListener(event -> {try{voice.settings(enabled.isSelected(),(String)device.getSelectedItem(),volume.getValue());status.setText("저장했습니다. 끄면 재생과 모델을 정리합니다.");}catch(Exception error){status.setText(error.getMessage());}});
        stop.addActionListener(event -> {voice.stop();stop.setEnabled(false);});
        preview.addActionListener(event -> {
            try {
                preview.setEnabled(false);
                status.setText("미리듣기 준비 중입니다. 첫 실행은 시간이 걸릴 수 있습니다.");
                voice.preview((String)device.getSelectedItem(),volume.getValue(),message -> {
                    status.setText(message);preview.setEnabled(voice.installed());stop.setEnabled(voice.canStop());
                });
                stop.setEnabled(voice.canStop());
            } catch(Exception error) {status.setText(error.getMessage());preview.setEnabled(voice.installed());stop.setEnabled(voice.canStop());}
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) {playbackState.stop();voice.endPreviewSession();}
        });
        install.addActionListener(event -> {
            progress.setIndeterminate(true);progress.setString("설치 준비 중");
            install.setEnabled(false);status.setText("설치 중입니다. 창을 닫아도 앱이 켜져 있으면 계속 설치합니다.");
            new SwingWorker<Void,String>() {
                protected Void doInBackground() throws Exception {voice.install(this::publish);return null;}
                protected void process(java.util.List<String> lines){
                    for(String line:lines) {
                        try {
                            Object message=Json.parse(line);
                            if("error".equals(Json.getString(message,"event"))) {
                                log.append("오류: "+Json.getString(message,"message")+"\n");
                                continue;
                            }
                            if("install_progress".equals(Json.getString(message,"event"))) {
                                String file=Json.getString(message,"file");
                                file=switch(file) {
                                    case "legacy.7z" -> "GPT-SoVITS 실행 환경";
                                    case "rtx50.7z" -> "RTX 50용 실행 환경";
                                    case "lumi.7z" -> "루미 음성 모델";
                                    case "7zr.exe" -> "압축 해제 도구";
                                    case "VOICE_MODEL_NOTICE.txt" -> "음성 모델 이용 안내";
                                    default -> file;
                                };
                                String phase=Json.getString(message,"phase");
                                long bytes=Json.get(message,"bytes") instanceof Number n?n.longValue():0;
                                long total=Json.get(message,"total") instanceof Number n?n.longValue():0;
                                if("download".equals(phase)) {
                                    progress.setIndeterminate(total<=0);
                                    String downloaded=String.format(java.util.Locale.ROOT,"%.1f MiB",bytes/1048576.0);
                                    if(total>0) {
                                        int percent=(int)Math.min(100,bytes*100.0/total);
                                        progress.setValue(percent);
                                        progress.setString(String.format(java.util.Locale.ROOT,"%s / %.1f MiB (%d%%)",downloaded,total/1048576.0,percent));
                                    } else progress.setString(downloaded+" 다운로드됨");
                                    status.setText(file+" 다운로드 중");
                                } else {
                                    String label=switch(phase) {
                                        case "verify" -> "파일 검증 중";
                                        case "extract" -> "압축 해제 중";
                                        case "cached" -> "기존 파일 확인 완료";
                                        case "download_done" -> "다운로드 완료";
                                        default -> "다운로드 준비 중";
                                    };
                                    progress.setIndeterminate(!"cached".equals(phase) && !"download_done".equals(phase));
                                    if(!progress.isIndeterminate())progress.setValue(100);
                                    progress.setString(label);status.setText(file+" — "+label);
                                    log.append(file+" — "+label+"\n");
                                }
                                continue;
                            }
                        } catch(RuntimeException ignored) { }
                        log.append(line+"\n");
                    }
                    if(log.getDocument().getLength()>20000)log.setText(log.getText().substring(log.getText().length()-10000));
                    log.setCaretPosition(log.getDocument().getLength());
                }
                protected void done(){progress.setIndeterminate(false);try{get();preview.setEnabled(voice.installed());progress.setValue(100);progress.setString("설치 완료");status.setText("설치 완료. TTS 켜기를 선택하고 설정을 저장하세요.");}catch(Exception error){progress.setString("설치 실패");status.setText("설치 실패. 로그를 확인하고 다시 시도하세요.");install.setEnabled(true);log.append("오류: "+(error.getCause()==null?error.getMessage():error.getCause().getMessage())+"\n");}}
            }.execute();
        });
        setContentPane(panel);pack();setLocationRelativeTo(null);
    }
}
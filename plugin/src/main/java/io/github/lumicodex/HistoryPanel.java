package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public final class HistoryPanel extends JPanel {
    public HistoryPanel(PluginContext context,LocalTtsService voice) {
        super(new BorderLayout(8,8));setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        ConversationStore store=new ConversationStore(context);
        var characters=new LinkedHashSet<>(context.installedImageSets());characters.addAll(context.activeImageSets());if(characters.isEmpty())characters.add("Lumi");
        JComboBox<String> choice=new JComboBox<>(characters.toArray(String[]::new));
        choice.setPreferredSize(new Dimension(200,choice.getPreferredSize().height));
        JPanel characterRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));characterRow.add(choice);
        JTextArea text=new JTextArea(18,40);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
        JLabel status=new JLabel("참고는 새 대화 연결 시 최대 40,000자 · 화면 이미지 제외");
        JPanel top=new JPanel(new BorderLayout(6,6));top.add(characterRow,BorderLayout.NORTH);top.add(status,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        HistorySettings limits=HistorySettings.load(context.prefs());
        JSpinner stored=new JSpinner(new SpinnerNumberModel(limits.stored(),1,50,1));
        JSpinner referenced=new JSpinner(new SpinnerNumberModel(limits.referenced(),1,limits.stored(),1));
        stored.addChangeListener(event->{int max=((Number)stored.getValue()).intValue();if(((Number)referenced.getValue()).intValue()>max)referenced.setValue(max);((SpinnerNumberModel)referenced.getModel()).setMaximum(max);});
        JButton saveLimits=new JButton("횟수 저장");
        JPanel settings=new JPanel(new GridLayout(0,1,4,4));
        JPanel counts=new JPanel(new FlowLayout(FlowLayout.LEFT));
        counts.add(new JLabel("저장"));counts.add(stored);counts.add(new JLabel("회 · 참고"));counts.add(referenced);counts.add(new JLabel("회"));counts.add(saveLimits);
        settings.add(counts);settings.add(new JLabel("모든 캐릭터에 적용 · 저장 횟수를 줄이면 오래된 기록이 삭제됩니다."));
        top.add(settings,BorderLayout.CENTER);
        add(new JScrollPane(text),BorderLayout.CENTER);
        Runnable refresh=()->{try{text.setText(store.display((String)choice.getSelectedItem()));text.setCaretPosition(text.getDocument().getLength());}catch(Exception error){status.setText("기록을 읽지 못했습니다.");status.setToolTipText(error.getMessage());}};
        saveLimits.addActionListener(event->{try{
            stored.commitEdit();referenced.commitEdit();
            store.settings(new HistorySettings(((Number)stored.getValue()).intValue(),((Number)referenced.getValue()).intValue()));
            refresh.run();status.setText("횟수를 저장했습니다. 참고 횟수는 다음 대화 연결부터 적용됩니다.");
        }catch(Exception error){status.setText(error.getMessage());}});
        JButton reload=new JButton("새로고침"),start=new JButton("새 대화 시작"),delete=new JButton("기록 삭제");
        reload.addActionListener(event->refresh.run());choice.addActionListener(event->refresh.run());
        start.addActionListener(event->{try{store.reset((String)choice.getSelectedItem(),false);voice.stop();refresh.run();status.setText("새 대화를 시작합니다. 이전 기록은 보관됩니다.");}catch(Exception error){status.setText(error.getMessage());}});
        delete.addActionListener(event->{
            if(JOptionPane.showConfirmDialog(this,"이 캐릭터의 저장된 대화 기록을 모두 삭제할까요?","기록 삭제",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try{store.reset((String)choice.getSelectedItem(),true);voice.stop();refresh.run();status.setText("기록을 삭제하고 새 대화를 시작합니다.");}catch(Exception error){status.setText(error.getMessage());}
        });
        putClientProperty("actionAnchor",delete);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(reload);buttons.add(start);buttons.add(delete);add(buttons,BorderLayout.SOUTH);refresh.run();
    }
}

package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.util.*;

/** One settings window; tab switches preserve unsaved edits. */
public final class SettingsWindow extends JFrame {
    private final ModelSettingsPanel model;
    private final TtsSettingsPanel tts;
    private final UpdatePanel updates;
    public SettingsWindow(PluginContext context,LocalTtsService voice) {
        super("Lumi Codex 설정");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImage(context.appIcon());
        JPanel root=new JPanel(new BorderLayout(0,10));
        root.setBorder(BorderFactory.createEmptyBorder(14,12,12,12));
        JLabel title=new JLabel("Lumi Codex",SwingConstants.CENTER);
        title.setFont(context.theme().font("",20f));
        context.theme().colour(title,()->context.theme().inkSoft());
        context.theme().colour(root,()->context.theme().cardBackground());
        root.add(title,BorderLayout.NORTH);
        model=new ModelSettingsPanel(context);tts=new TtsSettingsPanel(context,voice);
        JTabbedPane tabs=new JTabbedPane(JTabbedPane.TOP);
        tabs.addTab("모델 설정",scroll(model));
        tabs.addTab("페르소나 설정",personas(context));
        tabs.addTab("TTS 설정",scroll(tts));
        tabs.addTab("대화 기록",new HistoryPanel(context,voice));
        updates=new UpdatePanel(context.descriptor().version);
        JPanel updatePage=new JPanel(new BorderLayout());
        updatePage.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        updatePage.add(updates,BorderLayout.NORTH);
        tabs.addTab("업데이트",scroll(updatePage));
        root.add(tabs,BorderLayout.CENTER);
        JButton close=new JButton("닫기");close.addActionListener(event->dispose());
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT));footer.add(close);root.add(footer,BorderLayout.SOUTH);
        setContentPane(root);setMinimumSize(new Dimension(680,580));setSize(760,720);setLocationRelativeTo(null);
    }
    private static JScrollPane scroll(JComponent panel) {
        JScrollPane scroll=new JScrollPane(panel);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);return scroll;
    }
    private static JPanel personas(PluginContext context) {
        JPanel panel=new JPanel(new BorderLayout(8,8));
        var characters=new LinkedHashSet<>(context.activeImageSets());
        if(characters.isEmpty())characters.add("Lumi");
        JComboBox<String> choice=new JComboBox<>(characters.toArray(String[]::new));
        JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT));row.add(new JLabel("캐릭터"));row.add(choice);panel.add(row,BorderLayout.NORTH);
        CardLayout layout=new CardLayout();JPanel cards=new JPanel(layout);Set<String> loaded=new HashSet<>();
        Runnable select=()->{
            String character=(String)choice.getSelectedItem();
            if(character==null)return;
            if(!loaded.contains(character)) {
                try {cards.add(new PersonaPanel(context,character),character);loaded.add(character);}
                catch(Exception error){JOptionPane.showMessageDialog(panel,error.getMessage(),"페르소나 설정 오류",JOptionPane.ERROR_MESSAGE);return;}
            }
            layout.show(cards,character);
        };
        choice.addActionListener(event->select.run());select.run();
        panel.add(cards,BorderLayout.CENTER);return panel;
    }
    @Override public void dispose(){
        if(model!=null)model.close();if(tts!=null)tts.close();if(updates!=null)updates.close();super.dispose();
    }
}

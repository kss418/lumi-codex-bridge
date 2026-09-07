package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class PersonaPanel extends JPanel {
    public PersonaPanel(PluginContext context,String character) throws IOException {
        super(new BorderLayout());
        PersonaStore store=new PersonaStore(context);
        JTextArea text=new JTextArea(store.effective(character),12,40);
        text.setLineWrap(true); text.setWrapStyleWord(true);
        JLabel status=new JLabel("저장하면 다음 메시지부터 새 대화에 적용됩니다. (최대 20,000자)");
        JPanel panel=new JPanel(new BorderLayout(8,8)); panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        panel.add(status,BorderLayout.NORTH); panel.add(new JScrollPane(text),BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset=new JButton("캐릭터 기본값 사용"); JButton save=new JButton("저장");
        save.addActionListener(event -> {
            try { store.save(character,text.getText()); status.setText("저장했습니다. 다음 메시지부터 적용됩니다."); }
            catch(IOException error) { JOptionPane.showMessageDialog(this,error.getMessage(),"저장 오류",JOptionPane.ERROR_MESSAGE); }
        });
        reset.addActionListener(event -> {
            try { store.reset(character); text.setText(store.defaultPersona(character)); status.setText("캐릭터 기본 페르소나를 사용합니다."); }
            catch(IOException error) { JOptionPane.showMessageDialog(this,error.getMessage(),"설정 오류",JOptionPane.ERROR_MESSAGE); }
        });
        buttons.add(reset); buttons.add(save); panel.add(buttons,BorderLayout.SOUTH);
        add(panel,BorderLayout.CENTER);
    }
}

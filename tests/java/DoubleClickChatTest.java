import io.github.lumicodex.DoubleClickChat;
import com.group_finity.mascot.lumi.plugin.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.ArrayList;
public class DoubleClickChatTest {
 static void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.doubleclick.test")) {
   kit.mascots(new MascotState(1,"Lumi",new Rectangle(0,0,80,80),new Point(40,80),true,"idle"),new MascotState(2,"Lumi",new Rectangle(100,0,80,80),new Point(140,80),true,"idle"));
   kit.installedImageSets(java.util.List.of("Lumi"));
   var calls=new ArrayList<Integer>();var handler=new DoubleClickChat(kit.context(),(name,id)->calls.add(id));
   kit.publish("click","mascotId",1);flush();check(calls.isEmpty());
   kit.publish("doubleclick","mascotId",2,"imageSet","incorrect");flush();check(calls.equals(java.util.List.of(2)));
   kit.publish("doubleclick","mascotId",999);kit.publish("doubleclick");flush();check(calls.size()==1);
   handler.close();kit.publish("doubleclick","mascotId",1);flush();check(calls.size()==1);
  }
  System.out.println("PASS: double-click routing to exact mascot, single click ignored, missing target and unsubscribe (no UI)");
 }
}

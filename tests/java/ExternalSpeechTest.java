import io.github.lumicodex.ExternalSpeech;
import com.group_finity.mascot.lumi.plugin.*;
import java.awt.*;
import java.util.*;
import javax.swing.SwingUtilities;
public class ExternalSpeechTest {
 static void check(boolean c,String label){if(!c)throw new AssertionError(label);}
 static void flush() throws Exception {SwingUtilities.invokeAndWait(()->{});}
 public static void main(String[] args) throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.external.test")) {
   kit.mascots(new MascotState(1,"Lumi",new Rectangle(0,0,80,80),new Point(40,80),true,"idle"));
   kit.installedImageSets(java.util.List.of("Lumi"));
   var calls=new ArrayList<String>();
   var handler=new ExternalSpeech(kit.context(),(text,name,id)->calls.add(text+"/"+name+"/"+id));
   kit.publish("say_file","text","hello");flush();check(calls.isEmpty(),"default off");
   kit.context().prefs().set("tts.enabled",true);kit.context().prefs().set("tts.externalLines",false); // legacy flag must be ignored
   kit.publish("say_file","text","작업 완료","mascotId",1);flush();check(calls.size()==1,"external event");
   check(calls.getFirst().equals("작업 완료/Lumi/1"),"target and text");
   kit.publish("chat","text","do not repeat");flush();check(calls.size()==1,"normal chat not repeated");
   kit.publish("say_file","text"," ");flush();check(calls.size()==1,"blank ignored");
   kit.context().prefs().set("tts.enabled",false);kit.publish("say_file","text","off");flush();check(calls.size()==1,"toggle off");
   kit.context().prefs().set("tts.enabled",true);kit.focus(true,"test");kit.publish("say_file","text","quiet");flush();check(calls.size()==1,"focus mode");
   kit.focus(false,"");handler.close();kit.publish("say_file","text","closed");flush();check(calls.size()==1,"unsubscribed");
  }
  System.out.println("PASS: external speech routing, toggles, no duplicate normal chat, focus, unsubscribe; no audio playback");
 }
}
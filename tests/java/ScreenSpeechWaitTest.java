import io.github.lumicodex.*;
import com.group_finity.mascot.lumi.plugin.*;
import javax.swing.SwingUtilities;
import javax.sound.sampled.AudioFormat;
import java.util.concurrent.*;
import java.util.*;
public class ScreenSpeechWaitTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
 public static void main(String[] args)throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.screen.wait.test")) {
   kit.mascots(new MascotState(1,"Lumi",new java.awt.Rectangle(0,0,80,80),new java.awt.Point(40,80),true,"idle"));
   kit.context().prefs().set("tts.enabled",true);
   List<String> spoken=new CopyOnWriteArrayList<>();
   CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
   try(var voice=TtsPreviewTest.create(kit.context(),true,text->{spoken.add(text);if(text.equals("혼잣말.")){started.countDown();release.await();}return new PcmAudio(new AudioFormat(16000,16,1,true,false),new byte[320],10);})) {
    voice.speak("혼잣말.","Lumi",1);check(started.await(2,TimeUnit.SECONDS),"self-talk synthesis");
    voice.screenReply("이전 화면.","Lumi",1);voice.screenReply("최신 화면.","Lumi",1);flush();
    check(kit.calls("stopSpeaking").isEmpty(),"screen response must not stop self-talk");
    kit.speaking(true);kit.bubbleVisible(true);release.countDown();
    Thread.sleep(1200);flush();check(spoken.size()==1,"wait for built-in voice and bubble");
    kit.speaking(false);kit.bubbleVisible(false);
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
    while(spoken.size()<2 && System.nanoTime()<deadline)Thread.sleep(20);
    check(spoken.equals(List.of("혼잣말.","최신 화면.")),"preserve current speech, then latest screen only");
   }
   kit.context().prefs().set("tts.enabled",false);kit.clearCalls();kit.speaking(true);
   try(var voice=TtsPreviewTest.create(kit.context(),true,text->{throw new AssertionError("TTS off");})) {
    voice.screenReply("대기 화면.","Lumi",1);flush();Thread.sleep(1100);check(kit.calls("say").isEmpty(),"text-only screen also waits");
    voice.stop();flush();kit.speaking(false);kit.bubbleVisible(false);Thread.sleep(1100);flush();
    check(kit.calls("say").isEmpty(),"cancel clears waiting screen");
   }
  }
  System.out.println("PASS: screen waits for speech/bubble, keeps latest observation, and clears on stop (mock audio)");
 }
}

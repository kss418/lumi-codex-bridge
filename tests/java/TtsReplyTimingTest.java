import io.github.lumicodex.*;
import com.group_finity.mascot.lumi.plugin.*;
import javax.sound.sampled.AudioFormat;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;

public class TtsReplyTimingTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void awaitBubble(PluginTestKit kit)throws Exception {
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
  while(kit.calls("say").isEmpty() && System.nanoTime()<end)Thread.sleep(10);
  SwingUtilities.invokeAndWait(()->{});
  check(kit.calls("say").size()==1,"exactly one initial reply");
 }
 public static void main(String[] args)throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.reply.timing.test")) {
   kit.mascots(new MascotState(1,"Lumi",new java.awt.Rectangle(0,0,80,80),new java.awt.Point(40,80),true,"idle"));
   kit.context().prefs().set("tts.enabled",true);
   var started=new CountDownLatch(1);var release=new CountDownLatch(1);
   try(var voice=TtsPreviewTest.create(kit.context(),true,text->{started.countDown();release.await();return new PcmAudio(new AudioFormat(16000,16,1,true,false),new byte[320],10);})) {
    voice.reply("안녕하세요.","Lumi",1);
    check(started.await(2,TimeUnit.SECONDS),"synthesis started");
    SwingUtilities.invokeAndWait(()->{});
    check(kit.calls("say").isEmpty(),"no reply during model loading");
    release.countDown();awaitBubble(kit);
    check(kit.calls("speak").size()==1,"audio starts with reply");
   }
   kit.clearCalls();
   try(var voice=TtsPreviewTest.create(kit.context(),true,text->{throw new java.io.IOException("mock synthesis failure");})) {
    voice.reply("실패해도 답변은 보여요.","Lumi",1);awaitBubble(kit);
    check(kit.calls("speak").isEmpty(),"fallback without audio");
   }
   kit.clearCalls();kit.context().prefs().set("tts.enabled",false);
   try(var voice=TtsPreviewTest.create(kit.context(),true,text->{throw new AssertionError("TTS off");})) {
    voice.reply("텍스트 답변이에요.","Lumi",1);awaitBubble(kit);
   }
  }
  System.out.println("PASS: delayed first display, audio start, failure fallback and TTS off (mock audio only)");
 }
}

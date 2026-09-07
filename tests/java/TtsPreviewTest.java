import io.github.lumicodex.*;
import com.group_finity.mascot.lumi.plugin.*;
import javax.sound.sampled.AudioFormat;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

public class TtsPreviewTest {
 static LocalTtsService create(PluginContext ctx,boolean installed,SentenceAudioQueue.Synth<PcmAudio> synth) throws Exception {
  var ctor=LocalTtsService.class.getDeclaredConstructor(PluginContext.class,BooleanSupplier.class,SentenceAudioQueue.Synth.class);
  ctor.setAccessible(true);return ctor.newInstance(ctx,(BooleanSupplier)()->installed,synth);
 }
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args) throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.preview.test")) {
   kit.context().prefs().set("tts.enabled",false);kit.context().prefs().set("tts.volume",45);kit.context().prefs().set("tts.device","auto");
   var produced=new AtomicReference<String>();var completed=new CountDownLatch(1);
   var format=new AudioFormat(16000,16,1,true,false);
   try(var voice=create(kit.context(),true,text->{produced.set(text);return new PcmAudio(format,new byte[320],10);})) {
    voice.preview("cpu",71,message->completed.countDown());
    check(completed.await(3,TimeUnit.SECONDS),"completion callback");
    check(LocalTtsService.PREVIEW_TEXT.equals(produced.get()),"sample text");
    check(!voice.enabled() && kit.context().prefs().getInt("tts.volume",0)==45 && kit.context().prefs().get("tts.device","").equals("auto"),"settings unchanged");
    check(!voice.previewing(),"preview completes");
    check(kit.calls("speak").size()==1,"plays without a mascot");
   }
   AtomicInteger synthCalls=new AtomicInteger();
   try(var voice=create(kit.context(),false,text->{synthCalls.incrementAndGet();return null;})) {
    try{voice.preview("auto",80,message->{});throw new AssertionError("missing install");}catch(IllegalStateException expected){}
    check(synthCalls.get()==0,"does not auto install or synthesize");
   }
   CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),stopped=new CountDownLatch(1);kit.clearCalls();
   try(var voice=create(kit.context(),true,text->{started.countDown();release.await();return new PcmAudio(format,new byte[320],10);})) {
    voice.preview("cpu",71,message->stopped.countDown());check(started.await(2,TimeUnit.SECONDS),"start");
    voice.stop();release.countDown();check(stopped.await(2,TimeUnit.SECONDS),"stop callback");
    SwingUtilities.invokeAndWait(()->{});check(!voice.previewing(),"stopped state");check(kit.calls("speak").isEmpty(),"no late playback");
   }
  }
  System.out.println("PASS: preview completion, unchanged settings, missing-install guard and stop (mock audio only)");
 }
}
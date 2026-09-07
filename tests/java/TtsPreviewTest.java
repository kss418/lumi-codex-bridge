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
    kit.setting("lumi.voice.volume","38");
    voice.preview("cpu",message->completed.countDown());
    check(completed.await(3,TimeUnit.SECONDS),"completion callback");
    check(LocalTtsService.PREVIEW_TEXT.equals(produced.get()),"sample text");
    check(kit.speechVolume()==38,"preview follows voice pack instead of legacy TTS volume");
    check(!voice.enabled() && kit.context().prefs().getInt("tts.volume",0)==45 && kit.context().prefs().get("tts.device","").equals("auto"),"settings unchanged");
    check(!voice.previewing(),"preview completes");
    check(!voice.canStop(),"stop disabled after completion");
    check(kit.calls("speak").size()==1,"plays without a mascot");
    String[] values={"0","72","150","-10","invalid"};
    int[] expected={0,72,100,0,100};
    for(int i=0;i<values.length;i++) {
     kit.setting("lumi.voice.volume",values[i]);
     CountDownLatch done=new CountDownLatch(1);
     voice.preview("cpu",message->done.countDown());
     check(done.await(3,TimeUnit.SECONDS),"updated preview completes");
     check(kit.speechVolume()==expected[i],"updated volume, mute, clamp and fallback");
    }
    kit.mascots(new MascotState(1,"Lumi",new java.awt.Rectangle(0,0,80,80),new java.awt.Point(40,80),true,"idle"));
    voice.settings(true,"cpu");kit.setting("lumi.voice.volume","23");kit.clearCalls();
    voice.speak("안녕하세요.","Lumi",1);
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
    while(kit.calls("speak").isEmpty() && System.nanoTime()<deadline)Thread.sleep(10);
    SwingUtilities.invokeAndWait(()->{});
    check(!kit.calls("speak").isEmpty() && kit.speechVolume()==23,"normal speech follows updated voice pack volume");
    check(kit.calls("mouthFlapFor").isEmpty() && kit.calls("mouthFlap").isEmpty(),"voiced reply does not trigger chirping Talk animation");
    check(!kit.calls("say").isEmpty(),"voiced reply retains bubble");
    voice.settings(false,"cpu");
   }
   AtomicInteger synthCalls=new AtomicInteger();
   try(var voice=create(kit.context(),false,text->{synthCalls.incrementAndGet();return null;})) {
    try{voice.preview("auto",message->{});throw new AssertionError("missing install");}catch(IllegalStateException expected){}
    check(synthCalls.get()==0,"does not auto install or synthesize");
   }
   CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),stopped=new CountDownLatch(1);kit.clearCalls();
   try(var voice=create(kit.context(),true,text->{started.countDown();release.await();return new PcmAudio(format,new byte[320],10);})) {
    voice.preview("cpu",message->stopped.countDown());check(started.await(2,TimeUnit.SECONDS),"start");
    check(voice.canStop(),"stop enabled during synthesis");
    voice.stop();check(!voice.canStop(),"stop disabled immediately");release.countDown();check(stopped.await(2,TimeUnit.SECONDS),"stop callback");
    SwingUtilities.invokeAndWait(()->{});check(!voice.previewing(),"stopped state");check(kit.calls("speak").isEmpty(),"no late playback");
   }
  }
  System.out.println("PASS: preview completion, unchanged settings, missing-install guard and stop (mock audio only)");
 }
}
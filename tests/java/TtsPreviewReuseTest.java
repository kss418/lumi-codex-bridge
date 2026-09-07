import io.github.lumicodex.*;
import com.group_finity.mascot.lumi.plugin.*;
import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public class TtsPreviewReuseTest {
 static final class FakeProcess extends Process {
  boolean alive=true;
  public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
  public InputStream getInputStream(){return InputStream.nullInputStream();}
  public InputStream getErrorStream(){return InputStream.nullInputStream();}
  public int waitFor(){return 0;}
  public int exitValue(){return 0;}
  public boolean isAlive(){return alive;}
  public void destroy(){alive=false;}
  public Process destroyForcibly(){alive=false;return this;}
  public Stream<ProcessHandle> descendants(){return Stream.empty();}
 }
 static void set(LocalTtsService voice,String name,Object value)throws Exception {var f=LocalTtsService.class.getDeclaredField(name);f.setAccessible(true);f.set(voice,value);}
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static void preview(LocalTtsService voice,String device)throws Exception {
  CountDownLatch done=new CountDownLatch(1);voice.preview(device,71,message->done.countDown());
  check(done.await(3,TimeUnit.SECONDS),"preview finished");
 }
 public static void main(String[] args)throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.preview.reuse.test")) {
   var ctor=LocalTtsService.class.getDeclaredConstructor(PluginContext.class,BooleanSupplier.class,SentenceAudioQueue.Synth.class);ctor.setAccessible(true);
   var format=new AudioFormat(16000,16,1,true,false);
   SentenceAudioQueue.Synth<PcmAudio> synth=text->new PcmAudio(format,new byte[320],10);
   try(var voice=ctor.newInstance(kit.context(),(BooleanSupplier)()->true,synth)) {
    FakeProcess first=new FakeProcess();set(voice,"process",first);set(voice,"loadedDevice","cpu");
    preview(voice,"cpu");check(first.alive,"first preview reuses model");
    preview(voice,"cpu");check(first.alive,"repeat preview retains model while TTS off");
    check(!voice.enabled(),"does not enable TTS");
    voice.endPreviewSession();check(!first.alive,"closing settings releases off-mode model");
    FakeProcess second=new FakeProcess();set(voice,"process",second);set(voice,"loadedDevice","cpu");
    preview(voice,"cuda");check(!second.alive,"device switch unloads previous model");
    FakeProcess third=new FakeProcess();set(voice,"process",third);set(voice,"loadedDevice","cpu");
    preview(voice,"cpu");voice.settings(false,"cpu",45);check(!third.alive,"explicit off unloads model");
   }
  }
  System.out.println("PASS: same-device reuse, off-state preservation, device switch and explicit shutdown (no GPU/audio)");
 }
}
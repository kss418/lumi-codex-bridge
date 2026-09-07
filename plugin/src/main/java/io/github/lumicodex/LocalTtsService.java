package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.*;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Optional voice service: neither construction nor status checking installs anything. */
public final class LocalTtsService implements AutoCloseable {
    private final PluginContext context;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"lumi-tts");t.setDaemon(true);return t;});
    private final ScheduledExecutorService idle=Executors.newSingleThreadScheduledExecutor(r -> {Thread t=new Thread(r,"lumi-tts-idle");t.setDaemon(true);return t;});
    private final AtomicLong generation=new AtomicLong();
    private volatile Process process;
    private volatile Process installer;
    private volatile boolean closed;
    private volatile boolean speaking;
    private volatile boolean synthesizing;
    private volatile long lastUsed=System.nanoTime();
    private BlockingQueue<Object> replies;
    private String loadedDevice;
    private long requestId;
    public LocalTtsService(PluginContext context) {
        this.context=context;
        idle.scheduleAtFixedRate(() -> {if(!enabled() || context.focusActive()) stop(); if(!synthesizing && !speaking && System.nanoTime()-lastUsed>TimeUnit.SECONDS.toNanos(120)) stopProcess();},1,1,TimeUnit.SECONDS);
    }
    public Path root() {return Path.of(System.getenv("LOCALAPPDATA"),"LumiCodex","tts");}
    private Path tools() throws Exception {return Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent().resolve("tools");}
    private Object config() throws IOException {return Json.parse(Files.readString(root().resolve("installed.json")));}
    public boolean installed() {
        try {Object c=config();for(String key:List.of("python","gpt","sovits","reference")) if(!Files.isRegularFile(Path.of(Json.getString(c,key)))) return false;return true;}
        catch(Exception error){return false;}
    }
    public boolean enabled(){return context.prefs().getBoolean("tts.enabled",false);}
    public void settings(boolean enabled,String device,int volume) {
        if(enabled && !installed()) throw new IllegalStateException("로컬 TTS를 먼저 설치해 주세요.");
        if(!List.of("auto","cpu","cuda").contains(device)) throw new IllegalArgumentException("지원하지 않는 실행 장치입니다.");
        context.prefs().set("tts.enabled",enabled);context.prefs().set("tts.device",device);context.prefs().set("tts.volume",Math.max(0,Math.min(volume,100)));context.prefs().save();
        if(!enabled || !Objects.equals(loadedDevice,device)) stop();
    }
    public void install(Consumer<String> progress) throws Exception {
        if(closed)throw new IOException("서비스가 종료되었습니다.");
        if(installer!=null && installer.isAlive())throw new IOException("설치가 이미 진행 중입니다.");
        Path tools=tools();String python=System.getenv("LUMI_CODEX_PYTHON");
        if(python==null || python.isBlank()) {
            Path runtime=tools.resolve("runtime.json");
            if(Files.isRegularFile(runtime))python=Json.getString(Json.parse(Files.readString(runtime)),"python");
        }
        if(python==null || !Files.isRegularFile(Path.of(python)))python="python";
        Path lumi=context.dataDir().toAbsolutePath().getParent().getParent().getParent();
        if(!Files.isRegularFile(lumi.resolve("app/Shimeji-ee.jar")))lumi=Path.of("C:/Program Files (x86)/Steam/steamapps/common/Little LUMI");
        ProcessBuilder builder=new ProcessBuilder(python,"-u",tools.resolve("local_tts.py").toString(),"--install","--root",root().toString(),"--lumi-home",lumi.toString());
        builder.environment().put("PYTHONUTF8","1");builder.redirectErrorStream(true);
        Process child=builder.start();installer=child;
        try(var reader=child.inputReader(StandardCharsets.UTF_8)) {for(String line;(line=reader.readLine())!=null;)progress.accept(line);}
        if(child.waitFor()!=0 || !installed())throw new IOException("TTS 설치에 실패했습니다. 설치 로그를 확인해 주세요.");
    }
    private Object receive(long seconds) throws Exception {
        Object item=replies.poll(seconds,TimeUnit.SECONDS);
        if(item==null)throw new IOException("TTS 응답 시간이 초과되었습니다.");
        if(item instanceof Exception error)throw error;
        return item;
    }
    private PcmAudio synthesize(String text) throws Exception {
        lastUsed=System.nanoTime();synthesizing=true;
        try {
            String device=context.prefs().get("tts.device","auto");
            if(process==null || !process.isAlive() || !device.equals(loadedDevice)) {
                stopProcess();Object c=config();
                ProcessBuilder builder=new ProcessBuilder(Json.getString(c,"python"),"-u",tools().resolve("local_tts.py").toString(),"--serve","--root",root().toString(),"--device",device);
                builder.environment().put("PYTHONUTF8","1");
                Process child=builder.start();process=child;loadedDevice=device;
                if(closed || !enabled()) {stopProcess();throw new IOException("TTS가 꺼져 있습니다.");}
                BlockingQueue<Object> inbox=new LinkedBlockingQueue<>();replies=inbox;
                Thread.startVirtualThread(() -> {
                    try(var reader=child.inputReader(StandardCharsets.UTF_8)){for(String line;(line=reader.readLine())!=null;)inbox.add(Json.parse(line));}
                    catch(Exception error){inbox.add(error);}finally{inbox.offer(new IOException("TTS 연결이 종료되었습니다."));}
                });
                Thread.startVirtualThread(() -> {try(var reader=child.errorReader(StandardCharsets.UTF_8)){for(String line;(line=reader.readLine())!=null;)context.log().info("[tts] "+line);}catch(IOException ignored){}});
                Object ready=receive(180);
                if(!"ready".equals(Json.getString(ready,"event")))throw new IOException(Objects.toString(Json.getString(ready,"message"),"TTS 초기화 실패"));
            }
            long id=++requestId;
            byte[] request=(Json.write(Map.of("id",id,"text",text))+"\n").getBytes(StandardCharsets.UTF_8);
            process.getOutputStream().write(request);process.getOutputStream().flush();
            Object response=receive(180);
            if(!(Json.get(response,"id") instanceof Number n) || n.longValue()!=id)throw new IOException("TTS 응답 ID 오류");
            if(Json.get(response,"error")!=null)throw new IOException(Json.getString(response,"error"));
            byte[] wav=Base64.getDecoder().decode(Json.getString(response,"wav"));
            try(AudioInputStream audio=AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
                AudioFormat format=audio.getFormat();byte[] pcm=audio.readAllBytes();
                return new PcmAudio(format,pcm,PcmAudio.millisOf(format,pcm.length));
            }
        } finally {synthesizing=false;lastUsed=System.nanoTime();}
    }
    public void speak(String text,String imageSet,int mascotId) {
        if(closed || !enabled() || context.focusActive() || text.isBlank())return;
        long ticket=generation.incrementAndGet();
        jobs.submit(() -> {
            if(closed || ticket!=generation.get() || !enabled())return;
            try {
                PcmAudio audio=synthesize(text);
                context.onEdt(() -> {
                    if(closed || ticket!=generation.get() || !enabled() || context.focusActive() || context.mascotById(mascotId)==null)return;
                    context.setSpeechVolume(context.prefs().getInt("tts.volume",80));
                    context.speak(audio,() -> context.onEdt(() -> {
                        speaking=true;context.sayTo(mascotId,imageSet,text,audio.millis()+1500);context.mouthFlapFor(imageSet,audio.millis());
                    }),() -> {speaking=false;lastUsed=System.nanoTime();});
                });
            } catch(Exception error) {context.log().warning("[tts] "+error);stopProcess();}
        });
    }
    public void stop() {
        generation.incrementAndGet();stopProcess();
        if(speaking){context.stopSpeaking();context.stopMouthFlap();speaking=false;}
    }
    private synchronized void stopProcess() {
        Process child=process;process=null;
        if(child!=null && child.isAlive()){child.descendants().forEach(p -> p.destroyForcibly());child.destroyForcibly();}
    }
    @Override public void close(){closed=true;stop();jobs.shutdownNow();idle.shutdownNow();if(installer!=null && installer.isAlive()){installer.descendants().forEach(p -> p.destroyForcibly());installer.destroyForcibly();}}
}
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
import java.util.function.BooleanSupplier;

/** Optional voice service: neither construction nor status checking installs anything. */
public final class LocalTtsService implements AutoCloseable {
    private final PluginContext context;
    private final BooleanSupplier installationCheck;
    private final SentenceAudioQueue<PcmAudio> sentences;
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
    private volatile boolean previewRunning;
    private volatile String previewDevice;
    private volatile int previewVolume;
    private final java.util.concurrent.atomic.AtomicReference<Consumer<String>> previewFinished=new java.util.concurrent.atomic.AtomicReference<>();
    public static final String PREVIEW_TEXT="안녕하세요, 루미와 함께 즐거운 하루 보내요!";
    public LocalTtsService(PluginContext context) { this(context,null,null); }
    LocalTtsService(PluginContext context,BooleanSupplier installationCheck,SentenceAudioQueue.Synth<PcmAudio> synthesizer) {
        this.context=context;
        this.installationCheck=installationCheck;
        sentences=new SentenceAudioQueue<>(synthesizer==null?this::synthesize:synthesizer,this::playPart,error -> {
            context.log().warning("[tts] "+error);finishPreview("미리듣기 실패: "+error.getMessage());stop();
        });
        idle.scheduleAtFixedRate(() -> {if((!enabled() && !previewRunning) || context.focusActive()) stop(); if(!synthesizing && !speaking && System.nanoTime()-lastUsed>TimeUnit.MINUTES.toNanos(10)) stopProcess();},1,1,TimeUnit.SECONDS);
    }
    public Path root() {return Path.of(System.getenv("LOCALAPPDATA"),"LumiCodex","tts");}
    private Path tools() throws Exception {return Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent().resolve("tools");}
    private Object config() throws IOException {return Json.parse(Files.readString(root().resolve("installed.json")));}
    public boolean installed() {
        if(installationCheck!=null)return installationCheck.getAsBoolean();
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
            String device=previewRunning?previewDevice:context.prefs().get("tts.device","auto");
            if(process==null || !process.isAlive() || !device.equals(loadedDevice)) {
                stopProcess();Object c=config();
                ProcessBuilder builder=new ProcessBuilder(Json.getString(c,"python"),"-u",tools().resolve("local_tts.py").toString(),"--serve","--root",root().toString(),"--device",device);
                builder.environment().put("PYTHONUTF8","1");
                Process child=builder.start();process=child;loadedDevice=device;
                if(closed || (!enabled() && !previewRunning)) {stopProcess();throw new IOException("TTS가 꺼져 있습니다.");}
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
    private volatile String speechImageSet;
    private volatile String speechText;
    private volatile int speechMascotId;
    private volatile CompletableFuture<Void> playingDone;

    public void speak(String text,String imageSet,int mascotId) {
        if(closed || previewRunning || !enabled() || context.focusActive() || text.isBlank())return;
        long ticket=generation.incrementAndGet();
        stopAudio();
        speechImageSet=imageSet;speechMascotId=mascotId;speechText=text;
        sentences.submit(SpeechSentences.split(text),() -> !closed && ticket==generation.get() && enabled() && !context.focusActive());
    }

    public boolean previewing(){return previewRunning;}
    public void preview(String device,int volume,Consumer<String> finished) {
        if(closed)throw new IllegalStateException("음성 서비스가 종료됐습니다.");
        if(!installed())throw new IllegalStateException("로컬 TTS를 먼저 설치해 주세요.");
        if(context.focusActive())throw new IllegalStateException("집중 모드를 끈 뒤 미리듣기를 사용해 주세요.");
        if(!List.of("auto","cpu","cuda").contains(device))throw new IllegalArgumentException("지원하지 않는 실행 장치입니다.");
        if(volume<0 || volume>100)throw new IllegalArgumentException("음량은 0~100이어야 합니다.");
        stop();
        previewDevice=device;previewVolume=volume;previewFinished.set(finished);previewRunning=true;
        long ticket=generation.incrementAndGet();
        sentences.submit(List.of(PREVIEW_TEXT),() -> !closed && previewRunning && ticket==generation.get() && !context.focusActive());
    }
    private void finishPreview(String message) {
        Consumer<String> finished=previewFinished.getAndSet(null);
        previewRunning=false;
        if(finished!=null)context.onEdt(() -> finished.accept(message));
        if(!enabled())stopProcess();
    }

    private CompletableFuture<Void> playPart(PcmAudio audio,String text,BooleanSupplier valid) {
        CompletableFuture<Void> done=new CompletableFuture<>();
        // Resolve target only while this utterance is still current.
        String imageSet=speechImageSet;int mascotId=speechMascotId;String fullText=speechText;
        boolean preview=previewRunning;int volume=preview?previewVolume:context.prefs().getInt("tts.volume",80);
        context.onEdt(() -> {
            if(!valid.getAsBoolean() || (!preview && context.mascotById(mascotId)==null)){done.complete(null);return;}
            playingDone=done;
            context.setSpeechVolume(volume);
            speaking=true;
            try {
                context.speak(audio,() -> context.onEdt(() -> {
                    if(!valid.getAsBoolean()){context.stopSpeaking();done.complete(null);return;}
                    // Keep the complete reply visible; only the audio is split into sentences.
                    if(!preview) {
                        context.sayTo(mascotId,imageSet,fullText,audio.millis()+1500);
                        context.mouthFlapFor(imageSet,audio.millis());
                    }
                }),() -> {
                    if(playingDone==done){speaking=false;lastUsed=System.nanoTime();if(preview)finishPreview("미리듣기를 마쳤습니다.");}
                    done.complete(null);
                });
                // Do not stall the queue indefinitely if an audio backend misses its completion callback.
                done.orTimeout(audio.millis()+15000,TimeUnit.MILLISECONDS);
            } catch(Exception error){done.completeExceptionally(error);}
        });
        return done;
    }

    private void stopAudio() {
        CompletableFuture<Void> done=playingDone;
        playingDone=null;
        if(speaking){context.stopSpeaking();context.stopMouthFlap();speaking=false;}
        if(done!=null)done.complete(null);
    }

    public void stop() {
        generation.incrementAndGet();stopProcess();
        stopAudio();
        finishPreview("미리듣기를 중지했습니다.");
    }
    private synchronized void stopProcess() {
        Process child=process;process=null;
        if(child!=null && child.isAlive()){child.descendants().forEach(p -> p.destroyForcibly());child.destroyForcibly();}
    }
    @Override public void close(){closed=true;stop();sentences.close();idle.shutdownNow();if(installer!=null && installer.isAlive()){installer.descendants().forEach(p -> p.destroyForcibly());installer.destroyForcibly();}}
}
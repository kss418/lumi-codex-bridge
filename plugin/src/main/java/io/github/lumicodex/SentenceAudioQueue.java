package io.github.lumicodex;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

/** One synthesis lane and one ordered playback lane, with one-part lookahead. */
public final class SentenceAudioQueue<T> implements AutoCloseable {
    @FunctionalInterface public interface Synth<T> {T run(String text) throws Exception;}
    @FunctionalInterface public interface Player<T> {CompletableFuture<Void> play(T audio,String text,BooleanSupplier valid);}
    private final ExecutorService synthesis=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"lumi-tts-synth");t.setDaemon(true);return t;});
    private final ExecutorService playback=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"lumi-tts-play");t.setDaemon(true);return t;});
    private final Semaphore slots=new Semaphore(2);
    private final Synth<T> synth;
    private final Player<T> player;
    private final Consumer<Exception> errors;
    public SentenceAudioQueue(Synth<T> synth,Player<T> player,Consumer<Exception> errors){this.synth=synth;this.player=player;this.errors=errors;}
    public void submit(List<String> parts,BooleanSupplier valid) {
        synthesis.submit(() -> {
            for(String text:parts) {
                boolean acquired=false;
                try {
                    while(valid.getAsBoolean() && !(acquired=slots.tryAcquire(100,TimeUnit.MILLISECONDS))) { }
                    if(!acquired)return;
                    if(!valid.getAsBoolean()){slots.release();return;}
                    T audio=synth.run(text);
                    if(!valid.getAsBoolean()){slots.release();return;}
                    playback.execute(() -> {
                        try {
                            if(!valid.getAsBoolean())return;
                            CompletableFuture<Void> done=player.play(audio,text,valid);
                            while(valid.getAsBoolean()) {
                                try {done.get(100,TimeUnit.MILLISECONDS);break;}
                                catch(TimeoutException waiting) { }
                            }
                        } catch(Exception error) {if(valid.getAsBoolean())errors.accept(error);}
                        finally {slots.release();}
                    });
                } catch(Exception error) {
                    if(acquired)slots.release();
                    if(valid.getAsBoolean())errors.accept(error);
                    return;
                }
            }
        });
    }
    @Override public void close(){synthesis.shutdownNow();playback.shutdownNow();}
}
package io.github.lumicodex;

import java.util.function.*;

/** No capture code here: a clock-driven scheduler, invoked on the Swing EDT. */
public final class AutoScreenWatch {
    private final Supplier<ScreenWatchSettings> settings;
    private final BooleanSupplier ready;
    private final Runnable capture;
    private final LongSupplier clock;
    private ScreenWatchSettings previous;
    private long nextRun;
    private boolean stopped;

    public AutoScreenWatch(Supplier<ScreenWatchSettings> settings, BooleanSupplier ready, Runnable capture) {
        this(settings,ready,capture,System::nanoTime);
    }
    public AutoScreenWatch(Supplier<ScreenWatchSettings> settings, BooleanSupplier ready, Runnable capture, LongSupplier clock) {
        this.settings=settings; this.ready=ready; this.capture=capture; this.clock=clock;
    }
    public void tick() {
        if(stopped) return;
        ScreenWatchSettings current=settings.get(); long now=clock.getAsLong();
        long interval=current.intervalSeconds()*1_000_000_000L;
        if(!current.equals(previous)) { previous=current; nextRun=now+interval; return; }
        if(!current.enabled() || now-nextRun<0) return;
        // Missed ticks are skipped, never replayed in a burst after sleep/busy time.
        nextRun=now+interval;
        if(ready.getAsBoolean()) capture.run();
    }
    public void stop() { stopped=true; }
}

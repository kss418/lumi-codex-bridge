import io.github.lumicodex.AutoScreenWatch;
import io.github.lumicodex.ScreenWatchSettings;
import com.group_finity.mascot.lumi.plugin.PluginTestKit;
import java.util.concurrent.atomic.*;

public class AutoScreenWatchTest {
    private static void check(boolean condition,String label) { if(!condition) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        AtomicLong time=new AtomicLong(); AtomicInteger captures=new AtomicInteger(); AtomicBoolean ready=new AtomicBoolean(true);
        AtomicReference<ScreenWatchSettings> settings=new AtomicReference<>(new ScreenWatchSettings(false,30));
        AutoScreenWatch watch=new AutoScreenWatch(settings::get,ready::get,captures::incrementAndGet,time::get);
        watch.tick(); time.set(100_000_000_000L); watch.tick(); check(captures.get()==0,"default off");
        settings.set(new ScreenWatchSettings(true,30)); watch.tick(); check(captures.get()==0,"no immediate capture");
        time.addAndGet(29_000_000_000L); watch.tick(); check(captures.get()==0,"interval respected");
        time.addAndGet(1_000_000_000L); watch.tick(); check(captures.get()==1,"scheduled capture");
        ready.set(false); time.addAndGet(30_000_000_000L); watch.tick(); check(captures.get()==1,"busy skipped");
        ready.set(true); watch.tick(); check(captures.get()==1,"skipped capture not replayed");
        time.addAndGet(600_000_000_000L); watch.tick(); watch.tick(); check(captures.get()==2,"no catch-up burst");
        settings.set(new ScreenWatchSettings(true,60)); watch.tick(); time.addAndGet(59_000_000_000L); watch.tick(); check(captures.get()==2,"interval change resets deadline");
        time.addAndGet(1_000_000_000L); watch.tick(); check(captures.get()==3,"new interval");
        settings.set(new ScreenWatchSettings(false,60)); watch.tick(); time.addAndGet(100_000_000_000L); watch.tick(); check(captures.get()==3,"disabled stops capture");
        settings.set(new ScreenWatchSettings(true,30)); watch.tick(); watch.stop(); time.addAndGet(100_000_000_000L); watch.tick(); check(captures.get()==3,"plugin stop");
        try(var kit=PluginTestKit.start("lumi.codex.auto.screen.test")) {
            var prefs=kit.context().prefs();
            check(!ScreenWatchSettings.load(prefs).enabled(),"default disabled");
            new ScreenWatchSettings(true,240).save(prefs); prefs.reload();
            check(ScreenWatchSettings.load(prefs).equals(new ScreenWatchSettings(true,240)),"saved settings reload");
            prefs.set("screenWatch.intervalSeconds",-1);
            check(ScreenWatchSettings.load(prefs).intervalSeconds()==180,"invalid stored interval fallback");
        }
        System.out.println("PASS: timer, skip/burst handling, enable/disable, stop, settings persistence (no screen capture)");
    }
}
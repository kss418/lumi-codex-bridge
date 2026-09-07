import io.github.lumicodex.*;
import com.group_finity.mascot.lumi.plugin.*;
import java.util.concurrent.atomic.*;
public class SelfTalkTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.selftalk.test")) {
   check(!SelfTalkSettings.load(kit.context().prefs()).enabled(),"default off");
   check(SelfTalkSettings.load(kit.context().prefs()).intervalSeconds()==300,"default five minutes");
   new SelfTalkSettings(true,30).save(kit.context().prefs());
   check(SelfTalkSettings.load(kit.context().prefs()).equals(new SelfTalkSettings(true,30)),"settings persist");
   var clock=new AtomicLong();var ready=new AtomicBoolean(true);var requests=new AtomicInteger();
   var scheduler=new AutoScreenWatch(()->SelfTalkSettings.load(kit.context().prefs()).schedule(),ready::get,requests::incrementAndGet,clock::get);
   scheduler.tick();check(requests.get()==0,"not immediate");
   clock.set(30_000_000_000L);scheduler.tick();check(requests.get()==1,"interval elapsed");
   ready.set(false);clock.set(60_000_000_000L);scheduler.tick();check(requests.get()==1,"busy skipped");
   ready.set(true);scheduler.tick();check(requests.get()==1,"no catch-up burst");
   new SelfTalkSettings(false,30).save(kit.context().prefs());scheduler.tick();clock.set(120_000_000_000L);scheduler.tick();check(requests.get()==1,"disabled");
   new SelfTalkSettings(true,30).save(kit.context().prefs());scheduler.tick();scheduler.stop();clock.set(200_000_000_000L);scheduler.tick();check(requests.get()==1,"unload stops");
   check(ScreenReaction.visibleReply("[[LUMI_SILENT]]",true).isEmpty(),"silence handling");
   check(SelfTalkSettings.prompt().contains("화면 이미지가 없으므로"),"no imagined screen");
   for(int invalid:new int[]{0,29,3601}){try{new SelfTalkSettings(true,invalid);throw new AssertionError("invalid interval");}catch(IllegalArgumentException expected){}}
  }
  System.out.println("PASS: self-talk defaults, persistence, timing, busy/disable/stop and silence (no model, capture or audio)");
 }
}

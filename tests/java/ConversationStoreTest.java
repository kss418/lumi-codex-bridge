import io.github.lumicodex.*;
import com.group_finity.mascot.lumi.plugin.*;
import java.nio.file.*;
public class ConversationStoreTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  try(var kit=PluginTestKit.start("lumi.codex.history.test")) {
   var store=new ConversationStore(kit.context());
   var first=store.snapshot("Lumi");check(first.turns().isEmpty(),"new history");
   check(store.append("Lumi",first.session(),"안녕","안녕하세요"),"save");
   check(new ConversationStore(kit.context()).snapshot("Lumi").turns().getFirst().get("assistant").equals("안녕하세요"),"reload");
   check(store.snapshot("Other").turns().isEmpty(),"character isolation");
   store.reset("Lumi",false);check(store.snapshot("Lumi").turns().isEmpty(),"new conversation excludes old context");
   check(store.display("Lumi").contains("안녕하세요"),"archive retained");
   check(!store.append("Lumi",first.session(),"late","late"),"stale request rejected");
   String current=store.snapshot("Lumi").session();
   for(int i=0;i<55;i++)store.append("Lumi",current,"질문"+i,"답변"+i);
   check(store.snapshot("Lumi").turns().size()==20,"context count limit");
   check(!store.display("Lumi").contains("사용자: 질문0\n"),"retention limit");
   store.settings(new HistorySettings(50,50));
   check(new ConversationStore(kit.context()).snapshot("Lumi").turns().size()==50,"reference maximum 50");
   store.settings(new HistorySettings(5,3));
   check(HistorySettings.load(kit.context().prefs()).equals(new HistorySettings(5,3)),"saved limits");
   check(store.snapshot("Lumi").turns().size()==3,"configured reference count");
   check(!store.display("Lumi").contains("사용자: 질문49\n"),"lower retention prunes old records");
   for(int[] invalid:new int[][]{{0,1},{51,1},{5,6},{5,0},{50,51}}) {
    try{new HistorySettings(invalid[0],invalid[1]);throw new AssertionError("invalid limits");}catch(IllegalArgumentException expected){}
   }
   store.reset("Lumi",true);check(!store.display("Lumi").contains("답변"),"delete all history");
   check(!store.append("Lumi",current,"late","late"),"delete survives in-flight turn");
   Files.writeString(store.file(),"broken");
   try{store.snapshot("Lumi");throw new AssertionError("corruption accepted");}catch(java.io.IOException expected){}
   check(Files.readString(store.file()).equals("broken"),"corrupt file preserved");
  }
  System.out.println("PASS: history reload, isolation, reset/delete, bounded retention and corrupt-file preservation");
 }
}

import io.github.lumicodex.ScreenReaction;
public class ScreenReactionTest {
 public static void main(String[] args) {
  if(!ScreenReaction.visibleReply(" [[LUMI_SILENT]] \n",true).isEmpty())throw new AssertionError("silence leaked");
  if(!ScreenReaction.visibleReply("",true).isEmpty())throw new AssertionError("blank");
  if(!ScreenReaction.visibleReply("안녕하세요!",true).equals("안녕하세요!"))throw new AssertionError("reply lost");
  if(!ScreenReaction.prompt(true).contains("[[LUMI_SILENT]]"))throw new AssertionError("auto prompt");
  if(ScreenReaction.prompt(false).contains("[[LUMI_SILENT]]"))throw new AssertionError("manual prompt");
  System.out.println("PASS: automatic silence and manual response behavior");
 }
}
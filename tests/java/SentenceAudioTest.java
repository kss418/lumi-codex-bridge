import io.github.lumicodex.SpeechSentences;
import io.github.lumicodex.SentenceAudioQueue;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SentenceAudioTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  check(SpeechSentences.split("안녕! 루미예요. 같이 놀아요?").equals(List.of("안녕!","루미예요.","같이 놀아요?")),"Korean split");
  check(SpeechSentences.split("3.14예요.다음 문장!").equals(List.of("3.14예요.","다음 문장!")),"decimal and no space");
  check(SpeechSentences.split("  \n ").isEmpty(),"blank");
  check(SpeechSentences.split("가".repeat(700)).stream().allMatch(s->s.length()<=240),"long sentence bound");
  CountDownLatch firstPlaying=new CountDownLatch(1),secondSynth=new CountDownLatch(1),releaseSecond=new CountDownLatch(1),secondPlaying=new CountDownLatch(1);
  CompletableFuture<Void> finishFirst=new CompletableFuture<>();AtomicBoolean valid=new AtomicBoolean(true);
  List<String> played=new CopyOnWriteArrayList<>();List<Exception> errors=new CopyOnWriteArrayList<>();
  try(var queue=new SentenceAudioQueue<String>(text->{
   if(text.equals("two")){secondSynth.countDown();if(!releaseSecond.await(3,TimeUnit.SECONDS))throw new AssertionError("test gate");}
   return text;
  },(audio,text,alive)->{
   played.add(text);
   if(text.equals("one")){firstPlaying.countDown();return finishFirst;}
   secondPlaying.countDown();return CompletableFuture.completedFuture(null);
  },errors::add)) {
   queue.submit(List.of("one","two"),valid::get);
   check(firstPlaying.await(2,TimeUnit.SECONDS),"first starts before full synthesis");
   check(secondSynth.await(2,TimeUnit.SECONDS),"next sentence synthesized during playback");
   releaseSecond.countDown();
   check(!secondPlaying.await(150,TimeUnit.MILLISECONDS),"no overlapping playback");
   finishFirst.complete(null);
   check(secondPlaying.await(2,TimeUnit.SECONDS),"second plays after first");
   check(played.equals(List.of("one","two")),"ordered playback");check(errors.isEmpty(),"no queue errors");
  }
  AtomicBoolean active=new AtomicBoolean(true);CountDownLatch playing=new CountDownLatch(1),nextUtterance=new CountDownLatch(1);List<String> cancelPlayed=new CopyOnWriteArrayList<>();
  try(var queue=new SentenceAudioQueue<String>(text->text,(audio,text,alive)->{
   cancelPlayed.add(text);
   if(text.equals("old1")){playing.countDown();return new CompletableFuture<>();}
   nextUtterance.countDown();return CompletableFuture.completedFuture(null);
  },errors::add)) {
   queue.submit(List.of("old1","old2"),active::get);check(playing.await(2,TimeUnit.SECONDS),"started");
   active.set(false);queue.submit(List.of("new"),()->true);
   check(nextUtterance.await(2,TimeUnit.SECONDS),"stop releases old queue");
   check(cancelPlayed.equals(List.of("old1","new")),"stale sentence suppressed");
  }
  System.out.println("PASS: splitting, early playback, prefetch, ordering, cancellation (no audio/GPU)");
 }
}
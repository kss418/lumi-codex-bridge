package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import com.group_finity.mascot.lumi.plugin.MascotState;
import java.util.Map;
import java.util.function.Consumer;

/** Original LUMI Chat external speech event; no model invocation or extra file watcher. */
public final class ExternalSpeech implements AutoCloseable {
    @FunctionalInterface public interface Reader { void read(String text,String imageSet,int mascotId); }
    private final PluginContext context;
    private final Reader reader;
    private final Consumer<Map<String,Object>> handler=this::onLine;
    private volatile boolean closed;
    public ExternalSpeech(PluginContext context,Reader reader) {
        this.context=context;this.reader=reader;context.on("say_file",handler);
    }
    private void onLine(Map<String,Object> event) {
        if(closed || !context.prefs().getBoolean("tts.enabled",false)
                || context.focusActive())return;
        Object value=event.get("text");
        if(!(value instanceof String text) || text.isBlank())return;
        String line=text.strip();
        if(line.length()>2000){context.log().warning("외부 대사가 2000자를 넘어 음성 재생을 건너뜁니다.");return;}
        context.onEdt(() -> {
            if(closed || !context.prefs().getBoolean("tts.enabled",false)
                    || context.focusActive())return;
            MascotState target=null;
            if(event.get("mascotId") instanceof Number id) target=context.mascotById(id.intValue());
            if(target==null && event.get("imageSet") instanceof String name) target=context.mascot(name);
            if(target==null) {
                var candidates=context.mascots().stream().filter(m -> context.isCharacter(m.imageSet())).toList();
                target=candidates.stream().filter(m -> "Lumi".equalsIgnoreCase(m.imageSet())).findFirst().orElse(candidates.isEmpty()?null:candidates.getFirst());
            }
            if(target!=null)reader.read(line,target.imageSet(),target.id());
        });
    }
    @Override public void close(){closed=true;context.off("say_file",handler);}
}
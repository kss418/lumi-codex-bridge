package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginContext;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class DoubleClickChat implements AutoCloseable {
    private final PluginContext context;
    private final BiConsumer<String,Integer> open;
    private volatile boolean closed;
    private final Consumer<Map<String,Object>> handler=this::clicked;
    public DoubleClickChat(PluginContext context,BiConsumer<String,Integer> open) {
        this.context=context;this.open=open;context.on("doubleclick",handler);
    }
    private void clicked(Map<String,Object> event) {
        if(!(event.get("mascotId") instanceof Number id))return;
        context.onEdt(()->{
            if(closed)return;
            var mascot=context.mascotById(id.intValue());
            if(mascot!=null && context.isCharacter(mascot.imageSet()))open.accept(mascot.imageSet(),mascot.id());
        });
    }
    @Override public void close(){closed=true;context.off("doubleclick",handler);}
}

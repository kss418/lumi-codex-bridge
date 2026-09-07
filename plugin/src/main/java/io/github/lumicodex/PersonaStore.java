package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.Json;
import com.group_finity.mascot.lumi.plugin.PluginContext;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 캐릭터 이름은 JSON 키로만 사용하고 파일 경로에는 사용하지 않습니다. */
public final class PersonaStore {
    private final PluginContext context;
    public PersonaStore(PluginContext context) { this.context=context; }
    public Path file() { return context.dataDir().resolve("personas.json"); }
    private Map<String,String> read() throws IOException {
        if(!Files.exists(file())) return new LinkedHashMap<>();
        try {
            Object parsed=Json.parse(Files.readString(file()));
            Object version=Json.get(parsed,"version"); Object characters=Json.get(parsed,"characters");
            if(!(version instanceof Number n) || n.intValue()!=1 || !(characters instanceof Map<?,?> map)) throw new IOException("지원하지 않는 페르소나 파일 형식입니다.");
            Map<String,String> result=new LinkedHashMap<>();
            for(var entry:map.entrySet()) {
                if(!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) throw new IOException("페르소나 파일 형식 오류");
                if(value.length()>20000) throw new IOException("페르소나는 20,000자 이하여야 합니다.");
                result.put(key,value);
            }
            return result;
        } catch(RuntimeException error) { throw new IOException("페르소나 파일을 읽지 못했습니다. 원본을 확인해 주세요.",error); }
    }
    public String defaultPersona(String character) {
        String persona = Objects.toString(context.persona(character), "");
        if (!persona.isBlank()) return persona;
        persona = Objects.toString(context.persona("Lumi"), "");
        if (!persona.isBlank()) return persona;
        try (var stream = PersonaStore.class.getResourceAsStream("/default_persona.txt")) {
            if (stream != null) return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        } catch (IOException error) { context.log().warning(error.toString()); }
        return "너는 다정한 데스크톱 AI 루미다. 자신을 루미라고 부르며 밝고 따뜻한 존댓말로 짧게 대화한다.";
    }
    public String effective(String character) throws IOException {
        Map<String,String> data=read();
        return data.containsKey(character)?data.get(character):defaultPersona(character);
    }
    public void save(String character,String text) throws IOException {
        if(text.length()>20000) throw new IOException("페르소나는 20,000자 이하여야 합니다.");
        Map<String,String> data=read(); data.put(character,text); write(data);
    }
    public void reset(String character) throws IOException { Map<String,String> data=read(); data.remove(character); write(data); }
    private void write(Map<String,String> data) throws IOException {
        Files.createDirectories(context.dataDir());
        context.writeAtomic(file(),Json.write(Map.of("version",1,"characters",data)),true);
    }
}


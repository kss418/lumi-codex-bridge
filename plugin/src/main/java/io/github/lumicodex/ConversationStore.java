package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Text-only bounded history. Session tokens prevent deleted conversations returning after an in-flight turn. */
public final class ConversationStore {
    private static final Object LOCK=new Object();
    private final PluginContext context;
    public ConversationStore(PluginContext context){this.context=context;}
    public Path file(){return context.dataDir().resolve("conversations.json");}
    public record Snapshot(String session,List<Map<String,String>> turns) {}
    private Map<String,Object> read()throws IOException {
        if(!Files.exists(file()))return new LinkedHashMap<>();
        if(Files.size(file())>8*1024*1024)throw new IOException("대화 기록 파일이 너무 큽니다.");
        try {
            Object value=Json.parse(Files.readString(file()));
            if(!(Json.get(value,"version") instanceof Number n) || n.intValue()!=1 || !(Json.get(value,"characters") instanceof Map<?,?> data))throw new IOException("대화 기록 형식 오류");
            Map<String,Object> result=new LinkedHashMap<>();
            for(var item:data.entrySet()) {
                if(!(item.getKey() instanceof String key) || !(Json.get(item.getValue(),"session") instanceof String) || !(Json.get(item.getValue(),"turns") instanceof List<?> turns) || turns.size()>50)throw new IOException("대화 기록 형식 오류");
                for(Object turn:turns)for(String field:List.of("session","user","assistant"))if(!(Json.get(turn,field) instanceof String))throw new IOException("대화 기록 형식 오류");
                result.put(key,item.getValue());
            }
            return result;
        }catch(RuntimeException error){throw new IOException("대화 기록을 읽지 못했습니다. 원본 파일을 보존합니다.",error);}
    }
    private void write(Map<String,Object> data)throws IOException {
        Files.createDirectories(context.dataDir());
        context.writeAtomic(file(),Json.write(Map.of("version",1,"characters",data)),false);
    }
    private Object ensure(Map<String,Object> data,String character)throws IOException {
        if(!data.containsKey(character)) {data.put(character,Map.of("session",UUID.randomUUID().toString(),"turns",List.of()));write(data);}
        return data.get(character);
    }
    private static List<Map<String,String>> turns(Object entry) {
        List<Map<String,String>> result=new ArrayList<>();
        for(Object turn:(List<?>)Json.get(entry,"turns"))result.add(Map.of("session",Json.getString(turn,"session"),"user",Json.getString(turn,"user"),"assistant",Json.getString(turn,"assistant")));
        return result;
    }
    public Snapshot snapshot(String character)throws IOException {
        synchronized(LOCK) {
            var data=read();Object entry=ensure(data,character);String session=Json.getString(entry,"session");
            var active=turns(entry).stream().filter(t->session.equals(t.get("session"))).toList();
            List<Map<String,String>> recent=new ArrayList<>();int size=0;
            for(int i=active.size()-1;i>=0 && recent.size()<HistorySettings.load(context.prefs()).referenced();i--) {
                var turn=active.get(i);int length=turn.get("user").length()+turn.get("assistant").length();
                if(size+length>40000)break;
                recent.addFirst(Map.of("user",turn.get("user"),"assistant",turn.get("assistant")));size+=length;
            }
            return new Snapshot(session,List.copyOf(recent));
        }
    }
    public boolean current(String character,String session)throws IOException {
        synchronized(LOCK){return session.equals(Json.getString(read().get(character),"session"));}
    }
    public boolean append(String character,String session,String user,String assistant)throws IOException {
        synchronized(LOCK) {
            var data=read();Object entry=data.get(character);
            if(entry==null || !session.equals(Json.getString(entry,"session")))return false;
            var turns=turns(entry);turns.add(Map.of("session",session,"user",clip(user),"assistant",clip(assistant)));
            while(turns.size()>HistorySettings.load(context.prefs()).stored())turns.removeFirst();
            data.put(character,Map.of("session",session,"turns",turns));write(data);return true;
        }
    }
    private static String clip(String text){return text.length()>4000?text.substring(0,3999)+"…":text;}
    public void reset(String character,boolean delete)throws IOException {
        synchronized(LOCK) {
            var data=read();Object entry=data.get(character);
            data.put(character,Map.of("session",UUID.randomUUID().toString(),"turns",delete || entry==null?List.of():turns(entry)));write(data);
        }
    }
    public void settings(HistorySettings limits)throws IOException {
        synchronized(LOCK) {
            var data=read();
            for(var entry:data.entrySet()) {
                var kept=turns(entry.getValue());
                while(kept.size()>limits.stored())kept.removeFirst();
                entry.setValue(Map.of("session",Json.getString(entry.getValue(),"session"),"turns",kept));
            }
            write(data);limits.save(context.prefs());
        }
    }
    public String display(String character)throws IOException {
        synchronized(LOCK) {
            Object entry=read().get(character);if(entry==null)return "저장된 대화가 없습니다.";
            StringBuilder text=new StringBuilder();String previous="";
            for(var turn:turns(entry)) {
                if(!previous.equals(turn.get("session"))){text.append("── 대화 ──\n");previous=turn.get("session");}
                text.append("사용자: ").append(turn.get("user")).append("\n\n루미: ").append(turn.get("assistant")).append("\n\n");
            }
            return text.isEmpty()?"저장된 대화가 없습니다.":text.toString();
        }
    }
}

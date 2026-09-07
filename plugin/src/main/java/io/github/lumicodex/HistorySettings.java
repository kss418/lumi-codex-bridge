package io.github.lumicodex;
import com.group_finity.mascot.lumi.plugin.PluginPrefs;

public record HistorySettings(int stored,int referenced) {
    public HistorySettings {
        if(stored<1 || stored>50 || referenced<1 || referenced>stored)
            throw new IllegalArgumentException("저장·참고 횟수는 1~50회이며, 참고 횟수는 저장 횟수 이하여야 합니다.");
    }
    public static HistorySettings load(PluginPrefs prefs) {
        int stored=Math.max(1,Math.min(50,prefs.getInt("history.stored",50)));
        int referenced=Math.max(1,Math.min(stored,prefs.getInt("history.referenced",20)));
        return new HistorySettings(stored,referenced);
    }
    public void save(PluginPrefs prefs) {
        prefs.set("history.stored",stored);prefs.set("history.referenced",referenced);prefs.save();
    }
}

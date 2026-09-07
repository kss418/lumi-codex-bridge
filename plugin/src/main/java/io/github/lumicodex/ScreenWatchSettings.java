package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.PluginPrefs;

public record ScreenWatchSettings(boolean enabled, int intervalSeconds) {
    public static final int DEFAULT_SECONDS=180;
    public static final int MIN_SECONDS=30;
    public static final int MAX_SECONDS=3600;
    public ScreenWatchSettings {
        if(intervalSeconds<MIN_SECONDS || intervalSeconds>MAX_SECONDS)
            throw new IllegalArgumentException("Interval must be between 30 and 3600 seconds");
    }
    public static ScreenWatchSettings load(PluginPrefs prefs) {
        int seconds=prefs.getInt("screenWatch.intervalSeconds",DEFAULT_SECONDS);
        if(seconds<MIN_SECONDS || seconds>MAX_SECONDS) seconds=DEFAULT_SECONDS;
        return new ScreenWatchSettings(prefs.getBoolean("screenWatch.enabled",false),seconds);
    }
    public void save(PluginPrefs prefs) {
        prefs.set("screenWatch.intervalSeconds",intervalSeconds);
        prefs.set("screenWatch.enabled",enabled);
        prefs.save();
    }
}

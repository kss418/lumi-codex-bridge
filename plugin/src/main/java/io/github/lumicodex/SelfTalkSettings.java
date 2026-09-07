package io.github.lumicodex;
import com.group_finity.mascot.lumi.plugin.PluginPrefs;
public record SelfTalkSettings(boolean enabled,int intervalSeconds) {
    public static final int DEFAULT_SECONDS=300,MIN_SECONDS=30,MAX_SECONDS=3600;
    public SelfTalkSettings {if(intervalSeconds<MIN_SECONDS || intervalSeconds>MAX_SECONDS)throw new IllegalArgumentException("간격은 30~3600초여야 합니다.");}
    public static SelfTalkSettings load(PluginPrefs prefs) {
        int seconds=prefs.getInt("selfTalk.intervalSeconds",DEFAULT_SECONDS);
        if(seconds<MIN_SECONDS || seconds>MAX_SECONDS)seconds=DEFAULT_SECONDS;
        return new SelfTalkSettings(prefs.getBoolean("selfTalk.enabled",false),seconds);
    }
    public void save(PluginPrefs prefs){prefs.set("selfTalk.enabled",enabled);prefs.set("selfTalk.intervalSeconds",intervalSeconds);prefs.save();}
    public ScreenWatchSettings schedule(){return new ScreenWatchSettings(enabled,intervalSeconds);}
    public static String prompt(){return "사용자가 말을 걸지 않은 때에 먼저 건네는 짧은 혼잣말을 1~2문장으로 해 주세요. 페르소나와 최근 대화를 참고하되 같은 주제, 질문, 안부와 휴식 권유를 반복하지 마세요. 답을 요구하지 않는 가벼운 생각이나 이야기도 좋아요. 이번 요청에는 화면 이미지가 없으므로 지금 사용자가 하는 일, 게임 상황이나 감정을 보고 있는 것처럼 말하지 마세요. 지난 화면 이야기는 과거일 뿐이에요. 새롭게 할 말이 없다면 다른 설명 없이 [[LUMI_SILENT]]만 출력하세요.";}
}

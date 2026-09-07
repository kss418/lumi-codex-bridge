package io.github.lumicodex;

import java.util.*;

public final class SpeechSentences {
    private SpeechSentences() {}
    public static List<String> split(String text) {
        List<String> result=new ArrayList<>();StringBuilder part=new StringBuilder();
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);part.append(ch);
            boolean decimal=ch=='.' && i>0 && i+1<text.length() && Character.isDigit(text.charAt(i-1)) && Character.isDigit(text.charAt(i+1));
            boolean boundary=ch=='\n' || (!decimal && ".!?。！？".indexOf(ch)>=0);
            if(boundary) {
                while(i+1<text.length() && ".!?。！？\"'’”」』)】".indexOf(text.charAt(i+1))>=0)part.append(text.charAt(++i));
                add(result,part.toString());part.setLength(0);
            }
        }
        add(result,part.toString());return List.copyOf(result);
    }
    private static void add(List<String> result,String raw) {
        String text=raw.strip();
        while(text.length()>240) {
            int cut=text.lastIndexOf(' ',240);
            if(cut<80)cut=240;
            if(Character.isHighSurrogate(text.charAt(cut-1)))cut--;
            result.add(text.substring(0,cut).strip());text=text.substring(cut).strip();
        }
        if(!text.isBlank())result.add(text);
    }
}
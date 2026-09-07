package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.Json;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public record ReleaseUpdate(String version,URI download) {
    private static final String REPO="kss418/lumi-codex-bridge";
    private static final Pattern VERSION=Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?");
    public static ReleaseUpdate latest() throws Exception {
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var request=HttpRequest.newBuilder(URI.create("https://api.github.com/repos/"+REPO+"/releases/latest"))
                    .timeout(Duration.ofSeconds(20)).header("Accept","application/vnd.github+json")
                    .header("User-Agent","Lumi-Codex").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404)throw new IllegalStateException("공개된 릴리스가 없습니다.");
            if(response.statusCode()!=200)throw new IllegalStateException("업데이트 조회에 실패했습니다. HTTP "+response.statusCode());
            return parse(response.body());
        }
    }
    public static ReleaseUpdate parse(String body) {
        Object release=Json.parse(body);
        String tag=Json.getString(release,"tag_name");
        if(tag==null || !VERSION.matcher(tag).matches() || tag.contains("-") || Boolean.TRUE.equals(Json.get(release,"draft")) || Boolean.TRUE.equals(Json.get(release,"prerelease")))
            throw new IllegalArgumentException("정식 릴리스 버전을 확인할 수 없습니다.");
        Object assets=Json.get(release,"assets");
        if(assets instanceof List<?> list)for(Object asset:list) {
            if(!"install-lumi-codex.ps1".equals(Json.getString(asset,"name")))continue;
            String url=Json.getString(asset,"browser_download_url");
            String expected="https://github.com/"+REPO+"/releases/download/"+tag+"/install-lumi-codex.ps1";
            if(!expected.equals(url))throw new IllegalArgumentException("설치 스크립트 주소가 올바르지 않습니다.");
            return new ReleaseUpdate(tag,URI.create(url));
        }
        throw new IllegalArgumentException("릴리스에 설치 스크립트가 없습니다.");
    }
    public boolean newerThan(String current) {
        var a=VERSION.matcher(version);var b=VERSION.matcher(current);
        if(!a.matches() || !b.matches())throw new IllegalArgumentException("버전 형식을 확인할 수 없습니다.");
        for(int i=1;i<=3;i++) {
            int comparison=new java.math.BigInteger(a.group(i)).compareTo(new java.math.BigInteger(b.group(i)));
            if(comparison!=0)return comparison>0;
        }
        return a.group(4)==null && b.group(4)!=null;
    }
}

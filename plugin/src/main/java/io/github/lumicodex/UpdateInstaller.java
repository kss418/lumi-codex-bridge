package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.Json;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class UpdateInstaller {
    public static Path prepare(ReleaseUpdate release,Consumer<String> progress) throws Exception {
        Path lumi=Path.of(com.group_finity.mascot.Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();
        if(!Files.isRegularFile(lumi.resolve("Little LUMI.exe")))throw new IllegalStateException("꼬미 실행 파일을 찾지 못했습니다.");
        Path base=Path.of(System.getenv("LOCALAPPDATA"),"LumiCodex","updates");
        Files.createDirectories(base);Path folder=Files.createTempDirectory(base,"update-");
        URI installer=release.download();
        String prefix="https://github.com/kss418/lumi-codex-bridge/releases/download/"+release.version()+"/";
        if(!installer.toString().equals(prefix+"install-lumi-codex.ps1"))throw new IllegalArgumentException("업데이트 주소 오류");
        try(var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build()) {
            byte[] hashes=download(client,URI.create(prefix+"SHA256SUMS.txt"),65536);
            Files.write(folder.resolve("SHA256SUMS.txt"),hashes);
            for(String name:List.of("install-lumi-codex.ps1","lumi-codex-windows-x64.zip")) {
                progress.accept(name.endsWith(".zip")?"업데이트 파일 다운로드 중…":"설치 스크립트 다운로드 중…");
                byte[] bytes=download(client,URI.create(prefix+name),name.endsWith(".zip")?100*1024*1024:1024*1024);
                verify(bytes,new String(hashes,StandardCharsets.UTF_8),name);
                Files.write(folder.resolve(name),bytes);
            }
        }
        try(var input=UpdateInstaller.class.getResourceAsStream("/update-helper.ps1")) {
            if(input==null)throw new IllegalStateException("업데이트 도우미가 없습니다.");
            Files.copy(input,folder.resolve("update-helper.ps1"));
        }
        Files.writeString(folder.resolve("request.json"),Json.write(Map.of("parentPid",ProcessHandle.current().pid(),"lumiHome",lumi.toString())),StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("launch.ps1"),"$ErrorActionPreference='Stop'\r\n$helper=Join-Path $PSScriptRoot 'update-helper.ps1'\r\nStart-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -WindowStyle Hidden -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('\"'+$helper+'\"'))\r\n",StandardCharsets.UTF_8);
        return folder;
    }
    private static byte[] download(HttpClient client,URI uri,int limit)throws Exception {
        var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).header("User-Agent","Lumi-Codex").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200)throw new IllegalStateException("다운로드 실패: HTTP "+response.statusCode());
        byte[] bytes=response.body();
        if(bytes.length>limit)throw new IllegalArgumentException("업데이트 파일이 너무 큽니다.");
        return bytes;
    }
    public static void verify(byte[] bytes,String manifest,String name)throws Exception {
        var matcher=Pattern.compile("(?m)^([a-fA-F0-9]{64})[ \t]+\\*?"+Pattern.quote(name)+"[ \t]*\r?$").matcher(manifest);
        if(!matcher.find())throw new IllegalArgumentException("해시가 없습니다: "+name);
        String expected=matcher.group(1);
        if(matcher.find())throw new IllegalArgumentException("중복 해시: "+name);
        String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if(!actual.equalsIgnoreCase(expected))throw new IllegalArgumentException("파일 검증 실패: "+name);
    }
    public static void launch(Path folder)throws Exception {
        Path powershell=Path.of(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe");
        Process launcher=new ProcessBuilder(powershell.toString(),"-NoProfile","-ExecutionPolicy","Bypass","-File",folder.resolve("launch.ps1").toString())
                .redirectErrorStream(true).redirectOutput(folder.resolve("launcher.log").toFile()).start();
        if(!launcher.waitFor(10,TimeUnit.SECONDS) || launcher.exitValue()!=0)throw new IllegalStateException("업데이트 도우미를 실행하지 못했습니다.");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!Files.exists(folder.resolve("ready"))) {
            if(System.nanoTime()>deadline)throw new IllegalStateException("업데이트 도우미가 응답하지 않습니다. 꼬미는 종료하지 않았습니다.");
            Thread.sleep(100);
        }
    }
}

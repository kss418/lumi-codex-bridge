import io.github.lumicodex.ReleaseUpdate;
import com.group_finity.mascot.lumi.plugin.Json;
import java.net.URI;
import java.util.*;
public class ReleaseUpdateTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static String fixture(String url,boolean preview) {
  return Json.write(Map.of("tag_name","v1.1.0","prerelease",preview,"assets",List.of(Map.of("name","install-lumi-codex.ps1","browser_download_url",url))));
 }
 public static void main(String[] args) throws Exception {
  String url="https://github.com/kss418/lumi-codex-bridge/releases/download/v1.1.0/install-lumi-codex.ps1";
  var release=ReleaseUpdate.parse(fixture(url,false));
  check(release.newerThan("1.0.0"));check(!release.newerThan("1.1.0"));check(!release.newerThan("1.2.0"));
  check(release.newerThan("1.1.0-beta.1"));
  check(new ReleaseUpdate("v1.10.0",URI.create(url)).newerThan("1.9.0"));
  for(String invalid:List.of(fixture(url,true),fixture("https://example.com/install.ps1",false),Json.write(Map.of("tag_name","v1.1.0","assets",List.of())))) {
   try {ReleaseUpdate.parse(invalid);throw new AssertionError("invalid release accepted");}catch(IllegalArgumentException expected){}
  }
  byte[] bytes="installer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  String manifest=hash+"  install-lumi-codex.ps1\r\n";
  io.github.lumicodex.UpdateInstaller.verify(bytes,manifest,"install-lumi-codex.ps1");
  for(String bad:List.of("",manifest+manifest,hash+"  another.ps1\n")) {
   try {io.github.lumicodex.UpdateInstaller.verify(bytes,bad,"install-lumi-codex.ps1");throw new AssertionError("bad hash manifest accepted");}catch(IllegalArgumentException expected){}
  }
  try {io.github.lumicodex.UpdateInstaller.verify(new byte[]{1},manifest,"install-lumi-codex.ps1");throw new AssertionError("tampering accepted");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: version ordering, stable releases, missing installer and download URL validation");
 }
}

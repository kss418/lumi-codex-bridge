package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 설정창에서만 사용하는 일회성 모델 목록 조회 연결입니다. */
public final class ModelCatalog implements AutoCloseable {
    public record Model(String id, String label, List<String> efforts, boolean available) {
        @Override public String toString() { return label; }
    }
    private Process process;
    private boolean closed;

    public List<Model> fetch(Consumer<String> log) throws Exception {
        Path location = Path.of(ModelCatalog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path root = location.getParent().getParent(); // mod/plugins/plugin.jar
        Path tools = root.resolve("tools");
        Path main = tools.resolve("main.py");
        if (!Files.isRegularFile(main)) throw new IOException("Python 브릿지 파일이 없습니다. 모드를 다시 설치해 주세요.");
        String python = System.getenv("LUMI_CODEX_PYTHON");
        if (python == null || python.isBlank()) {
            Path runtime = tools.resolve("runtime.json");
            if (Files.isRegularFile(runtime)) {
                String candidate = Json.getString(Json.parse(Files.readString(runtime)), "python");
                if (candidate != null && Files.isRegularFile(Path.of(candidate))) python = candidate;
            }
        }
        if (python == null || python.isBlank()) python = "python";
        synchronized (this) {
            if (closed) throw new IOException("조회가 취소되었습니다.");
            ProcessBuilder builder = new ProcessBuilder(python, "-u", main.toString(), "--stdio");
            builder.directory(tools.toFile());
            builder.environment().put("PYTHONUTF8", "1");
            process = builder.start();
        }
        Process child = process;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> output = executor.submit(() -> new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            executor.submit(() -> {
                try (var reader = child.errorReader(StandardCharsets.UTF_8)) {
                    for (String line; (line = reader.readLine()) != null;) log.accept(line);
                } catch (IOException ignored) { }
            });
            try {
                try (var writer = child.outputWriter(StandardCharsets.UTF_8)) {
                    writer.write("{\"id\":1,\"method\":\"model/list\"}\n{\"id\":2,\"method\":\"shutdown\"}\n");
                }
                if (!child.waitFor(30, TimeUnit.SECONDS)) throw new IOException("모델 목록 조회 시간이 초과되었습니다. 다시 시도해 주세요.");
                String response = output.get(3, TimeUnit.SECONDS);
                Object data = null;
                for (String line : response.lines().toList()) {
                    Object message = Json.parse(line);
                    Object id = Json.get(message, "id");
                    if (id instanceof Number number && number.intValue() == 1) {
                        if (Json.get(message, "error") != null) throw new IOException(Json.getString(message, "error", "message"));
                        data = Json.get(message, "result", "models");
                    } else if ("error".equals(Json.getString(message, "event"))) {
                        throw new IOException(Json.getString(message, "error", "message"));
                    }
                }
                if (!(data instanceof List<?> entries) || entries.isEmpty()) throw new IOException("모델 목록을 받지 못했습니다. Codex 로그인 상태를 확인해 주세요.");
                List<Model> models = new ArrayList<>();
                for (Object entry : entries) {
                    List<String> levels = new ArrayList<>();
                    Object supported = Json.get(entry, "supportedReasoningEfforts");
                    if (supported instanceof List<?> list) {
                        for (Object item : list) levels.add(Json.getString(item, "reasoningEffort"));
                    }
                    models.add(new Model(Json.getString(entry, "model"), Json.getString(entry, "displayName"), List.copyOf(levels), true));
                }
                return List.copyOf(models);
            } finally { close(); }
        }
    }

    @Override public synchronized void close() {
        closed = true;
        if (process != null && process.isAlive()) {
            process.descendants().forEach(handle -> handle.destroyForcibly());
            process.destroyForcibly();
        }
    }
}

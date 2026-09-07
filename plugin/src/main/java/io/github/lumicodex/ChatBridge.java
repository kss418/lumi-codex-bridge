package io.github.lumicodex;

import com.group_finity.mascot.lumi.plugin.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 대화창 하나가 소유하는 Python 프로세스. 요청은 한 번에 하나씩 실행합니다. */
public final class ChatBridge implements AutoCloseable {
    private final AtomicReference<Process> process = new AtomicReference<>();
    private final BlockingQueue<Object> incoming = new LinkedBlockingQueue<>();
    private final Consumer<String> log;
    private String model;
    private long nextId;
    private volatile boolean closed;
    private final Object writeGuard = new Object();
    private Long activeRequest;
    private boolean cancelRequested;
    public static final class Cancelled extends IOException {
        public Cancelled() { super("Generation cancelled"); }
    }
    public void prepareTurn() {
        synchronized (writeGuard) { cancelRequested = false; }
    }
    public void cancel() throws IOException {
        synchronized (writeGuard) {
            cancelRequested = true;
            if (activeRequest == null || process.get() == null) return;
            String line = Json.write(Map.of("id", "cancel-" + activeRequest, "method", "cancel",
                    "params", Map.of("request_id", activeRequest))) + "\n";
            OutputStream stream = process.get().getOutputStream();
            stream.write(line.getBytes(StandardCharsets.UTF_8)); stream.flush();
        }
    }

    public ChatBridge(Consumer<String> log) { this.log = log; }

    private void start(String selectedModel) throws Exception {
        if (closed) throw new IOException("대화가 종료되었습니다.");
        Path location = Path.of(ChatBridge.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path tools = location.getParent().getParent().resolve("tools");
        Path main = tools.resolve("main.py");
        if (!Files.isRegularFile(main)) throw new IOException("Python 브릿지가 없습니다. -Build로 다시 설치해 주세요.");
        String python = System.getenv("LUMI_CODEX_PYTHON");
        if (python == null || python.isBlank()) {
            Path runtime = tools.resolve("runtime.json");
            if (Files.isRegularFile(runtime)) {
                String candidate = Json.getString(Json.parse(Files.readString(runtime)), "python");
                if (candidate != null && Files.isRegularFile(Path.of(candidate))) python = candidate;
            }
        }
        if (python == null || python.isBlank()) python = "python";
        ProcessBuilder builder = new ProcessBuilder(python, "-u", main.toString(), "--stdio");
        builder.directory(tools.toFile()); builder.environment().put("PYTHONUTF8", "1");
        Process child = builder.start();
        process.set(child);
        if (closed) { stopProcess(); throw new IOException("대화가 종료되었습니다."); }
        Thread.startVirtualThread(() -> {
            try (var reader = child.inputReader(StandardCharsets.UTF_8)) {
                for (String line; (line = reader.readLine()) != null;) incoming.add(Json.parse(line));
            } catch (Exception error) { incoming.add(error); }
            finally { incoming.add(new IOException("Python 연결이 종료되었습니다. 창을 다시 열어 주세요.")); }
        });
        Thread.startVirtualThread(() -> {
            try (var reader = child.errorReader(StandardCharsets.UTF_8)) {
                for (String line; (line = reader.readLine()) != null;) log.accept(line);
            } catch (IOException ignored) { }
        });
        Object ready = receive(System.nanoTime() + TimeUnit.SECONDS.toNanos(35));
        if (!"ready".equals(Json.getString(ready, "event"))) {
            throw new IOException(Objects.toString(Json.getString(ready, "error", "message"), "Python 초기화 실패"));
        }
        model = selectedModel;
    }

    public String chat(String text, String selectedModel, String effort) throws Exception {
        return chat(text, selectedModel, effort, "");
    }

    public String chat(String text, String selectedModel, String effort, String persona) throws Exception {
        return chat(text, selectedModel, effort, persona, null);
    }

    public synchronized String chat(String text, String selectedModel, String effort, String persona, String image) throws Exception {
        return chat(text,selectedModel,effort,persona,image,null);
    }
    public synchronized String chat(String text, String selectedModel, String effort, String persona, String image,ConversationStore.Snapshot history) throws Exception {
        try {
            if (process.get() == null) start(selectedModel);
            if (!Objects.equals(model, selectedModel)) throw new IOException("모델 설정이 변경됐습니다. 대화창을 닫고 다시 열어 새 대화를 시작해 주세요.");
            long id = ++nextId;
            Map<String,Object> params = new LinkedHashMap<>(); params.put("text", text); params.put("persona", persona);
            if(history!=null){params.put("conversation",history.session());params.put("history",history.turns());}
            if (image != null) params.put("image", image);
            if (!selectedModel.isBlank()) params.put("model", selectedModel);
            if (!effort.isBlank()) params.put("effort", effort);
            String line = Json.write(Map.of("id", id, "method", "chat", "params", params)) + "\n";
            synchronized (writeGuard) {
                if (cancelRequested) throw new Cancelled();
                activeRequest = id;
                OutputStream stream = process.get().getOutputStream();
                stream.write(line.getBytes(StandardCharsets.UTF_8)); stream.flush();
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
            while (true) {
                Object response = receive(deadline);
                Object responseId = Json.get(response, "id");
                if (!(responseId instanceof Number number) || number.longValue() != id) continue;
                if (Json.get(response, "error") != null) throw new IOException(Json.getString(response, "error", "message"));
                if (Boolean.TRUE.equals(Json.get(response, "result", "cancelled"))) throw new Cancelled();
                return Objects.toString(Json.getString(response, "result", "text"), "");
            }
        } catch (Cancelled cancelled) {
            throw cancelled;
        } catch (Exception error) {
            close(); // a failed connection is not retried or reused automatically
            throw error;
        } finally {
            synchronized (writeGuard) { activeRequest = null; cancelRequested = false; }
        }
    }

    private Object receive(long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        Object item = remaining > 0 ? incoming.poll(remaining, TimeUnit.NANOSECONDS) : null;
        if (item == null) throw new IOException("Codex 응답 시간이 초과되었습니다. 창을 다시 열어 주세요.");
        if (item instanceof Exception error) throw error;
        return item;
    }

    private void stopProcess() {
        Process child = process.getAndSet(null);
        if (child == null) return;
        var descendants = child.descendants().toList();
        try { child.getOutputStream().close(); } catch (IOException ignored) { }
        Thread.startVirtualThread(() -> {
            try { child.waitFor(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            descendants.forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
            if (child.isAlive()) child.destroyForcibly();
        });
    }
    @Override public void close() {
        closed = true;
        stopProcess();
        incoming.offer(new IOException("대화가 종료되었습니다."));
    }
}


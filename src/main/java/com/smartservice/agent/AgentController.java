package com.smartservice.agent;

import com.smartservice.common.ApiResponse;
import com.smartservice.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentRuntime runtime;
    private final long streamDelayMs;

    public AgentController(AgentRuntime runtime,
                           @Value("${smartservice.agent.stream-delay-ms:90}") long streamDelayMs) {
        this.runtime = runtime;
        this.streamDelayMs = streamDelayMs;
    }

    @PostMapping("/chat")
    ApiResponse<AgentResponse> chat(@AuthenticationPrincipal UserPrincipal user,
                                    @Valid @RequestBody AgentRequest request) {
        return ApiResponse.ok(runtime.execute(user.id(), request));
    }

    @GetMapping("/status")
    ApiResponse<AgentRuntime.RuntimeStatus> status() {
        return ApiResponse.ok(runtime.status());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal UserPrincipal user,
                      @Valid @RequestBody AgentRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            try {
                send(emitter, "stage", Map.of("name", "intent_routing", "label", "正在识别需求"));
                pause();
                send(emitter, "stage", Map.of("name", "tool_planning", "label", "正在规划业务工具"));
                AgentResponse result = runtime.execute(user.id(), request,
                        chunk -> sendUnchecked(emitter, "token", Map.of("content", chunk)));
                pause();
                send(emitter, "engine", new EngineView(result.execution().engine().name(),
                        result.execution().model(), result.execution().fallback()));
                for (ToolRegistryView tool : result.toolCalls().stream()
                        .map(it -> new ToolRegistryView(it.tool(), it.latencyMs())).toList()) {
                    send(emitter, "tool", tool);
                }
                send(emitter, "result", result);
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) throws java.io.IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private void sendUnchecked(SseEmitter emitter, String event, Object data) {
        try {
            send(emitter, event, data);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private void pause() throws InterruptedException {
        Thread.sleep(Math.max(0, streamDelayMs));
    }

    private record ToolRegistryView(String name, long latencyMs) {}
    private record EngineView(String name, String model, boolean fallback) {}
}

package com.tzp.zjzx.agent.observability;

import com.tzp.zjzx.agent.service.GuideChatResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTelemetryTest {

    @Test
    void recordsCompletedChatOnce() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new AgentTelemetry(registry);

        telemetry.observeChat(Mono.just(new GuideChatResponse(
                "answer",
                GuideChatResponse.GuideResponseMode.AI,
                "deepseek",
                List.of()
        ))).block();

        assertThat(registry.counter(
                "zjzx.agent.chat.requests",
                "outcome",
                "ai"
        ).count()).isEqualTo(1D);
        assertThat(registry.find("zjzx.agent.chat.duration")
                .tag("outcome", "ai")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordsClientCancellationOnce() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new AgentTelemetry(registry);

        Disposable subscription = telemetry.observeChat(
                Mono.<GuideChatResponse>never()
        ).subscribe();
        subscription.dispose();

        assertThat(registry.counter(
                "zjzx.agent.chat.requests",
                "outcome",
                "cancelled"
        ).count()).isEqualTo(1D);
        assertThat(registry.find("zjzx.agent.chat.duration")
                .tag("outcome", "cancelled")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void classifiesModelFallbackWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new AgentTelemetry(registry);

        telemetry.recordModelFallback(new RuntimeException(
                new TimeoutException("model timeout")
        ));
        telemetry.recordModelFallback(new IllegalStateException("failure"));

        assertThat(registry.counter(
                "zjzx.agent.model.fallbacks",
                "reason",
                "timeout"
        ).count()).isEqualTo(1D);
        assertThat(registry.counter(
                "zjzx.agent.model.fallbacks",
                "reason",
                "model_error"
        ).count()).isEqualTo(1D);
    }
}

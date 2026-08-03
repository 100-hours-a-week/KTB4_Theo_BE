package com.theo.community_api.loadtest;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Component
@Profile("local")
@Endpoint(id = "loadtestsnapshot")
@RequiredArgsConstructor
public class LoadTestMetricsEndpoint {

    private static final String UNREAD_COUNT_URI = "/notifications/unread-count";

    private final MeterRegistry meterRegistry;

    @ReadOperation
    public LoadTestMetricsSnapshot snapshot() {
        Collection<Timer> gcPauseTimers =
                meterRegistry.find("jvm.gc.pause").timers();
        Collection<Timer> unreadRequestTimers =
                meterRegistry.find("http.server.requests")
                        .tag("method", "GET")
                        .tag("uri", UNREAD_COUNT_URI)
                        .timers();

        return new LoadTestMetricsSnapshot(
                Instant.now(),
                gaugeValue("process.cpu.usage"),
                gaugeSum("jvm.memory.used", "area", "heap"),
                gaugeValue("jvm.threads.live"),
                timerCount(gcPauseTimers),
                timerTotalTime(gcPauseTimers),
                timerMax(gcPauseTimers),
                gaugeSum("hikaricp.connections.active"),
                gaugeSum("hikaricp.connections.pending"),
                gaugeSum("tomcat.threads.busy"),
                gaugeSum("sse.emitters.active"),
                counterCount("sse.connections"),
                counterCount("sse.connections.failed"),
                counterCount("sse.notifications.sent"),
                counterCount("sse.notifications.failed"),
                timerCount(unreadRequestTimers),
                timerTotalTime(unreadRequestTimers),
                timerMax(unreadRequestTimers)
        );
    }

    private Double gaugeValue(String meterName) {
        Gauge gauge = meterRegistry.find(meterName).gauge();
        return gauge == null ? null : finiteValue(gauge.value());
    }

    private double gaugeSum(String meterName) {
        return meterRegistry.find(meterName)
                .gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .filter(Double::isFinite)
                .sum();
    }

    private double gaugeSum(
            String meterName,
            String tagName,
            String tagValue
    ) {
        return meterRegistry.find(meterName)
                .tag(tagName, tagValue)
                .gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .filter(Double::isFinite)
                .sum();
    }

    private long timerCount(Collection<Timer> timers) {
        return timers.stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private double counterCount(String meterName) {
        return meterRegistry.find(meterName)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private double timerTotalTime(Collection<Timer> timers) {
        return timers.stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS))
                .sum();
    }

    private double timerMax(Collection<Timer> timers) {
        return timers.stream()
                .mapToDouble(timer -> timer.max(TimeUnit.SECONDS))
                .max()
                .orElse(0.0);
    }

    private Double finiteValue(double value) {
        return Double.isFinite(value) ? value : null;
    }

    public record LoadTestMetricsSnapshot(
            Instant timestamp,
            Double processCpuUsage,
            double jvmHeapUsedBytes,
            Double jvmThreadsLive,
            long gcPauseCount,
            double gcPauseTotalSeconds,
            double gcPauseMaxSeconds,
            double hikariActiveConnections,
            double hikariPendingConnections,
            double tomcatBusyThreads,
            double sseActiveEmitters,
            double sseConnections,
            double sseConnectionFailures,
            double sseNotificationsSent,
            double sseNotificationFailures,
            long unreadRequestCount,
            double unreadRequestTotalSeconds,
            double unreadRequestMaxSeconds
    ) {
    }
}

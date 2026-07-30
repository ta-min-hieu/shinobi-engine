package dev.kage.server.gateway;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * Bootstrap OpenTelemetry SDK thật (ADR-015 — OTel cho Tracing, KHÔNG dùng Micrometer Tracing) —
 * OBSERVABILITY.md §6. Export qua OTLP/HTTP tới Jaeger ({@code deploy/docker/docker-compose.yml}
 * service {@code jaeger}, cổng 4318) — dùng biến thể HTTP thay vì gRPC để tránh transitive
 * {@code grpc-netty-shaded} đụng version với {@code netty-all} dự án đã dùng thật (Điều 23,
 * MODULE DUY NHẤT import Netty).
 *
 * <p>Sampling {@code Sampler.traceIdRatioBased(0.01)} — đúng lựa chọn ĐẦU của OBSERVABILITY.md §6
 * ("Sampling: trace 1% Command, HOẶC trace 100% Command chậm"). Nhánh "100% Command chậm" CHƯA
 * làm ở lát cắt này (cần {@code SpanProcessor} tự viết lọc theo duration lúc span kết thúc, phức
 * tạp hơn hẳn ratio-based có sẵn trong SDK — để dành lát cắt sau nếu số liệu thật đòi hỏi, Điều 30).
 *
 * <p>Chỉ 1 span "gateway.command" bao decode+validate+dispatch được tạo cho MỌI opcode (ở
 * {@link GatewayChannelHandler#channelRead0}, 1 điểm trung tâm) — span con sâu hơn (feature + DB,
 * khớp ví dụ đầy đủ OBSERVABILITY.md §6) mới chỉ làm cho 1 luồng đại diện
 * ({@code EnhanceItemCommand}), CHƯA retrofit hết ~50 opcode (phạm vi lát cắt này, xem javadoc
 * {@link GatewayChannelHandler#handleEnhanceItemBlocking}).
 */
final class GatewayTracing {

    private static final String SERVICE_NAME = "kage-server-gateway";
    private static final double SAMPLE_RATIO = 0.01;
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4318/v1/traces";

    private final OpenTelemetrySdk sdk;
    private final Tracer tracer;

    GatewayTracing() {
        this(
                Sampler.traceIdRatioBased(SAMPLE_RATIO),
                System.getenv().getOrDefault("KAGE_OTLP_ENDPOINT", DEFAULT_OTLP_ENDPOINT));
    }

    private GatewayTracing(Sampler sampler, String otlpEndpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), SERVICE_NAME)));
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler)
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder()
                                .setEndpoint(otlpEndpoint)
                                .build())
                        .build())
                .build();
        this.sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        this.tracer = sdk.getTracer(SERVICE_NAME);
    }

    /** {@code Sampler.alwaysOff()} — dùng cho test, không tạo network call OTLP thật nào. */
    static GatewayTracing disabled() {
        return new GatewayTracing(Sampler.alwaysOff(), DEFAULT_OTLP_ENDPOINT);
    }

    /** Span gốc cho 1 packet vào — {@code opcodeName} = tên hằng {@code Opcode}, hoặc "unknown" nếu số hiệu lạ. */
    Span startCommandSpan(String opcodeName) {
        return tracer.spanBuilder("gateway.command")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("opcode", opcodeName)
                .startSpan();
    }

    /**
     * Span con chạy trên Virtual Thread khác — {@code parentContext} PHẢI là
     * {@code Context.current()} chụp TRƯỚC khi rời EventLoop.
     */
    Span startChildSpan(String name, Context parentContext) {
        return tracer.spanBuilder(name).setParent(parentContext).startSpan();
    }

    void shutdown() {
        sdk.getSdkTracerProvider().shutdown();
    }
}

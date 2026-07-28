package dev.kage.server.gateway;

/**
 * Gộp {@link GatewayNetworkMetrics} + {@link GatewayTracing} thành 1 tham số — {@link
 * GatewayChannelHandler}/{@link GatewayServer} đều cần cả hai, gộp lại tránh vượt giới hạn
 * Checkstyle {@code ParameterNumber} (max 4) ở constructor.
 */
record GatewayObservability(GatewayNetworkMetrics networkMetrics, GatewayTracing tracing) {
}

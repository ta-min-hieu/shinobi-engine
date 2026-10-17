package dev.kage.server.world;

import java.lang.reflect.Proxy;

/** Sink không làm gì — cho test chỉ đọc trạng thái World qua khe kiểm thử. */
final class SilentWorldSink {

    private SilentWorldSink() {}

    // ReferenceEquality: equals của một proxy không trạng thái ĐÚNG là so tham chiếu.
    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    static WorldOutboundSink create() {
        return (WorldOutboundSink) Proxy.newProxyInstance(
                WorldOutboundSink.class.getClassLoader(),
                new Class<?>[] {WorldOutboundSink.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "SilentWorldSink";
                    default -> null;
                });
    }
}

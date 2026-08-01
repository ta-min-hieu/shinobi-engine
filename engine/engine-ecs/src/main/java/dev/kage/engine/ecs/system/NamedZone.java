package dev.kage.engine.ecs.system;

/** Vùng {@link Aabb} có tên, dùng bởi {@link TriggerZoneSystem} để báo vào/ra qua {@code zoneId}. */
public record NamedZone(String zoneId, Aabb bounds) {}

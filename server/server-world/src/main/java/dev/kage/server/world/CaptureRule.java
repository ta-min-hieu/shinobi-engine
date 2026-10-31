package dev.kage.server.world;

import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Luật của một lần thu phục yêu thú (ADR-124), do gateway đưa vào {@link WorldFacade#captureMonster}.
 *
 * @param chanceOf tỉ lệ (%) theo mã loài; rỗng = loài không thu phục được — chạy trên tick thread, chỉ tra bảng, không
 *     I/O
 */
public record CaptureRule(float rangeMeters, int maxHpPercent, Function<String, OptionalInt> chanceOf) {}

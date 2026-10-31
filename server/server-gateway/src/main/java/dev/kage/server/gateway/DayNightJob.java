package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;

import dev.kage.protocol.DayNightEvent;
import dev.kage.protocol.Opcode;
import dev.kage.world.domain.DayNightCycle;

/**
 * Ngày đêm (ADR-123) — mỗi {@value #PERIOD_SECONDS} giây: báo mọi kênh world chính đang đêm hay ngày (sát thương quái,
 * điểm spawn chỉ-đêm) và phát gói 327 cho mọi người online để client tối/sáng nền. Gửi lặp vô điều kiện thay vì chỉ
 * lúc đổi: kênh mới mở và người vừa vào world nhận trạng thái trong tối đa một nhịp, không cần đường riêng.
 */
final class DayNightJob {

    private static final long PERIOD_SECONDS = 30L;

    private DayNightJob() {}

    static ExpansionJobs.Job job(MainWorldChannelRegistry channels, ConnectedCharacterRegistry registry, Clock clock) {
        return new ExpansionJobs.Job("ngày đêm", PERIOD_SECONDS, () -> {
            Instant now = clock.instant();
            boolean night = DayNightCycle.isNight(now);
            int monsterDamagePercent = DayNightCycle.monsterDamagePercent(now);
            channels.forEachChannel(world -> world.setNight(night, monsterDamagePercent));
            registry.broadcastToAll(
                    Opcode.OPCODE_DAY_NIGHT_EVENT_VALUE,
                    DayNightEvent.newBuilder()
                            .setNight(night)
                            .setSecondsUntilChange((int) DayNightCycle.secondsUntilChange(now))
                            .build()
                            .toByteArray());
        });
    }
}

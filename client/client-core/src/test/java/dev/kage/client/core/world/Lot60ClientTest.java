package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailView;

/** Lô 60 phía client — DPS trong trận (ADR-413), đếm thư chưa đọc (ADR-412), ngày tạo nhân vật (ADR-410). */
class Lot60ClientTest {

    @Test
    void dpsOnlyInsideCombat() {
        assertThat(HudStatsCoordinator.dps(0L, 9_000L, 900L, 10_000L)).isEqualTo(90L);
        assertThat(HudStatsCoordinator.dps(0L, 0L, 900L, 10_000L))
                .as("nghỉ quá 5 giây")
                .isEqualTo(-1L);
        assertThat(HudStatsCoordinator.dps(-1L, 0L, 0L, 10_000L)).isEqualTo(-1L);
        assertThat(HudStatsCoordinator.dps(0L, 500L, 300L, 500L))
                .as("dưới 1 giây tính là 1")
                .isEqualTo(300L);
    }

    @Test
    void countsUnreadMail() {
        ListMailResponse response = ListMailResponse.newBuilder()
                .addMails(MailView.newBuilder().setUnread(true))
                .addMails(MailView.newBuilder().setUnread(false))
                .addMails(MailView.newBuilder().setUnread(true))
                .build();
        assertThat(HudStatsCoordinator.countUnread(response)).isEqualTo(2L);
    }

    @Test
    void formatsCreationDate() {
        assertThat(LookupToolsCoordinator.dateOf(1_956_528_000L, ZoneOffset.UTC))
                .isEqualTo("01/01/2032");
    }
}

package dev.kage.monster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link MonsterSpawnPoint} là hàng rào thứ hai sau {@code CHECK (respawn_seconds > 0)} của DB — và
 * {@code CHECK} đó KHÔNG chặn được {@code 'NaN'}/{@code 'Infinity'}: Postgres coi {@code NaN} lớn
 * hơn mọi số, nên cả hai đều thoả {@code > 0}.
 *
 * <p>Hậu quả ở {@code WorldFacade.tickMonsterRespawn}: đồng hồ {@code NaN} không bao giờ {@code < 0}
 * (coi là "đang sống") cũng không bao giờ {@code <= 0} (tới giờ hồi sinh) — con quái chết lần đầu
 * là biến mất VĨNH VIỄN, không lỗi, không log. Toạ độ {@code NaN} thì đặt con quái ở chỗ không ai
 * tới được và rơi vào ô lưới AoI {@code 0} bất kể vị trí thật.
 */
class MonsterSpawnPointTest {

    @Test
    void shouldAcceptOrdinarySpawnPoint() {
        MonsterSpawnPoint point = new MonsterSpawnPoint("training_dummy", "main", 8f, 0f, 5f, false);

        assertThat(point.respawnSeconds()).isEqualTo(5f);
    }

    @Test
    void shouldRejectNonFiniteRespawnSeconds() {
        assertThatThrownBy(() -> new MonsterSpawnPoint("training_dummy", "main", 8f, 0f, Float.NaN, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("respawnSeconds");
        assertThatThrownBy(
                        () -> new MonsterSpawnPoint("training_dummy", "main", 8f, 0f, Float.POSITIVE_INFINITY, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("respawnSeconds");
    }

    @Test
    void shouldRejectNonFinitePosition() {
        assertThatThrownBy(() -> new MonsterSpawnPoint("training_dummy", "main", Float.NaN, 0f, 5f, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posX");
        assertThatThrownBy(
                        () -> new MonsterSpawnPoint("training_dummy", "main", 8f, Float.NEGATIVE_INFINITY, 5f, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posY");
    }
}

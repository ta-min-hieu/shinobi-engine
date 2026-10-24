package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-081 D3 — sổ sát thương chỉ cho boss thế giới, chỉ đòn của nhân vật, đóng đúng một lần. */
class WorldBossDamageLedgerTest {

    @Test
    void shouldSumDamagePerCharacter_onlyForTrackedBosses() {
        WorldBossDamageLedger ledger = new WorldBossDamageLedger();
        ledger.track(-10L, "sample_boss");

        ledger.record(-10L, 1L, 30);
        ledger.record(-10L, 2L, 5);
        ledger.record(-10L, 1L, 12);
        ledger.record(-10L, -3L, 99); // quái khác đánh — bỏ qua
        ledger.record(-10L, 2L, 0); // trượt — bỏ qua
        ledger.record(-11L, 1L, 50); // quái thường — không có sổ

        assertThat(ledger.close(-11L)).isEmpty();
        WorldBossDamageLedger.Defeat defeat = ledger.close(-10L).orElseThrow();
        assertThat(defeat.monsterCode()).isEqualTo("sample_boss");
        assertThat(defeat.damageByCharacter()).isEqualTo(Map.of(1L, 42L, 2L, 5L));
        assertThat(ledger.close(-10L)).as("đóng một lần").isEmpty();
    }
}

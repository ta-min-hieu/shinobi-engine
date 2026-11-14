package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ScoreEntry;

/** Lô 64 — dòng bảng xếp hạng dùng chung (ADR-439…442). */
class Lot64ServerTest {

    @Test
    void buildsScoreEntry() {
        ScoreEntry entry = Lot64Commands.score("ĐạoHữu01", 12L);
        assertThat(entry.getName()).isEqualTo("ĐạoHữu01");
        assertThat(entry.getValue()).isEqualTo(12L);
    }
}

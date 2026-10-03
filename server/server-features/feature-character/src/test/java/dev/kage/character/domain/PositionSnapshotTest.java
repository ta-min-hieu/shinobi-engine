package dev.kage.character.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Vị trí đã lưu không hữu hạn (di chứng lỗ hướng NaN trước 2026-09-24 — vị trí hỏng được ghi xuống
 * cột REAL lúc thoát) không được đi tiếp vào {@code SelectCharacterResponse} hay world.
 */
class PositionSnapshotTest {

    @Test
    void shouldFallBackToOrigin_whenSavedCoordinatesAreNotFinite() {
        PositionSnapshot nan = new PositionSnapshot(Float.NaN, Float.NaN);
        PositionSnapshot infinite = new PositionSnapshot(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);

        assertThat(nan.x()).isZero();
        assertThat(nan.y()).isZero();
        assertThat(infinite.x()).isZero();
        assertThat(infinite.y()).isZero();
    }

    @Test
    void shouldKeepFiniteCoordinates() {
        PositionSnapshot snapshot = new PositionSnapshot(-12.5f, 3f);

        assertThat(snapshot.x()).isEqualTo(-12.5f);
        assertThat(snapshot.y()).isEqualTo(3f);
    }
}

package dev.kage.client.core.playground;

/**
 * Hằng số cho PlaygroundScreen — màn hình demo Phase 1 để verify fixed timestep + input, KHÔNG
 * phải gameplay thật (CODING-STANDARDS §18: constant theo domain, không magic number).
 */
final class PlaygroundConstants {

    /** World unit/giây (RENDERING.md §4: 1 world unit = 1 mét). */
    static final float MOVE_SPEED = 5f;

    static final float BOX_SIZE = 1f;

    /** Số world unit hiển thị theo chiều cao camera — FitViewport tạm thời (ADR-020 chưa chốt). */
    static final float VIEWPORT_HEIGHT_UNITS = 10f;

    private PlaygroundConstants() {
    }
}

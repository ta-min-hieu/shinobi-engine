package dev.kage.engine.core;

/**
 * Một trạng thái màn hình (CLIENT.md §3): Splash → Loading → Login → ... → InGame.
 *
 * <p>{@code fixedUpdate} chạy ở tick rate cố định 60Hz (logic). {@code render} chạy theo FPS máy,
 * nhận {@code alpha} = phần dư giữa 2 tick để nội suy hình ảnh mượt
 * (ENGINE.md §4, RENDERING.md §7). KHÔNG đặt logic gameplay trong {@code render} — chỉ vẽ.
 */
public interface GameScreen {

    default void show() {
    }

    default void hide() {
    }

    default void pause() {
    }

    default void resume() {
    }

    default void resize(int width, int height) {
    }

    /** Logic — chạy đúng {@code FIXED_DT}, không phụ thuộc FPS. */
    void fixedUpdate(float fixedDeltaTime);

    /**
     * Chỉ vẽ. {@code alpha} trong [0,1) — phần dư để nội suy giữa tick trước và tick hiện tại.
     */
    void render(float alpha);

    default void dispose() {
    }
}

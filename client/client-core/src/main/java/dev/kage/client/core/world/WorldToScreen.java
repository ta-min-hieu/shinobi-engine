package dev.kage.client.core.world;

import com.badlogic.gdx.math.Vector3;

/**
 * Chiếu một điểm THẾ GIỚI sang toạ độ MÀN HÌNH, ghi đè tại chỗ (không cấp phát — Điều 31).
 *
 * <p>Tách thành interface riêng vì {@code WorldScreen} có HAI camera khả dĩ tuỳ nhánh render đang
 * hoạt động (camera 3D của {@code CharacterModelRenderer} theo ADR-031, hoặc camera 2D thay thế khi
 * chưa chạy pipeline art) — mọi thứ neo theo world nhưng làm việc ở không gian màn hình
 * ({@link FloatingCombatText} vẽ số damage, {@link MonsterPicker} bắt click chọn mục tiêu) chỉ cần
 * biết "chiếu hộ tôi", không cần biết camera nào.
 *
 * <p>⚠️ Toạ độ trả về theo quy ước GL: gốc ở góc DƯỚI-trái, {@code y} tăng lên trên — KHÁC toạ độ
 * chuột của {@code InputProcessor} (gốc TRÊN-trái). Người gọi phải tự lật {@code y} trước khi so.
 */
interface WorldToScreen {
    void project(Vector3 worldPositionInOut);
}

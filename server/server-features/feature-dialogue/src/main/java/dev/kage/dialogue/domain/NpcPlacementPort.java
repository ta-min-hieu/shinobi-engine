package dev.kage.dialogue.domain;

import java.util.List;

/**
 * Đọc {@code npc_spawn} (ADR-040) — adapter thật ở {@code server-database}.
 *
 * <p>Port RIÊNG, không gộp vào {@link NpcTemplatePort}: hai port đọc hai bảng khác nhau cho hai
 * người dùng khác nhau. {@link NpcTemplatePort} phục vụ gameplay hội thoại (tra script mỗi lần
 * người chơi bấm nói chuyện); port này phục vụ composition root lúc khởi động (world nào có NPC nào,
 * đứng ở đâu) và không bao giờ được gọi trong lúc chơi.
 */
public interface NpcPlacementPort {

    /**
     * Mọi NPC được đặt trong một world.
     *
     * @param worldCode {@link NpcPlacement#MAIN_WORLD_CODE} cho world chính
     * @return rỗng nếu world đó không đặt NPC nào — hợp lệ, không phải lỗi (dungeon/arena)
     */
    List<NpcPlacement> listPlacements(String worldCode);
}

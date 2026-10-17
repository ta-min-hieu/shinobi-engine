package dev.kage.dialogue.api;

import java.util.List;

import dev.kage.dialogue.domain.NpcPlacement;
import dev.kage.dialogue.domain.NpcPlacementPort;

/**
 * API công khai để hỏi "world này có NPC nào, đứng ở đâu" (ADR-040 D6) — module khác
 * ({@code server-gateway}) CHỈ gọi qua đây, không đọc bảng {@code npc_spawn} trực tiếp (Điều 24).
 *
 * <p>Đọc-thuần, không use case nào: đây là tra cứu config nạp MỘT LẦN lúc khởi động, cùng cách
 * {@code MonsterFacade} được đối xử (ADR-037 D3). Đổi vị trí NPC ⇒ migration + khởi động lại, không
 * có đường nóng.
 *
 * <p><b>Vì sao là facade thứ hai của module này thay vì một method trên {@link DialogueFacade}:</b>
 * hai facade phục vụ hai người gọi khác nhau ở hai thời điểm khác nhau — {@link DialogueFacade} bị
 * gọi từ handler packet trong lúc chơi, lớp này bị gọi đúng một lần từ composition root lúc khởi
 * động. Nhét chúng chung một lớp là để một cái tên gợi ý sai về lúc nào được gọi.
 *
 * <p>⚠️ Module này de facto là "nội dung tĩnh của NPC", không chỉ hội thoại — nó sở hữu
 * {@code npc_template} (từ V10) và nay cả {@code npc_spawn}. Tên module vì thế hơi lệch nghĩa. Tách
 * thành {@code feature-npc} là Alternatives B của ADR-040: rẻ (mọi thứ đã sau Port) nhưng thêm
 * module là việc /CLAUDE.md §4 bắt dừng hỏi, nên chờ chủ dự án.
 */
public final class NpcPlacementFacade {

    private final NpcPlacementPort placementPort;

    /** Dựng ở composition root. */
    public NpcPlacementFacade(NpcPlacementPort placementPort) {
        this.placementPort = placementPort;
    }

    /**
     * NPC được đặt trong {@code worldCode}.
     *
     * @return rỗng nếu world đó không đặt NPC nào (dungeon/arena) — không phải lỗi
     */
    public List<NpcPlacement> listPlacements(String worldCode) {
        return placementPort.listPlacements(worldCode);
    }
}

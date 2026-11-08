package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.server.world.NpcCatalogSnapshot;

/**
 * "Nhân vật này có đang đứng đủ gần NPC đó không" (quyết định chủ dự án 2026-10-11).
 *
 * <p>Trước đó nói chuyện với NPC, chọn lựa chọn thoại, và vì thế cả nhận quest lẫn mục tiêu quest "đi
 * gặp X", đều KHÔNG kiểm khoảng cách: client gửi mã NPC từ bất kỳ đâu là server chạy — một client sửa
 * đổi hoàn thành mục tiêu "đi gặp" mà không rời chỗ (Điều 15, server phán quyết).
 *
 * <p><b>Vì sao có {@link #POSITION_SLACK_METERS}.</b> Vị trí gateway biết là vị trí world chính báo ở
 * lần đổi hướng gần nhất và ở nhịp lấy mẫu 1 Hz ({@code WorldOutboundSink.onCharacterPositionSampled}),
 * nên với người đang chạy nó chậm tới một giây — tức tới 5 m ở tốc độ 5 m/s. Không cộng khoảng bù đó
 * thì người vừa chạy tới nơi và bấm ngay sẽ bị từ chối oan. Người đứng yên được đo chính xác (dừng lại
 * là một lần đổi hướng).
 *
 * <p>NPC không được đặt trong world chính thì không ai đứng gần nó được — một mã NPC có trong
 * {@code npc_template} mà chưa có dòng {@code npc_spawn} không nói chuyện được. Cùng một mã đặt ở
 * nhiều chỗ (tiếp dẫn sứ ở mỗi trạm) thì tính chỗ GẦN NHẤT.
 */
final class NpcProximity {

    /** Tầm tương tác với NPC, đo từ chỗ NPC đứng. */
    static final float INTERACTION_RANGE_METERS = 5f;

    /** Quãng tối đa vị trí đã biết có thể lệch khỏi vị trí thật: một nhịp lấy mẫu (1 s) × tốc độ chạy (5 m/s). */
    static final float POSITION_SLACK_METERS = 5f;

    private static final float MAX_KNOWN_DISTANCE_METERS = INTERACTION_RANGE_METERS + POSITION_SLACK_METERS;

    private final NpcCatalogSnapshot npcs;
    private final LongFunction<Optional<PositionSnapshot>> mainWorldPositionOf;
    private final Supplier<List<NpcCatalogSnapshot.NpcPlacementDefinition>> dynamicNpcs;

    /**
     * @param npcs NPC được đặt trong world chính
     * @param mainWorldPositionOf vị trí ở world chính của một nhân vật; rỗng nếu nhân vật đó hiện không
     *     đứng ở world chính (đang trong hầm ngục/đấu trường) — khi đó không NPC nào ở gần
     */
    NpcProximity(NpcCatalogSnapshot npcs, LongFunction<Optional<PositionSnapshot>> mainWorldPositionOf) {
        this(npcs, mainWorldPositionOf, List::of);
    }

    /** @param dynamicNpcs NPC ĐỘNG đang đứng ở world chính (ADR-131 — thương nhân du hành), đọc mỗi lần hỏi */
    NpcProximity(
            NpcCatalogSnapshot npcs,
            LongFunction<Optional<PositionSnapshot>> mainWorldPositionOf,
            Supplier<List<NpcCatalogSnapshot.NpcPlacementDefinition>> dynamicNpcs) {
        this.npcs = npcs;
        this.mainWorldPositionOf = mainWorldPositionOf;
        this.dynamicNpcs = dynamicNpcs;
    }

    boolean isWithinReach(long characterId, String npcCode) {
        Optional<PositionSnapshot> position = mainWorldPositionOf.apply(characterId);
        if (position.isEmpty()) {
            return false;
        }
        float x = position.get().x();
        float y = position.get().y();
        return anyClose(npcs.placements(), npcCode, x, y) || anyClose(dynamicNpcs.get(), npcCode, x, y);
    }

    /**
     * ADR-206 — mọi chỗ đứng NPC ở world chính (tĩnh rồi tới động), cho tự tìm đường; rỗng nếu nhân vật hiện không
     * đứng ở world chính. Chạy trên luồng blocking: vị trí có thể phải đọc từ DB.
     */
    Optional<List<NpcCatalogSnapshot.NpcPlacementDefinition>> placementsVisibleTo(long characterId) {
        if (mainWorldPositionOf.apply(characterId).isEmpty()) {
            return Optional.empty();
        }
        List<NpcCatalogSnapshot.NpcPlacementDefinition> all = new java.util.ArrayList<>(npcs.placements());
        all.addAll(dynamicNpcs.get());
        return Optional.of(all);
    }

    /** ADR-228 — mã mọi NPC đặt TĨNH ở world chính (không trùng). */
    java.util.Set<String> staticNpcCodes() {
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        npcs.placements().forEach(p -> codes.add(p.npcCode()));
        return codes;
    }

    private static boolean anyClose(
            List<NpcCatalogSnapshot.NpcPlacementDefinition> placements, String npcCode, float x, float y) {
        for (NpcCatalogSnapshot.NpcPlacementDefinition placement : placements) {
            if (placement.npcCode().equals(npcCode) && isClose(x - placement.posX(), y - placement.posY())) {
                return true;
            }
        }
        return false;
    }

    /** ADR-118 — đứng ở world chính, cách điểm {@code (x, y)} không quá {@code radius} mét. */
    boolean isNear(long characterId, float x, float y, float radius) {
        return mainWorldPositionOf
                .apply(characterId)
                .map(p -> {
                    float dx = p.x() - x;
                    float dy = p.y() - y;
                    return dx * dx + dy * dy <= radius * radius;
                })
                .orElse(false);
    }

    private static boolean isClose(float dx, float dy) {
        return dx * dx + dy * dy <= MAX_KNOWN_DISTANCE_METERS * MAX_KNOWN_DISTANCE_METERS;
    }
}

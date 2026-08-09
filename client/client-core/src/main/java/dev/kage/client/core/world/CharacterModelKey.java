package dev.kage.client.core.world;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;

/**
 * Ghép hệ phái + bậc trang phục thành khoá asset của model glTF — {@code char_<hệ>-<bậc>}, khớp cột
 * {@code asset_key} của bảng {@code art_asset} và tên file dưới {@code models/<tier>/}
 * ([ART-PIPELINE §8](docs/14-art/ART-PIPELINE.md),
 * [ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)).
 *
 * <p>Đây là chỗ DUY NHẤT biết quy ước đặt tên đó ở phía client. Ghép chuỗi rải rác trong
 * {@link WorldScreen} thì đổi quy ước là phải đi sửa nhiều nơi.
 */
final class CharacterModelKey {

    private static final String PREFIX = "char_";

    private CharacterModelKey() {}

    /**
     * @return khoá asset kiểu {@code char_hoa-b1}, luôn trỏ tới một model có thật.
     */
    static String of(Faction faction, CostumeTier costumeTier) {
        return PREFIX + factionSegment(faction) + "-" + costumeTierSegment(costumeTier);
    }

    // UNSPECIFIED/UNRECOGNIZED -> hoa: giống hệt cách server tự xử lý (GatewayChannelHandler
    // .toDomainFaction), và quan trọng hơn là nó luôn ra khoá TỒN TẠI. Trả về khoá không có file
    // thì WorldModelRenderer tắt hẳn nhánh 3D cho TOÀN BỘ phiên chơi, không chỉ cho nhân vật
    // đó — hỏng một nhân vật không đáng đổi lấy chuyện cả màn hình quay về hình hộp.
    private static String factionSegment(Faction faction) {
        return switch (faction) {
            case FACTION_THUY -> "thuy";
            case FACTION_PHONG -> "phong";
            case FACTION_LOI -> "loi";
            case FACTION_THO -> "tho";
            case FACTION_HOA, FACTION_UNSPECIFIED, UNRECOGNIZED -> "hoa";
        };
    }

    private static String costumeTierSegment(CostumeTier costumeTier) {
        return switch (costumeTier) {
            case COSTUME_TIER_B2 -> "b2";
            case COSTUME_TIER_B3 -> "b3";
            case COSTUME_TIER_B1, COSTUME_TIER_UNSPECIFIED, UNRECOGNIZED -> "b1";
        };
    }
}

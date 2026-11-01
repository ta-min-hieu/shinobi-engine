package dev.kage.server.gateway;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;

/**
 * Ngoại hình của một nhân vật đang kết nối — đúng những gì client cần để chọn model glTF phải nạp
 * ([ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)). KHÔNG chứa chỉ số
 * gameplay nào: đây thuần là dữ liệu hiển thị mà server phát cho nhân vật khác trong AoI.
 *
 * <p>Dùng thẳng enum protobuf thay vì enum domain riêng vì giá trị này chỉ đi từ DB ra dây, không
 * có logic nghiệp vụ nào ở giữa — thêm một tầng enum nữa chỉ để dịch qua lại là thừa.
 */
record CharacterAppearance(Faction faction, CostumeTier costumeTier, int level) {

    /**
     * ADR-132 (lô 25, chủ dự án chọn) — bậc trang phục theo CẢNH GIỚI, không theo trang bị: B2 từ Trúc Cơ (Cấp 19,
     * arc 2 Ngoại Môn), B3 từ Tử Phủ (Cấp 37, arc 3 Nội Môn). Trước đó là hằng số B1 dù art đã đủ cả ba bậc.
     */
    static final int SECOND_TIER_LEVEL = 19;

    /** Xem {@link #SECOND_TIER_LEVEL}. */
    static final int THIRD_TIER_LEVEL = 37;

    /**
     * ADR-140 — cấp mở đầu từng cảnh giới (Trúc Cơ, Tử Phủ, Kim Đan, Nguyên Anh, Hoá Thần); khớp
     * {@code BreakthroughHooks} và {@code CultivationRealm} phía client.
     */
    private static final int[] REALM_OPENING_LEVELS = {19, 37, 55, 73, 91};

    /** Bậc trang phục của một cấp. */
    static CostumeTier tierFor(int level) {
        if (level >= THIRD_TIER_LEVEL) {
            return CostumeTier.COSTUME_TIER_B3;
        }
        return level >= SECOND_TIER_LEVEL ? CostumeTier.COSTUME_TIER_B2 : CostumeTier.COSTUME_TIER_B1;
    }

    /** Chỉ số cảnh giới của một cấp (0 = Luyện Khí) — danh hiệu trên đầu chỉ đổi khi số này đổi. */
    static int realmIndex(int level) {
        int index = 0;
        for (int opening : REALM_OPENING_LEVELS) {
            if (level >= opening) {
                index++;
            }
        }
        return index;
    }

    /** Ngoại hình suy từ hệ phái thật (cột {@code character.faction}) và cấp hiện tại của nhân vật. */
    static CharacterAppearance of(Faction faction, int level) {
        return new CharacterAppearance(faction, tierFor(level), level);
    }

    /** ADR-140 — người đang nhìn phải được báo lại: bậc trang phục hoặc CẢNH GIỚI (danh hiệu trên đầu) đã đổi. */
    boolean differsVisiblyFrom(CharacterAppearance other) {
        return costumeTier != other.costumeTier || realmIndex(level) != realmIndex(other.level);
    }

    /**
     * Chưa biết ngoại hình — nhân vật đã ngắt kết nối trước khi AoI kịp phát event, hoặc lối đăng ký
     * chỉ quan tâm đường đi của packet (test). Client thấy {@code UNSPECIFIED} thì tự vẽ model thay
     * thế; đó là lựa chọn đúng hơn bỏ vẽ hẳn nhân vật.
     */
    static CharacterAppearance unknown() {
        return new CharacterAppearance(Faction.FACTION_UNSPECIFIED, CostumeTier.COSTUME_TIER_UNSPECIFIED, 0);
    }
}

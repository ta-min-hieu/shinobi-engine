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
record CharacterAppearance(Faction faction, CostumeTier costumeTier) {

    /**
     * Bậc trang phục mặc định. ⚠️ HIỆN LÀ HẰNG SỐ: art đã có đủ B1/B2/B3 cho cả 5 hệ
     * ([ART-PIPELINE §4.2](docs/14-art/ART-PIPELINE.md)) nhưng gameplay CHƯA có khái niệm "bậc
     * trang bị" nào để suy ra — bảng {@code equipment} không có cột bậc, và ánh xạ trang bị → bậc
     * là quyết định thiết kế game, không phải thứ tầng gateway được tự đặt ra. Khi khái niệm đó tồn
     * tại, đổi đúng ở đây là đủ; protocol đã sẵn field.
     */
    private static final CostumeTier DEFAULT_COSTUME_TIER = CostumeTier.COSTUME_TIER_B1;

    /** Ngoại hình suy từ hệ phái thật của nhân vật (cột {@code character.faction}, migration V2). */
    static CharacterAppearance of(Faction faction) {
        return new CharacterAppearance(faction, DEFAULT_COSTUME_TIER);
    }

    /**
     * Chưa biết ngoại hình — nhân vật đã ngắt kết nối trước khi AoI kịp phát event, hoặc lối đăng ký
     * chỉ quan tâm đường đi của packet (test). Client thấy {@code UNSPECIFIED} thì tự vẽ model thay
     * thế; đó là lựa chọn đúng hơn bỏ vẽ hẳn nhân vật.
     */
    static CharacterAppearance unknown() {
        return new CharacterAppearance(Faction.FACTION_UNSPECIFIED, CostumeTier.COSTUME_TIER_UNSPECIFIED);
    }
}

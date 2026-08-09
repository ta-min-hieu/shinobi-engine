package dev.kage.client.core.world;

import java.util.Locale;

/**
 * Đổi {@code monster_code} của server thành khoá asset model — {@code training_dummy} →
 * {@code mob_training-dummy}, khớp cột {@code asset_key} của bảng {@code art_asset} và tên file dưới
 * {@code models/<tier>/} ([ART-PIPELINE §8](docs/14-art/ART-PIPELINE.md)).
 *
 * <p>Song song với {@link CharacterModelKey}, và cùng một lý do tồn tại: đây là chỗ DUY NHẤT phía
 * client biết quy ước đặt tên này.
 *
 * <p>Quy tắc là một phép biến đổi thuần, KHÔNG phải bảng tra: prefix {@code mob_} (danh mục
 * {@code monster} của {@code art_category}, xem {@code AssetCategoryResolver}) cộng với mã quái đã
 * đổi {@code _} thành {@code -}. Bảng tra sẽ bắt phải sửa client mỗi lần thêm một con quái, mà con
 * quái mới thì đằng nào cũng chỉ là một dòng {@code monster_template} cộng một file {@code .glb};
 * client không nên là bên thứ ba phải đồng ý.
 *
 * <p>Hệ quả cố ý: mã quái CHƯA có art vẫn ra một khoá hợp lệ trỏ tới file không tồn tại
 * ({@code sample_boss} → {@code mob_sample-boss}, chưa vẽ tính đến 2026-08-09). Đó là trường hợp
 * {@link WorldModelRenderer#addMonster} xử lý được — vẽ hộp cho riêng con đó — nên ở đây không cần
 * (và không thể) biết con nào đã có model.
 */
final class MonsterModelKey {

    private static final String PREFIX = "mob_";

    private MonsterModelKey() {}

    /**
     * Ghép khoá asset cho một mã quái.
     *
     * @param monsterCode {@code monster_code} nhận từ {@code MonsterSpawnedEvent}.
     * @return khoá asset kiểu {@code mob_training-dummy} — có thể trỏ tới file chưa tồn tại, xem
     *     javadoc lớp.
     */
    static String of(String monsterCode) {
        // Locale.ROOT: mã quái là định danh ASCII của giao thức, không phải văn bản người đọc. Không
        // ghim locale thì máy đặt tiếng Thổ Nhĩ Kỳ đổi 'I' thành 'ı' và ra khoá không khớp file nào.
        return PREFIX + monsterCode.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

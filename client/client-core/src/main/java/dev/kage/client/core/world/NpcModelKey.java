package dev.kage.client.core.world;

import java.util.Locale;

/**
 * Đổi {@code npc_code} của server thành khoá asset model — {@code town_elder} →
 * {@code char_town-elder}, khớp cột {@code asset_key} của {@code art_asset} và tên file dưới
 * {@code models/<tier>/} ([ART-PIPELINE §8](docs/14-art/ART-PIPELINE.md)).
 *
 * <p>⚠️ Prefix là <b>{@code char_}</b>, KHÔNG phải {@code npc_} — trông như lỗi nhưng không phải.
 * Danh mục tầng 1 của NPC là {@code character} ({@code AssetCategoryResolver}: {@code char_*} vào
 * {@code character-player}, riêng {@code char_town-elder} vào {@code character-npc}), và
 * {@code NamingValidator.MODEL_PREFIXES} chỉ nhận {@code char_}/{@code mob_}. Đặt {@code npc_} là
 * pipeline art từ chối file ngay lúc upsert. Đó là lý do lớp này tồn tại tách khỏi
 * {@link MonsterModelKey} thay vì gộp một hàm với tham số prefix: hai loại thực thể liền kề nhau
 * trong gameplay lại nằm ở hai danh mục art khác nhau, và chỗ ghi lại điều bất ngờ đó phải là chỗ
 * nào đó cụ thể.
 */
final class NpcModelKey {

    private static final String PREFIX = "char_";

    private NpcModelKey() {}

    /**
     * Ghép khoá asset cho một mã NPC.
     *
     * @param npcCode {@code npc_code} nhận từ {@code NpcSpawnedEvent}.
     * @return khoá asset kiểu {@code char_town-elder} — có thể trỏ tới file chưa tồn tại, khi đó
     *     {@link NpcPresenter} vẽ hộp cho riêng NPC đó.
     */
    static String of(String npcCode) {
        // Locale.ROOT vì cùng lý do MonsterModelKey ghim nó — mã NPC là định danh ASCII của giao
        // thức, không phải văn bản người đọc.
        return PREFIX + npcCode.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

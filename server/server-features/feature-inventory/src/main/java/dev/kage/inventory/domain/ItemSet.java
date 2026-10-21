package dev.kage.inventory.domain;

import java.util.List;
import java.util.Set;

/**
 * Bộ pháp khí (ADR-065, GDD §5) — dữ liệu thuần đọc từ {@code item_set}/{@code item_set_member}/
 * {@code item_set_bonus} (V67). Thành viên tính theo {@code item_template.id}: hai món cùng mẫu chỉ
 * đếm MỘT (hai vòng tay giống nhau không thành "hai món của bộ").
 *
 * @param nameKey khoá i18n tên bộ
 * @param memberTemplateIds mẫu vật phẩm thuộc bộ
 * @param tiers các bậc thưởng, thứ tự bất kỳ
 */
public record ItemSet(String nameKey, Set<Long> memberTemplateIds, List<ItemSetTier> tiers) {

    public ItemSet {
        if (nameKey == null || nameKey.isBlank()) {
            throw new IllegalArgumentException("nameKey không được rỗng");
        }
        memberTemplateIds = Set.copyOf(memberTemplateIds);
        tiers = List.copyOf(tiers);
    }
}

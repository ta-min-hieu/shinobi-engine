package dev.kage.inventory.domain;

import java.util.List;

/** Tra cứu vật phẩm (ADR-279): giá cửa hàng NPC + các loại quái rơi ra món đó. Chỉ đọc, thông tin công khai. */
public interface ItemLookupPort {

    /** Một nguồn rơi. */
    record DropSource(String monsterCode, String monsterNameKey, int chancePercent) {}

    /**
     * Giá + nguồn rơi.
     *
     * @param buyPrice giá mua ở cửa hàng NPC (0 = cửa hàng không bán)
     * @param sellPrice giá cửa hàng thu lại (0 = không thu)
     */
    record Lookup(long buyPrice, long sellPrice, List<DropSource> drops) {}

    /** Theo id mẫu; mẫu không tồn tại ⇒ giá 0, không nguồn rơi. */
    Lookup lookup(long itemTemplateId);
}

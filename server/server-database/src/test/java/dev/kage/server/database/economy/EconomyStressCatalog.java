package dev.kage.server.database.economy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.inventory.domain.EquipmentSlotType;

/**
 * Bảng tra vật phẩm đọc MỘT lần lúc dựng bài kiểm (dữ liệu seed của migration, không đổi trong lúc
 * chạy). {@code buyable} là các loại bộ lái được phép mua — cố ý hẹp (rẻ + đủ dạng: xếp chồng,
 * trang bị, tiêu hao, Bảo Vệ) để túi đồ đầy/vơi liên tục chứ không cạn vàng sau vài lệnh.
 */
record EconomyStressCatalog(Map<Long, Template> templates, List<Long> buyable, Map<String, Long> idByCode) {

    EconomyStressCatalog {
        templates = Map.copyOf(templates);
        buyable = List.copyOf(buyable);
        idByCode = Map.copyOf(idByCode);
    }

    Template template(long templateId) {
        Template template = templates.get(templateId);
        if (template == null) {
            throw new IllegalStateException("Loại vật phẩm lạ trong túi: " + templateId);
        }
        return template;
    }

    long idOf(String code) {
        Long id = idByCode.get(code);
        if (id == null) {
            throw new IllegalStateException("Không có item_template code=" + code);
        }
        return id;
    }

    /** Một dòng {@code item_template} — các cột bài kiểm cần. */
    record Template(long id, String code, int maxStack, Optional<EquipmentSlotType> equipSlot) {}
}

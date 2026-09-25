package dev.kage.inventory.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Mặc trang bị (GAMEPLAY.md §7, DATABASE.md §2.2). Điều phối: đọc túi đồ (item có tồn tại, thuộc
 * đúng nhân vật không — Điều 24, không tin {@code itemId} từ client) → đọc {@code item_template}
 * (đúng slot không, đủ CẤP không — ADR-036 D4) → gọi domain
 * {@link dev.kage.inventory.domain.Equipment#equip} → lưu.
 *
 * <p><b>{@code characterLevel} do CHỖ GỌI truyền vào</b>, không phải một port mới của module này:
 * cấp nhân vật thuộc {@code feature-character}, và {@code GatewayChannelHandler} vốn đã là nơi nối
 * hai module (cùng khuôn với {@code computeEquipmentBonus} → {@code WorldFacade}). Con số đó luôn
 * đọc từ DB phía server, KHÔNG bao giờ nhận từ client (Điều 15/24).
 *
 * <p><b>Cả lượt chạy trong MỘT giao dịch, dòng vật phẩm bị khoá trước khi xác thực</b> (sửa
 * 2026-09-24) — xem {@link InventoryPort#findOwnedItemForUpdate}. Thứ tự khoá: khoá túi đồ của
 * nhân vật (từ 2026-09-25 — DATABASE.md §5.1), dòng {@code inventory_item}, rồi mới tới
 * {@code equipment_slot}, cùng chiều với giao dịch/cửa hàng/thư (khoá túi đồ, khoá dòng vật phẩm,
 * chỉ ĐỌC {@code equipment_slot}) — không tạo vòng khoá mới.
 *
 * <p>Constructor Injection tường minh — không DI container, không field injection (ADR-009).
 */
public final class EquipItemUseCase {

    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final EquipmentPort equipmentPort;
    private final TransactionRunner tx;

    public EquipItemUseCase(
            InventoryPort inventoryPort,
            ItemTemplatePort itemTemplatePort,
            EquipmentPort equipmentPort,
            TransactionRunner tx) {
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.equipmentPort = equipmentPort;
        this.tx = tx;
    }

    /**
     * Mặc {@code itemId} vào {@code targetSlot}, hoặc trả về lý do KHÔNG mặc được.
     *
     * @param characterLevel cấp HIỆN TẠI của nhân vật, server tự đọc từ DB — so với
     *     {@code item_template.required_level} (ADR-036 D4)
     */
    public EquipItemResult execute(long characterId, long itemId, EquipmentSlotType targetSlot, int characterLevel) {
        AtomicReference<EquipItemResult> result = new AtomicReference<>();
        tx.inTransaction((Transaction t) ->
                result.set(equipInTransaction(t, new EquipRequest(characterId, itemId, targetSlot, characterLevel))));
        return result.get();
    }

    private EquipItemResult equipInTransaction(Transaction t, EquipRequest request) {
        long characterId = request.characterId();
        long itemId = request.itemId();
        EquipmentSlotType targetSlot = request.targetSlot();
        Optional<ItemStack> owned = inventoryPort.findOwnedItemForUpdate(t, characterId, itemId);
        if (owned.isEmpty()) {
            // item không tồn tại HOẶC không thuộc characterId này — cùng lỗi (fail secure, SECURITY.md §4)
            return EquipItemResult.ITEM_NOT_FOUND;
        }
        ItemStack item = owned.get();

        ItemTemplate template = itemTemplatePort
                .findById(item.itemTemplateId())
                .orElseThrow(() -> new IllegalStateException(
                        "item_template không tồn tại cho itemTemplateId=" + item.itemTemplateId()));
        if (template.equipSlot() != targetSlot) {
            return EquipItemResult.WRONG_SLOT;
        }
        // ADR-036 D4 — GAMEPLAY.md §7 mô tả phép kiểm này từ lâu nhưng nó KHÔNG tồn tại được cho
        // tới khi có cột required_level: trước đó nhân vật Cấp 1 đủ vàng là mặc trọn bộ "Hạ Nhẫn".
        if (request.characterLevel() < template.requiredLevel()) {
            return EquipItemResult.LEVEL_TOO_LOW;
        }

        Equipment equipment = equipmentPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));
        equipment.equip(targetSlot, itemId);
        equipmentPort.save(t, equipment);
        return EquipItemResult.OK;
    }

    // Gói tham số — trần Checkstyle ParameterNumber là 4, cộng thêm Transaction là 5.
    private record EquipRequest(long characterId, long itemId, EquipmentSlotType targetSlot, int characterLevel) {}

    public enum EquipItemResult {
        OK,
        ITEM_NOT_FOUND,
        WRONG_SLOT,
        LEVEL_TOO_LOW
    }
}

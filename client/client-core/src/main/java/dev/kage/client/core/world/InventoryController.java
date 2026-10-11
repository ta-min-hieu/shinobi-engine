package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ListInventoryResponse;

/**
 * Trạng thái túi đồ + trang bị (ADR-035, opcode 507/508) — thuần Java, không libGDX/GL, test được
 * thẳng như {@link MailController} (UI.md §4: controller giữ trạng thái, widget chỉ vẽ).
 *
 * <p>Giữ đúng sáu thứ: <b>danh sách vật phẩm</b> server vừa trả về, <b>sức chứa</b> túi, <b>slot nào
 * đang mặc item nào</b>, <b>vật phẩm đang chọn</b> để xem chi tiết/mặc, <b>khoá idempotency của lệnh
 * cường hoá đang treo</b> (ADR-025 — cường hoá trừ vàng THẬT, cùng lý do {@link ShopController} giữ
 * khoá của lệnh Mua/Bán), và <b>cờ "có tiêu Bảo Vệ không"</b> người chơi đang bật.
 *
 * <p><b>Item đang mặc VẪN nằm trong {@code items}.</b> {@code equipment_slot.inventory_item_id}
 * (V5__add_equipment.sql) tham chiếu tới {@code inventory_item} chứ KHÔNG xoá dòng đó khi mặc, nên
 * tra tên một item đang mặc là tra thẳng {@link #byId(long)} bằng {@code item_id} lấy từ
 * {@link #equippedItemId(EquipmentSlot)} — không cần packet nào khác ngoài {@code ListInventoryResponse}.
 *
 * <p><b>Server là nguồn chân lý duy nhất.</b> Lớp này KHÔNG tự sửa danh sách sau khi mặc/gỡ —
 * {@link InventoryCoordinator} luôn hỏi lại {@code ListInventoryRequest} sau MỌI lệnh đổi trạng thái
 * (Điều 15), cùng nguyên tắc {@link MailController}/{@link GuildController}.
 *
 * <p><b>⚠️ Khác Mail: KHÔNG có enum outcome thật.</b> {@code EquipItemResponse}/
 * {@code UnequipItemResponse} chỉ có {@code bool success} + {@code string error_key} tự do
 * (NETWORK.md §107 — error_key ĐÃ LÀ khoá i18n do server chọn sẵn, không phải một mã cần tra bảng).
 * {@link #errorMessageKey(String)} vì vậy không tra một enum như
 * {@code MailController.claimMessageKey}, mà đối chiếu với đúng tập ba chuỗi THẬT SỰ server phát ra
 * hôm nay ({@code GatewayChannelHandler#toErrorKey}, {@code #handleUnequipItemCommandBlocking}) —
 * đóng vai trò tương đương một bảng tra outcome, và {@code InventoryControllerTest} gác đúng ba
 * chuỗi này thay vì gác một enum không tồn tại.
 */
final class InventoryController {

    /**
     * Ba {@code error_key} THẬT server phát cho Equip/Unequip hôm nay — xem javadoc lớp về việc
     * inventory.proto không có enum outcome cho hai lệnh này.
     */
    private static final Set<String> KNOWN_ERROR_KEYS = Set.of(
            "error.item_not_found",
            "error.wrong_equipment_slot",
            "error.slot_not_equipped",
            "error.equip_level_too_low",
            // Cường hoá món đang chào trong giao dịch (quyết định chủ dự án 2026-09-29) — tới bằng
            // ErrorResponse, không phải EnhanceOutcome, xem InventoryCoordinator#onEnhanceRejected.
            "error.item_in_trade",
            // Cường hoá món không phải trang bị (quyết định chủ dự án 2026-10-11) — cùng đường trên.
            "error.item_not_enhanceable");

    /**
     * Một dòng túi đồ.
     *
     * @param itemId {@code inventory_item.id} THẬT — gửi thẳng lại server ở {@code EquipItemCommand}.
     * @param itemTemplateId khoá của {@code item_template}, tra catalog (ADR-035) ra tên/slot/icon.
     * @param slotIndex vị trí trong túi — {@link InventoryController} sắp theo trường này.
     */
    record Item(long itemId, long itemTemplateId, int quantity, int enhanceLevel, int slotIndex) {}

    private final List<Item> items = new ArrayList<>();
    private int capacity;
    private final Map<EquipmentSlot, Long> equipped = new EnumMap<>(EquipmentSlot.class);

    /** Item đang xem chi tiết trong khung, 0 = chưa chọn cái nào. */
    private long selectedItemId;

    /** Xem {@link #beginEnhance(long)} — 0 / rỗng khi không có lệnh cường hoá nào đang treo. */
    private long pendingEnhanceItemId;

    private String pendingEnhanceKey = "";

    /** {@code sequenceId} của gói Cường hoá gửi gần nhất — chỉ có nghĩa khi đang có lệnh treo. */
    private int pendingEnhanceSequenceId;

    /** Xem {@link #useProtection()} — mặc định TẮT. */
    private boolean useProtection;

    /**
     * Ảnh chụp túi mới từ server — THAY hoàn toàn, không trộn với bản cũ, cùng luật
     * {@link MailController#onMailList}.
     */
    void onInventory(ListInventoryResponse response) {
        items.clear();
        for (InventoryItemView view : response.getItemsList()) {
            items.add(new Item(
                    view.getItemId(),
                    view.getItemTemplateId(),
                    view.getQuantity(),
                    view.getEnhanceLevel(),
                    view.getSlotIndex()));
        }
        items.sort(Comparator.comparingInt(Item::slotIndex));
        capacity = response.getCapacity();
        equipped.clear();
        for (EquippedSlotView view : response.getEquippedList()) {
            equipped.put(view.getSlot(), view.getItemId());
        }
        if (byId(selectedItemId).isEmpty()) {
            selectedItemId = 0L;
        }
    }

    /** Vật phẩm trong túi, đã sắp theo {@code slotIndex}. */
    List<Item> items() {
        return List.copyOf(items);
    }

    /** Sức chứa túi — hằng số phía server (V4), gửi kèm để client vẽ đủ ô trống mà không phải đoán. */
    int capacity() {
        return capacity;
    }

    /** Tra một item theo id THẬT — dùng cho CẢ túi lẫn trang bị (xem javadoc lớp). */
    Optional<Item> byId(long itemId) {
        if (itemId == 0L) {
            return Optional.empty();
        }
        for (Item item : items) {
            if (item.itemId() == itemId) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** {@code item_id} đang mặc ở slot, 0 nếu trống — 0 không đụng hàng vì {@code inventory_item.id} bắt đầu từ 1. */
    long equippedItemId(EquipmentSlot slot) {
        return equipped.getOrDefault(slot, 0L);
    }

    /** Vật phẩm đang xem chi tiết, rỗng khi chưa chọn (hoặc vừa biến mất khỏi túi sau một lần hỏi lại). */
    Optional<Item> selected() {
        return byId(selectedItemId);
    }

    /** Chọn một item để xem chi tiết/mặc. Id lạ thì KHÔNG đổi gì — danh sách có thể đã đổi dưới chân cú bấm. */
    void select(long itemId) {
        if (byId(itemId).isPresent()) {
            selectedItemId = itemId;
        }
    }

    /**
     * Khoá i18n của một {@code error_key} do server gửi kèm response THẤT BẠI.
     *
     * @return {@link Optional#empty()} khi {@code error_key} lạ (server MỚI HƠN client, đã thêm
     *     nhánh lỗi mới) — chỗ gọi dịch nó thành {@code inventory.error.unknown} thay vì hiện thẳng
     *     một chuỗi chưa có bản dịch, cùng lý do {@link MailController#claimMessageKey}.
     */
    static Optional<String> errorMessageKey(String serverErrorKey) {
        return KNOWN_ERROR_KEYS.contains(serverErrorKey) ? Optional.of(serverErrorKey) : Optional.empty();
    }

    // ---------------------------------------------------------------------------------------------
    // Cường hoá (inventory.proto, opcode 505/506 — GAMEPLAY.md §7.1).
    //
    // Client KHÔNG giữ bảng chi phí/tỉ lệ: EnhancementTable sống phía server và không có packet nào
    // gửi nó sang đây, nên hiện một con số ở client là bịa (Điều 24). Kết quả một lần cường hoá đọc
    // được HẾT từ EnhanceOutcome, kể cả nhánh "thất bại nhưng Bảo Vệ đã chặn rớt cấp".
    // ---------------------------------------------------------------------------------------------

    /**
     * Có tiêu "Bảo Vệ" khi cường hoá thất bại hay không. MẶC ĐỊNH TẮT: bùa là vật phẩm tiêu hao mua
     * bằng vàng, bật sẵn hộ người chơi là tiêu tài sản của họ mà họ không chọn.
     */
    boolean useProtection() {
        return useProtection;
    }

    /** Người chơi bấm công tắc Bảo Vệ — trả về trạng thái MỚI. */
    boolean toggleProtection() {
        useProtection = !useProtection;
        return useProtection;
    }

    /**
     * Khoá idempotency cho một lệnh Cường hoá (ADR-025) — cùng luật {@code ShopController#beginBuy}:
     * bấm lại trên ĐÚNG item đang treo là "tự thử lại cùng một lần cường hoá" nên giữ nguyên khoá cũ;
     * bấm sau khi lệnh cũ đã có kết luận ({@link #onEnhanceSettled}) là một canh bạc MỚI và phải có
     * khoá mới.
     *
     * <p>Cờ Bảo Vệ CỐ TÌNH không nằm trong danh tính của khoá (khác cặp {@code (item, quantity)} của
     * lệnh Mua): gạt công tắc rồi bấm lại trong lúc lệnh cũ chưa có kết luận vẫn là cùng MỘT lần
     * cường hoá, và server đã quyết theo cờ của gói ĐẦU TIÊN nó nhận — cấp khoá mới lúc đó chỉ tạo ra
     * lần trừ vàng thứ hai.
     */
    String beginEnhance(long itemId) {
        if (pendingEnhanceItemId != itemId || pendingEnhanceKey.isEmpty()) {
            pendingEnhanceItemId = itemId;
            pendingEnhanceKey = UUID.randomUUID().toString();
        }
        return pendingEnhanceKey;
    }

    /** Lệnh Cường hoá đang treo đã có kết luận — xoá khoá kể cả khi hỏng, cùng {@code ShopController}. */
    void onEnhanceSettled() {
        pendingEnhanceItemId = 0L;
        pendingEnhanceKey = "";
    }

    /** Gói Cường hoá vừa gửi đi mang {@code sequenceId} này — xem {@link #isPendingEnhance(int)}. */
    void onEnhanceSent(int sequenceId) {
        pendingEnhanceSequenceId = sequenceId;
    }

    /**
     * {@code ErrorResponse} có {@code related_sequence_id} này là lời từ chối của lệnh Cường hoá đang
     * treo không. Opcode lỗi dùng chung cả giao thức, nên chỉ nhận đúng gói của mình — nuốt hết là
     * cướp lỗi của luồng khác (cùng luật {@code FactionCoordinator#onError}).
     */
    boolean isPendingEnhance(int relatedSequenceId) {
        return !pendingEnhanceKey.isEmpty() && relatedSequenceId == pendingEnhanceSequenceId;
    }

    /** Vật phẩm của lệnh Cường hoá đang treo — 0 khi không có lệnh nào treo. Chỉ dùng để test. */
    long pendingEnhanceItemId() {
        return pendingEnhanceItemId;
    }

    /**
     * Bảng tra khoá i18n cho từng {@code EnhanceOutcome} — cùng cách {@code ShopController} làm, và
     * cùng lý do: một biểu thức switch phủ hết một enum protobuf tự nó vượt trần
     * {@code CyclomaticComplexity} của PMD.
     *
     * <p>{@code UNSPECIFIED}/{@code UNRECOGNIZED} CỐ TÌNH vắng mặt: cả hai rơi vào
     * {@link Optional#empty()} và chỗ gọi dịch thành {@code inventory.error.unknown} — kết quả của một
     * server MỚI HƠN client, chỉ có thể nói ra là không hiểu, không đoán bừa (Điều 24).
     */
    private static final Map<EnhanceOutcome, String> ENHANCE_MESSAGE_KEYS = Map.of(
            EnhanceOutcome.ENHANCE_OUTCOME_SUCCESS, "inventory.enhance.success",
            EnhanceOutcome.ENHANCE_OUTCOME_FAILED_NO_DROP, "inventory.enhance.failed_no_drop",
            EnhanceOutcome.ENHANCE_OUTCOME_FAILED_DROPPED, "inventory.enhance.failed_dropped",
            EnhanceOutcome.ENHANCE_OUTCOME_FAILED_PROTECTED, "inventory.enhance.failed_protected",
            EnhanceOutcome.ENHANCE_OUTCOME_ITEM_NOT_FOUND, "inventory.enhance.item_not_found",
            EnhanceOutcome.ENHANCE_OUTCOME_MAX_LEVEL, "inventory.enhance.max_level",
            EnhanceOutcome.ENHANCE_OUTCOME_INSUFFICIENT_GOLD, "inventory.enhance.insufficient_gold",
            EnhanceOutcome.ENHANCE_OUTCOME_DUPLICATE_REQUEST, "inventory.enhance.duplicate_request");

    /** Xem {@link #ENHANCE_MESSAGE_KEYS} về hai giá trị cố tình không có mặt trong bảng. */
    static Optional<String> enhanceMessageKey(EnhanceOutcome outcome) {
        return Optional.ofNullable(ENHANCE_MESSAGE_KEYS.get(outcome));
    }

    /**
     * Rời màn hình/mất kết nối — túi của một phiên chơi không được sống sót sang phiên sau.
     *
     * <p>Cờ Bảo Vệ cũng về mặc định TẮT: đó là một ý định của phiên chơi vừa rồi, không phải một
     * thiết lập người chơi đã lưu.
     */
    void clear() {
        items.clear();
        capacity = 0;
        equipped.clear();
        selectedItemId = 0L;
        useProtection = false;
        onEnhanceSettled();
    }
}

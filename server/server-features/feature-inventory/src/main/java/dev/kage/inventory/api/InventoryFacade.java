package dev.kage.inventory.api;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GetItemCatalogUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.ListInventoryUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemCatalog;
import dev.kage.inventory.domain.ItemStack;

/**
 * API công khai của {@code feature-inventory} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code inventory_item}/{@code equipment_slot}/{@code drop_table*} trực
 * tiếp (Điều 24).
 *
 * <p>⚠️ <b>Race THẬT tìm được 2026-09-20, giống hệt hình dạng bug đã vá ở {@code PvpRatingPort}
 * cùng ngày, nhưng NẶNG hơn.</b> Bốn use case — {@link AddItemUseCase}, {@link GrantLootUseCase},
 * {@link EnhanceItemUseCase}, {@link UseItemUseCase} — đều đọc TOÀN BỘ {@code Inventory} của 1
 * nhân vật, sửa trong bộ nhớ, rồi lưu lại CẢ CHỒNG qua {@code InventoryPort#save} —
 * {@code PostgresInventoryAdapter#save} coi mọi dòng KHÔNG có mặt trong chồng vừa lưu là "đã bị
 * gỡ" rồi <b>XOÁ THẬT</b> (không chỉ "mất cập nhật" như bug PvP — ở đây một món đồ hoàn toàn có
 * thể biến mất khỏi DB).
 *
 * <p>{@code GrantLootUseCase} chạy trên {@code ConnectedCharacterRegistry.lootExecutor}
 * (virtual-thread-per-task, MỘT tác vụ MỚI cho MỖI con quái chết — KHÔNG tuần tự hoá theo nhân
 * vật), khác hẳn 3 use case kia vốn chạy qua lệnh client trên {@code authExecutor} (tự nhiên tuần
 * tự vì một kết nối chỉ có một lệnh đang xử lý). Giết 2 quái gần nhau (rất bình thường), hoặc giết
 * 1 quái RỒI LẬP TỨC dùng đan dược (cũng bình thường — combo "đánh rồi hồi máu"), đều có thể cho 2
 * lệnh này chạy THẬT SỰ song song — lệnh nào lưu chồng SAU sẽ xoá mất phần mà lệnh kia vừa thêm.
 *
 * <p>{@link EquipItemUseCase}/{@link UnequipItemUseCase} KHÔNG dính lỗ này — chúng chỉ ĐỌC
 * {@code Inventory} để xác thực rồi ghi qua {@code EquipmentPort} (bảng {@code equipment_slot}
 * nhỏ, không phải chồng {@code inventory_item}). Vẫn khoá CHUNG cho nhất quán (đơn giản hơn phải
 * nhớ "2 method này miễn khoá") — cái giá là không đáng kể, đây là hành động hiếm/theo nhịp người.
 * ⚠️ Khoá này chỉ tuần tự hoá các lệnh ĐI QUA facade này; giao dịch/cửa hàng/thư xoá hoặc đổi chủ
 * dòng vật phẩm bằng adapter riêng của feature đó, KHÔNG qua đây — nên mặc đồ còn phải khoá chính
 * dòng đó trong DB (2026-09-24, {@code InventoryPort#findOwnedItemForUpdate}).
 *
 * <p>Khoá {@link #withCharacterLock} bọc NGOÀI mọi method có sửa túi đồ — không sửa gì bên trong
 * các use case, không đổi hành vi khi chỉ có MỘT lệnh tại một thời điểm (trường hợp tuyệt đại đa
 * số). 3 method chỉ ĐỌC ({@link #computeEquipmentBonus}, {@link #listInventory},
 * {@link #itemCatalog}) KHÔNG cần khoá — Postgres tự bảo đảm một giao dịch {@code save()} không
 * bao giờ để lộ trạng thái nửa vời (đọc thấy TRƯỚC hoặc SAU trọn vẹn, không bao giờ ở giữa).
 */
public final class InventoryFacade {

    /**
     * Một khoá/nhân vật — {@link ReentrantLock}, KHÔNG {@code synchronized}: cả 2 nhóm gọi vào đây
     * đều là Virtual Thread (authExecutor/lootExecutor, cùng lý do các nơi khác trong dự án dùng
     * Virtual Thread cho blocking I/O), và {@code synchronized} có thể ghim Virtual Thread vào
     * carrier thread trong lúc chờ khoá — {@code ReentrantLock} thì không. Không bao giờ dọn map
     * này: số nhân vật hữu hạn theo tổng tài khoản đã tạo, một {@code ReentrantLock} rỗng nặng vài
     * chục byte — không đáng đổi lấy rủi ro dọn nhầm lúc khoá đang được giữ.
     */
    private final ConcurrentHashMap<Long, ReentrantLock> locksByCharacterId = new ConcurrentHashMap<>();

    private <T> T withCharacterLock(long characterId, Supplier<T> action) {
        ReentrantLock lock = locksByCharacterId.computeIfAbsent(characterId, id -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private final AddItemUseCase addItemUseCase;
    private final GrantLootUseCase grantLootUseCase;
    private final EquipItemUseCase equipItemUseCase;
    private final UnequipItemUseCase unequipItemUseCase;
    private final ComputeEquipmentBonusUseCase computeEquipmentBonusUseCase;
    private final EnhanceItemUseCase enhanceItemUseCase;
    private final UseItemUseCase useItemUseCase;
    private final ListInventoryUseCase listInventoryUseCase;
    private final GetItemCatalogUseCase getItemCatalogUseCase;

    public InventoryFacade(
            ItemAcquisitionUseCases acquisitionUseCases,
            EquipmentUseCases equipmentUseCases,
            ItemActionUseCases actionUseCases,
            InventoryQueryUseCases queryUseCases) {
        this.addItemUseCase = acquisitionUseCases.addItemUseCase();
        this.grantLootUseCase = acquisitionUseCases.grantLootUseCase();
        this.equipItemUseCase = equipmentUseCases.equipItemUseCase();
        this.unequipItemUseCase = equipmentUseCases.unequipItemUseCase();
        this.computeEquipmentBonusUseCase = equipmentUseCases.computeEquipmentBonusUseCase();
        this.enhanceItemUseCase = actionUseCases.enhanceItemUseCase();
        this.useItemUseCase = actionUseCases.useItemUseCase();
        this.listInventoryUseCase = queryUseCases.listInventoryUseCase();
        this.getItemCatalogUseCase = queryUseCases.getItemCatalogUseCase();
    }

    /**
     * Thêm item vào túi nhân vật.
     *
     * @return {@link Result#FULL} nếu túi đầy (không ném exception — túi đầy là bình thường)
     */
    public Result addItem(long characterId, ItemStack stack) {
        return withCharacterLock(characterId, () -> switch (addItemUseCase.execute(characterId, stack)) {
            case OK -> Result.OK;
            case FULL -> Result.FULL;
        });
    }

    /**
     * Roll {@code dropTableCode} và cộng item rơi được vào túi {@code characterId} (GAMEPLAY.md
     * §5 "Rơi đồ" — quái chết gọi hàm này với người hạ gục).
     *
     * @return item THẬT SỰ đã cộng được (rỗng nếu không rơi gì hoặc túi đầy hết) — dùng để báo
     *     client biết nhặt được gì
     */
    public List<ItemStack> grantLoot(long characterId, String dropTableCode) {
        return withCharacterLock(characterId, () -> grantLootUseCase.execute(characterId, dropTableCode));
    }

    /**
     * Mặc item {@code itemId} (ID thật của {@code inventory_item}, không phải vị trí) vào
     * {@code targetSlot} — server tự kiểm tra item thuộc đúng nhân vật + đúng loại slot + nhân vật
     * đủ cấp ({@code item_template.required_level}, ADR-036 D4).
     *
     * @param characterLevel cấp hiện tại của nhân vật, chỗ gọi ĐỌC TỪ DB (không nhận từ client) —
     *     xem javadoc {@link dev.kage.inventory.application.EquipItemUseCase} về lý do không dùng
     *     port riêng
     */
    public EquipResult equipItem(long characterId, long itemId, EquipmentSlotType targetSlot, int characterLevel) {
        return withCharacterLock(
                characterId, () -> switch (equipItemUseCase.execute(characterId, itemId, targetSlot, characterLevel)) {
                    case OK -> EquipResult.OK;
                    case ITEM_NOT_FOUND -> EquipResult.ITEM_NOT_FOUND;
                    case WRONG_SLOT -> EquipResult.WRONG_SLOT;
                    case LEVEL_TOO_LOW -> EquipResult.LEVEL_TOO_LOW;
                });
    }

    /** Gỡ trang bị khỏi {@code slot} — item vẫn còn trong túi đồ, không bị xoá. */
    public UnequipResult unequipItem(long characterId, EquipmentSlotType slot) {
        return withCharacterLock(characterId, () -> switch (unequipItemUseCase.execute(characterId, slot)) {
            case OK -> UnequipResult.OK;
            case NOT_EQUIPPED -> UnequipResult.NOT_EQUIPPED;
        });
    }

    /**
     * Tổng chỉ số cộng thêm từ TOÀN BỘ trang bị đang mặc (GAMEPLAY.md §2) — gọi lại sau
     * {@link #equipItem}/{@link #unequipItem} thành công VÀ lúc attach nhân vật vào world (server-
     * gateway nối sang {@code WorldFacade.applyEquipmentBonus}, server-world không tự biết module này).
     */
    public EquipmentBonus computeEquipmentBonus(long characterId) {
        return computeEquipmentBonusUseCase.execute(characterId);
    }

    /**
     * Cường hoá item {@code itemId} (ID thật, không phải vị trí) — tốn gold, tỉ lệ thành công giảm
     * dần theo cấp (GAMEPLAY.md §7). Từ +10 trở lên, thất bại rớt 1 cấp ({@code FAILED_DROPPED})
     * TRỪ KHI {@code useProtection=true} VÀ đủ "Bảo Vệ" trong túi ({@code FAILED_PROTECTED}, §7.1).
     * {@code idempotencyKey} (ADR-025) — client sinh UUID, giữ nguyên khi tự retry.
     */
    public EnhanceResult enhanceItem(long characterId, long itemId, boolean useProtection, String idempotencyKey) {
        return withCharacterLock(characterId, () -> switch (enhanceItemUseCase.execute(
                characterId, itemId, useProtection, idempotencyKey)) {
            case SUCCESS -> EnhanceResult.SUCCESS;
            case FAILED_NO_DROP -> EnhanceResult.FAILED_NO_DROP;
            case FAILED_DROPPED -> EnhanceResult.FAILED_DROPPED;
            case FAILED_PROTECTED -> EnhanceResult.FAILED_PROTECTED;
            case ITEM_NOT_FOUND -> EnhanceResult.ITEM_NOT_FOUND;
            case MAX_LEVEL -> EnhanceResult.MAX_LEVEL;
            case INSUFFICIENT_GOLD -> EnhanceResult.INSUFFICIENT_GOLD;
            case DUPLICATE_REQUEST -> EnhanceResult.DUPLICATE_REQUEST;
        });
    }

    /**
     * Dùng MỘT món tiêu hao (ADR-046 D5 — đan dược). Trừ trong túi + ghi audit; hiệu ứng THẬT
     * (cộng máu) do chỗ gọi áp qua {@code WorldFacade} — {@code feature-inventory} không biết
     * {@code server-world} tồn tại.
     *
     * @return hiệu ứng cần áp + số cái còn lại; {@code outcome != OK} nghĩa là không trừ gì cả
     */
    public UseItemUseCase.UseItemResult useItem(long characterId, long inventoryItemId) {
        return withCharacterLock(characterId, () -> useItemUseCase.execute(characterId, inventoryItemId));
    }

    /**
     * Ảnh chụp TOÀN BỘ túi đồ + trang bị đang mặc của {@code characterId} (ADR-035 Decision-1) —
     * nguồn DUY NHẤT để client biết {@code inventory_item.id} thật sau khi đăng nhập lại, thứ mà
     * {@code EquipItemCommand}/{@code SellItemCommand} bắt buộc phải có.
     */
    public InventorySnapshot listInventory(long characterId) {
        return listInventoryUseCase.execute(characterId);
    }

    /**
     * Toàn bộ catalog vật phẩm + {@code version} là hash nội dung do server tính (ADR-035
     * Decision-2, phương án A′) — gateway so {@code version} này với bản client gửi lên để quyết
     * gửi lại hay báo {@code unchanged}. Có cache trong bộ nhớ, xem
     * {@link GetItemCatalogUseCase} cho điều kiện làm mất hiệu lực.
     */
    public ItemCatalog itemCatalog() {
        return getItemCatalogUseCase.execute();
    }

    public enum Result {
        OK,
        FULL
    }

    public enum EquipResult {
        OK,
        ITEM_NOT_FOUND,
        WRONG_SLOT,
        /** Nhân vật chưa đủ {@code item_template.required_level} (ADR-036 D4). */
        LEVEL_TOO_LOW
    }

    public enum UnequipResult {
        OK,
        NOT_EQUIPPED
    }

    public enum EnhanceResult {
        SUCCESS,
        FAILED_NO_DROP,
        FAILED_DROPPED,
        FAILED_PROTECTED,
        ITEM_NOT_FOUND,
        MAX_LEVEL,
        INSUFFICIENT_GOLD,
        DUPLICATE_REQUEST
    }
}

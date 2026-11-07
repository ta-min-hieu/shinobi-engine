package dev.kage.inventory.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.CompanionPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.inventory.domain.MountPolicy;
import dev.kage.inventory.domain.PetPolicy;
import dev.kage.inventory.domain.PetProgress;
import dev.kage.inventory.domain.TradeOfferPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Dùng MỘT vật phẩm tiêu hao (ADR-046 D5 — đan dược hồi máu).
 *
 * <p>Module này chỉ làm nửa việc: kiểm quyền sở hữu, kiểm món có dùng được không, TRỪ một cái +
 * ghi audit trong một transaction, rồi TRẢ VỀ hiệu ứng cần áp. Nửa còn lại (cộng máu vào entity
 * ECS) là của {@code server-world}, và chỗ nối hai nửa là {@code server-gateway} — đúng ranh giới
 * đã có: {@code feature-inventory} không biết {@code WorldFacade} tồn tại.
 *
 * <p><b>KHÔNG kiểm "đang đầy máu thì đừng uống".</b> Máu sống trong ECS, mà đường tới đó là bất
 * đồng bộ (hàng đợi tick) — hỏi rồi mới trừ là một cuộc đua, và trả lời sai chiều nào cũng tệ hơn
 * hiện trạng. Client tự chặn nút "Dùng" khi đầy máu (nó có sẵn số máu trong HUD); nếu người chơi
 * vẫn ép gói qua thì mất một viên đan của CHÍNH họ — phiền, nhưng không khai thác được gì.
 *
 * <p>Không có {@code idempotencyKey} như {@code EnhanceItemUseCase}: retry của lệnh này không mất
 * tài sản có giá trị lớn (một viên đan), và nó KHÔNG trừ gold. Thêm bảng idempotency cho mỗi lần
 * uống thuốc là chi phí ghi DB cho mọi trận đánh.
 */
public final class UseItemUseCase {

    /** Điều gì đã xảy ra + hiệu ứng caller phải áp (chỉ có nghĩa khi {@link Outcome#OK}). */
    public record UseItemResult(Outcome outcome, int remainingQuantity, ItemUseEffect effect, int value) {

        static UseItemResult failed(Outcome outcome) {
            return new UseItemResult(outcome, 0, null, 0);
        }
    }

    public enum Outcome {
        OK,
        /** Không có món đó trong túi CỦA NGƯỜI GỬI — gộp "không tồn tại" với "của người khác" (SECURITY.md). */
        ITEM_NOT_FOUND,
        /** Món có thật nhưng {@code item_template.use_effect} là NULL (trang bị, nguyên liệu...). */
        NOT_USABLE,
        /**
         * Mọi cái của dòng đó đang được chào trong một phiên giao dịch còn mở (dòng 80, chủ dự án
         * duyệt 2026-10-18). Chồng 5 viên chào 2 thì vẫn uống được 3 viên kia.
         */
        ITEM_IN_TRADE,
        /** Linh Thú Đan (ADR-078) nhưng không mặc linh thú nào. Không trừ viên đan. */
        NO_PET_EQUIPPED,
        /** Linh thú đang mặc chưa tới {@link PetPolicy#EVOLVE_LEVEL}. Không trừ viên đan. */
        PET_LEVEL_TOO_LOW,
        /** Linh thú đang mặc đã ở bậc cao nhất. Không trừ viên đan. */
        PET_ALREADY_EVOLVED,
        /** Túi đã đạt sức chứa tối đa (ADR-088). Không trừ Túi Càn Khôn. */
        BAG_AT_MAX,
        /** Đan x2 EXP (ADR-164) nhưng thời gian còn lại đã chạm trần. Không trừ viên đan. */
        EXP_BUFF_AT_MAX,
        /** Thức ăn linh thú (ADR-201) nhưng linh thú đang mặc đã ở cấp tối đa. Không trừ món. */
        PET_AT_MAX_LEVEL,
        /** Tinh thạch phi kiếm (ADR-203) nhưng không mặc phi kiếm. Không trừ món. */
        NO_MOUNT_EQUIPPED,
        /** Phi kiếm đang mặc đã ở cấp tối đa. Không trừ món. */
        MOUNT_AT_MAX_LEVEL
    }

    /**
     * Gom port — CODING-STANDARDS {@code ParameterNumber} max=4, cùng cách {@link EnhancePorts}. Port
     * thứ năm ({@link TradeOfferPort}, dòng 80, 2026-10-18) là thứ đẩy constructor qua trần.
     */
    public record Ports(
            InventoryPort inventoryPort,
            ItemTemplatePort itemTemplatePort,
            AuditPort auditPort,
            TradeOfferPort tradeOfferPort,
            CompanionPort companionPort) {

        /** Không có linh thú (mọi chỗ dựng trước ADR-078) — Linh Thú Đan luôn {@code NO_PET_EQUIPPED}. */
        public Ports(
                InventoryPort inventoryPort,
                ItemTemplatePort itemTemplatePort,
                AuditPort auditPort,
                TradeOfferPort tradeOfferPort) {
            this(inventoryPort, itemTemplatePort, auditPort, tradeOfferPort, CompanionPort.NONE);
        }
    }

    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final AuditPort auditPort;
    private final TradeOfferPort tradeOfferPort;
    private final CompanionPort companionPort;
    private final TransactionRunner tx;

    public UseItemUseCase(Ports ports, TransactionRunner tx) {
        this.inventoryPort = ports.inventoryPort();
        this.itemTemplatePort = ports.itemTemplatePort();
        this.auditPort = ports.auditPort();
        this.tradeOfferPort = ports.tradeOfferPort();
        this.companionPort = ports.companionPort();
        this.tx = tx;
    }

    public UseItemResult execute(long characterId, long inventoryItemId) {
        AtomicReference<UseItemResult> result = new AtomicReference<UseItemResult>();
        // Đọc + trừ + lưu trong CÙNG một transaction, đọc sau khoá túi đồ (sửa 2026-09-25): đọc ngoài
        // transaction thì một lần mua hàng/nhận thư commit chen giữa bị save() coi là "đã gỡ" và xoá
        // thật. Các nhánh từ chối không ghi gì — transaction commit rỗng.
        tx.inTransaction((Transaction t) -> result.set(useInTransaction(t, characterId, inventoryItemId)));
        return result.get();
    }

    private UseItemResult useInTransaction(Transaction t, long characterId, long inventoryItemId) {
        Inventory inventory = inventoryPort
                .findByCharacterForUpdate(t, characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        ItemStack item = inventory.findItem(inventoryItemId).orElse(null);
        if (item == null) {
            return UseItemResult.failed(Outcome.ITEM_NOT_FOUND);
        }
        Optional<ItemTemplate> template = itemTemplatePort.findById(item.itemTemplateId());
        if (template.isEmpty() || !template.get().isUsable()) {
            return UseItemResult.failed(Outcome.NOT_USABLE);
        }
        // Hỏi SAU khoá túi đồ (findByCharacterForUpdate): đặt lời chào cũng lấy khoá này nên hai bên
        // xếp hàng — xem TradeOfferPort.
        if (item.quantity() - tradeOfferPort.offeredQuantity(t, characterId, inventoryItemId) < 1) {
            return UseItemResult.failed(Outcome.ITEM_IN_TRADE);
        }
        Optional<Outcome> refused = applyEffect(t, characterId, template.get(), inventory);
        if (refused.isPresent()) {
            return UseItemResult.failed(refused.get());
        }

        inventory.consumeOne(inventoryItemId);
        int remaining = inventory
                .findItem(inventoryItemId)
                .map(ItemStack::quantity)
                .orElse(0); // ô túi biến mất khi dùng cái cuối cùng
        inventoryPort.save(t, inventory);
        // Audit dù không đụng gold: đây là đường DUY NHẤT làm vật phẩm biến mất mà không có
        // giao dịch nào đối chiếu được, nên "vì sao mất viên đan" phải trả lời được.
        auditPort.log(t, characterId, "USE_ITEM", detailJson(inventoryItemId, item.itemTemplateId()));
        return new UseItemResult(
                Outcome.OK,
                remaining,
                template.get().useEffect(),
                template.get().useValue());
    }

    /**
     * Hiệu ứng phải ghi TRONG giao dịch dùng (trước khi trừ món) — từ chối thì chưa ghi gì, món chưa bị trừ. Tách khỏi
     * {@link #useInTransaction} vì trần CyclomaticComplexity.
     */
    private Optional<Outcome> applyEffect(Transaction t, long characterId, ItemTemplate template, Inventory inventory) {
        return switch (template.useEffect()) {
            case EXPAND_BAG ->
                inventoryPort.expandCapacity(t, characterId, template.useValue())
                        ? Optional.empty()
                        : Optional.of(Outcome.BAG_AT_MAX);
            case DOUBLE_EXP ->
                inventoryPort.extendDoubleExp(t, characterId, template.useValue())
                        ? Optional.empty()
                        : Optional.of(Outcome.EXP_BUFF_AT_MAX);
            case EVOLVE_PET -> evolveEquippedPet(t, characterId);
            case FEED_PET -> feedEquippedPet(t, characterId, template.useValue());
            case UPGRADE_MOUNT -> upgradeWornMount(t, characterId, inventory);
            case HEAL_HP -> Optional.empty();
        };
    }

    /**
     * ADR-078 — tiến hoá linh thú đang mặc, dưới khoá túi đồ rồi khoá dòng {@code pet_state}. Từ chối thì chưa ghi
     * gì (viên đan chưa bị trừ); đồng ý thì ghi bậc mới, viên đan bị trừ ngay sau trong cùng transaction.
     */
    private Optional<Outcome> evolveEquippedPet(Transaction t, long characterId) {
        Optional<PetProgress> pet = companionPort.lockEquippedPet(t, characterId);
        if (pet.isEmpty()) {
            return Optional.of(Outcome.NO_PET_EQUIPPED);
        }
        if (pet.get().stage() >= PetPolicy.MAX_STAGE) {
            return Optional.of(Outcome.PET_ALREADY_EVOLVED);
        }
        if (pet.get().level() < PetPolicy.EVOLVE_LEVEL) {
            return Optional.of(Outcome.PET_LEVEL_TOO_LOW);
        }
        companionPort.setPetStage(t, pet.get().inventoryItemId(), pet.get().stage() + 1);
        return Optional.empty();
    }

    /**
     * ADR-201 — cho linh thú đang mặc ăn: từ chối thì chưa ghi gì; đồng ý thì EXP ghi cùng transaction với việc trừ
     * món.
     */
    private Optional<Outcome> feedEquippedPet(Transaction t, long characterId, int expAmount) {
        Optional<PetProgress> pet = companionPort.lockEquippedPet(t, characterId);
        if (pet.isEmpty()) {
            return Optional.of(Outcome.NO_PET_EQUIPPED);
        }
        if (pet.get().level() >= PetPolicy.MAX_LEVEL) {
            return Optional.of(Outcome.PET_AT_MAX_LEVEL);
        }
        // Rỗng = linh thú vừa bị tháo giữa hai câu (không xảy ra dưới khoá túi đồ, nhưng không cho trừ món oan).
        return companionPort.grantPetExp(t, characterId, expAmount).isPresent()
                ? Optional.empty()
                : Optional.of(Outcome.NO_PET_EQUIPPED);
    }

    /**
     * ADR-203 — nâng phi kiếm đang mặc một cấp trên CHÍNH aggregate túi đồ đang khoá (lưu cùng lúc với việc trừ tinh
     * thạch ⇒ không ghi đè nhau). Từ chối thì chưa đổi gì.
     */
    private Optional<Outcome> upgradeWornMount(Transaction t, long characterId, Inventory inventory) {
        Optional<ItemStack> mount =
                companionPort.wornMountItemId(t, characterId).flatMap(inventory::findItem);
        if (mount.isEmpty()) {
            return Optional.of(Outcome.NO_MOUNT_EQUIPPED);
        }
        ItemStack current = mount.get();
        if (current.enhanceLevel() >= MountPolicy.MAX_LEVEL) {
            return Optional.of(Outcome.MOUNT_AT_MAX_LEVEL);
        }
        inventory.replaceItem(
                new ItemStack(current.id(), current.itemTemplateId(), current.quantity(), current.enhanceLevel() + 1));
        return Optional.empty();
    }

    private static String detailJson(long inventoryItemId, long itemTemplateId) {
        return "{\"itemId\":" + inventoryItemId + ",\"itemTemplateId\":" + itemTemplateId + "}";
    }
}

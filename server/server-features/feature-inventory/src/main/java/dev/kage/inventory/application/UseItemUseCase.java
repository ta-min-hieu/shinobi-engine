package dev.kage.inventory.application;

import java.util.Optional;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.ItemUseEffect;
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
        NOT_USABLE
    }

    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final AuditPort auditPort;
    private final TransactionRunner tx;

    public UseItemUseCase(
            InventoryPort inventoryPort, ItemTemplatePort itemTemplatePort, AuditPort auditPort, TransactionRunner tx) {
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.auditPort = auditPort;
        this.tx = tx;
    }

    public UseItemResult execute(long characterId, long inventoryItemId) {
        Inventory inventory = inventoryPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        ItemStack item = inventory.findItem(inventoryItemId).orElse(null);
        if (item == null) {
            return UseItemResult.failed(Outcome.ITEM_NOT_FOUND);
        }
        Optional<ItemTemplate> template = itemTemplatePort.findById(item.itemTemplateId());
        if (template.isEmpty() || !template.get().isUsable()) {
            return UseItemResult.failed(Outcome.NOT_USABLE);
        }

        inventory.consumeOne(inventoryItemId);
        int remaining = inventory
                .findItem(inventoryItemId)
                .map(ItemStack::quantity)
                .orElse(0); // ô túi biến mất khi dùng cái cuối cùng
        tx.inTransaction((Transaction t) -> {
            inventoryPort.save(t, inventory);
            // Audit dù không đụng gold: đây là đường DUY NHẤT làm vật phẩm biến mất mà không có
            // giao dịch nào đối chiếu được, nên "vì sao mất viên đan" phải trả lời được.
            auditPort.log(t, characterId, "USE_ITEM", detailJson(inventoryItemId, item.itemTemplateId()));
        });
        return new UseItemResult(
                Outcome.OK,
                remaining,
                template.get().useEffect(),
                template.get().useValue());
    }

    private static String detailJson(long inventoryItemId, long itemTemplateId) {
        return "{\"itemId\":" + inventoryItemId + ",\"itemTemplateId\":" + itemTemplateId + "}";
    }
}

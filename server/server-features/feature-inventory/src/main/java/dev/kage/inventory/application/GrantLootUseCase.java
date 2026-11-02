package dev.kage.inventory.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.LootRoller;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Quái chết → roll {@code drop_table} → cộng item rơi được vào túi người hạ gục (GAMEPLAY.md §5).
 * Điều phối: roll (domain, thuần) → load túi → cộng từng item → lưu 1 lần.
 *
 * <p>⚠️ Item rơi mà túi đầy giữa chừng thì BỊ MẤT (không rơi xuống đất) — feature-inventory/
 * CLAUDE.md ghi rõ đây là hạn chế tạm thời của lát cắt này, không phải hành vi cuối cùng.
 */
public final class GrantLootUseCase {

    private final DropTablePort dropTablePort;
    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final TransactionRunner tx;
    private final AuditPort auditPort;
    private final IntSupplier rollCount;
    // ADR-158 — % cơ hội tung bảng rơi thêm một lượt, RIÊNG từng người hạ gục (quẻ bói). Mặc định 0.
    private final LongToIntFunction bonusRollPercent;

    /** Bản KHÔNG ghi audit — cho test chỉ quan tâm tới túi đồ. Production dùng {@link #auditedBy}. */
    public GrantLootUseCase(
            DropTablePort dropTablePort,
            InventoryPort inventoryPort,
            ItemTemplatePort itemTemplatePort,
            TransactionRunner tx) {
        this.dropTablePort = dropTablePort;
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.tx = tx;
        this.auditPort = NO_AUDIT;
        this.rollCount = GrantLootUseCase::oneRoll;
        this.bonusRollPercent = characterId -> 0;
    }

    private GrantLootUseCase(
            GrantLootUseCase source, AuditPort auditPort, IntSupplier rollCount, LongToIntFunction bonusRollPercent) {
        this.dropTablePort = source.dropTablePort;
        this.inventoryPort = source.inventoryPort;
        this.itemTemplatePort = source.itemTemplatePort;
        this.tx = source.tx;
        this.auditPort = auditPort;
        this.rollCount = rollCount;
        this.bonusRollPercent = bonusRollPercent;
    }

    /**
     * ADR-158 — {@code percent.applyAsInt(characterId)} % cơ hội tung bảng rơi THÊM một lượt cho riêng người hạ gục đó
     * (quẻ bói hôm nay). Module này không biết quẻ là gì, chỉ biết một con số 0–100; random vẫn sinh ở server.
     */
    public GrantLootUseCase withBonusRollPercent(LongToIntFunction percent) {
        return new GrantLootUseCase(this, auditPort, rollCount, percent);
    }

    /**
     * Số lượt roll bảng rơi cho MỖI con quái (ADR-067 — sự kiện nhân đôi rơi đồ). Hỏi lại mỗi lần giết;
     * module này không biết sự kiện là gì, chỉ biết một con số {@code >= 1}.
     */
    public GrantLootUseCase withRollCount(IntSupplier count) {
        return new GrantLootUseCase(this, auditPort, count, bonusRollPercent);
    }

    /**
     * Bản ghi một dòng {@code audit_log} {@value #AUDIT_ACTION} cho MỖI chồng đồ rơi thật sự vào túi
     * (quyết định chủ dự án 2026-10-11). Rơi đồ là nguồn sinh vật phẩm lớn nhất của game và trước đó
     * không để lại dấu nào: sổ vàng dựng lại trọn được từ audit, sổ vật phẩm thì không.
     *
     * <p>Ghi trong CÙNG transaction với lần lưu túi (luật của {@link AuditPort}). Một method thay vì
     * tham số constructor thứ năm: constructor đã chạm trần 4 tham số của Checkstyle.
     */
    public GrantLootUseCase auditedBy(AuditPort port) {
        return new GrantLootUseCase(this, port, rollCount, bonusRollPercent);
    }

    /** {@code audit_log.action} của một chồng đồ rơi. */
    public static final String AUDIT_ACTION = "LOOT_GRANT";

    private static final AuditPort NO_AUDIT = (transaction, characterId, action, detailJson) -> {};

    /** 1 nếu trúng cơ hội tung thêm của người này (ADR-158), không thì 0 — random ở {@link LootRoller}. */
    private int bonusRoll(long characterId) {
        return LootRoller.rollsBonus(bonusRollPercent.applyAsInt(characterId)) ? 1 : 0;
    }

    /** Mặc định: một lượt roll mỗi con quái. */
    private static int oneRoll() {
        return 1;
    }

    /**
     * Roll bảng rơi rồi cộng vào túi người hạ gục.
     *
     * @return BIÊN LAI của những gì vừa cộng được: {@code id} là {@code inventory_item.id} THẬT
     *     (đọc từ {@code Inventory} mà {@link InventoryPort#save} trả về — object truyền vào
     *     {@code add()} vẫn còn {@code id == NEW_ITEM_ID}), client cần nó để mặc đồ
     *     ({@code EquipItemCommand}); còn {@code quantity} là SỐ VỪA NHẬN, <b>không phải</b> tổng
     *     của chồng.
     *     <p>⚠️ Phân biệt này chỉ thành ra quan trọng từ khi có gộp chồng (2026-09-05): nhặt 2 phi
     *     đao vào chồng đang có 15 thì {@code ItemGrantedEvent} phải báo "nhận được 2", không phải
     *     "nhận được 17". Trước đó mỗi lần rơi luôn tạo ô mới nên hai con số tình cờ bằng nhau.
     */
    public List<ItemStack> execute(long characterId, String dropTableCode) {
        return execute(characterId, dropTableCode, stack -> false).granted();
    }

    /**
     * Kết quả một lượt rơi đồ có tách: {@code granted} đã vào túi (biên lai như {@link #execute(long, String)}),
     * {@code diverted} KHÔNG vào túi ai — phía gọi tự chia (ADR-150 tung xúc xắc trong nhóm) rồi cấp bằng
     * {@link #grantRolled}.
     */
    public record Loot(List<ItemStack> granted, List<ItemStack> diverted) {

        public Loot {
            granted = List.copyOf(granted);
            diverted = List.copyOf(diverted);
        }
    }

    /**
     * Như {@link #execute(long, String)} nhưng chồng nào {@code divert} nhận thì để riêng, không cộng vào túi người hạ
     * gục (ADR-150). Roll vẫn ở đây, chỉ sinh ở server.
     */
    public Loot execute(long characterId, String dropTableCode, Predicate<ItemStack> divert) {
        List<DropTableEntry> entries = dropTablePort.findEntries(dropTableCode);
        List<ItemStack> rolled = new ArrayList<>(LootRoller.roll(entries));
        for (int extra = rollCount.getAsInt() - 1 + bonusRoll(characterId); extra > 0; extra--) {
            rolled.addAll(LootRoller.roll(entries));
        }
        List<ItemStack> drops = new ArrayList<>(rolled.size());
        List<ItemStack> diverted = new ArrayList<>();
        for (ItemStack stack : rolled) {
            (divert.test(stack) ? diverted : drops).add(stack);
        }
        return new Loot(place(characterId, dropTableCode, drops), diverted);
    }

    /**
     * Cấp một chồng đã tách ra ở {@link #execute(long, String, Predicate)} cho người thắng — cùng giao dịch, cùng
     * dòng audit {@value #AUDIT_ACTION} như đồ rơi thường.
     *
     * @return biên lai (rỗng nếu túi đầy — phía gọi tự gửi cách khác)
     */
    public List<ItemStack> grantRolled(long characterId, String dropTableCode, ItemStack stack) {
        return place(characterId, dropTableCode, List.of(stack));
    }

    private List<ItemStack> place(long characterId, String dropTableCode, List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return List.of();
        }

        // Đọc + cộng + lưu trong CÙNG một transaction, đọc sau khoá túi đồ (sửa 2026-09-25): đọc ngoài
        // transaction thì một lần mua hàng/nhận thư commit chen giữa bị save() coi là "đã gỡ" và xoá
        // thật. Khoá JVM của InventoryFacade không che được — cửa hàng/thư không đi qua facade.
        ArrayList<int[]> placements = new ArrayList<int[]>(); // {index, soLuongVuaNhan}
        AtomicReference<Inventory> savedHolder = new AtomicReference<Inventory>();
        tx.inTransaction((Transaction t) -> {
            Inventory inventory = inventoryPort
                    .findByCharacterForUpdate(t, characterId)
                    .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
            // Vị trí do chính Inventory.Added báo về — KHÔNG đoán bằng items().size() - 1 nữa: từ khi
            // có gộp chồng, item vừa cộng có thể nằm ở một ô CŨ chứ không phải ô cuối. save() giữ
            // nguyên thứ tự nên chỉ số này tra lại đúng item trong Inventory đã lưu.
            for (ItemStack stack : drops) {
                if (inventory.add(stack, maxStackOf(stack)) instanceof Inventory.Added added) {
                    placements.add(new int[] {added.index(), stack.quantity()});
                    // Chỉ chồng THẬT SỰ vào túi mới được ghi — túi đầy thì chồng đó bị bỏ, không có gì để ghi.
                    auditPort.log(t, characterId, AUDIT_ACTION, detailJson(dropTableCode, stack));
                }
            }
            if (!placements.isEmpty()) {
                savedHolder.set(inventoryPort.save(t, inventory));
            }
        });
        if (placements.isEmpty()) {
            return List.of();
        }
        List<ItemStack> savedItems = savedHolder.get().items();

        ArrayList<ItemStack> granted = new ArrayList<ItemStack>(placements.size());
        for (int[] placement : placements) {
            ItemStack saved = savedItems.get(placement[0]);
            // quantity = SỐ VỪA NHẬN (xem javadoc), id/enhanceLevel = của chồng thật trong DB.
            granted.add(new ItemStack(saved.id(), saved.itemTemplateId(), placement[1], saved.enhanceLevel()));
        }
        return List.copyOf(granted);
    }

    /** {@code source} là mã bảng rơi (= mã loài quái) — dữ liệu server, nhưng vẫn lọc về chữ/số/gạch dưới. */
    private static String detailJson(String dropTableCode, ItemStack stack) {
        String source = dropTableCode.replaceAll("[^A-Za-z0-9_]", "_");
        return "{\"source\":\"%s\",\"itemTemplateId\":%d,\"quantity\":%d}"
                .formatted(source, stack.itemTemplateId(), stack.quantity());
    }

    /** Thiếu template (dữ liệu hỏng) ⇒ coi như KHÔNG xếp chồng — an toàn hơn là gộp nhầm. */
    private int maxStackOf(ItemStack stack) {
        return itemTemplatePort
                .findById(stack.itemTemplateId())
                .map(template -> template.maxStack())
                .orElse(1);
    }
}

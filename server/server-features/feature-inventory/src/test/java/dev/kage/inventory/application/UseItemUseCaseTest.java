package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/** ADR-046 D5 — dùng đan dược: trừ MỘT cái, ghi audit, trả về hiệu ứng cho chỗ gọi áp. */
class UseItemUseCaseTest {

    private static final long CHARACTER_ID = 7L;
    private static final long PILL_ITEM_ID = 41L;
    private static final long PILL_TEMPLATE_ID = 900L;
    private static final long SWORD_TEMPLATE_ID = 100L;
    private static final int HEAL_HP = 50;

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    /** Fake = implementation in-memory THẬT (TESTING.md §4), cùng khuôn {@code AddItemUseCaseTest}. */
    private static final class FakeInventoryPort implements InventoryPort {
        private final Inventory existing;
        Inventory saved;

        FakeInventoryPort(Inventory existing) {
            this.existing = existing;
        }

        @Override
        public Optional<Inventory> findByCharacter(long characterId) {
            return Optional.of(existing);
        }

        @Override
        public Inventory save(Transaction tx, Inventory inventory) {
            this.saved = inventory;
            return inventory;
        }
    }

    private static ItemTemplatePort catalogPort() {
        return new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                if (itemTemplateId == PILL_TEMPLATE_ID) {
                    return Optional.of(new ItemTemplate(
                            PILL_TEMPLATE_ID, null, 20, 0, 0, 0, 0, 1, ItemUseEffect.HEAL_HP, HEAL_HP));
                }
                if (itemTemplateId == SWORD_TEMPLATE_ID) {
                    return Optional.of(
                            new ItemTemplate(SWORD_TEMPLATE_ID, EquipmentSlotType.WEAPON, 1, 5, 0, 0, 0, 1, null, 0));
                }
                return Optional.empty();
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return Optional.empty();
            }
        };
    }

    private static Inventory inventoryWith(ItemStack... stacks) {
        return new Inventory(CHARACTER_ID, 20, new ArrayList<>(List.of(stacks)));
    }

    @Test
    void shouldConsumeOneAndReturnTheHealEffect() {
        FakeInventoryPort inventoryPort =
                new FakeInventoryPort(inventoryWith(new ItemStack(PILL_ITEM_ID, PILL_TEMPLATE_ID, 3, 0)));
        List<String> audited = new ArrayList<>();
        AuditPort auditPort = (tx, characterId, action, detailJson) -> audited.add(action);
        UseItemUseCase useCase = new UseItemUseCase(inventoryPort, catalogPort(), auditPort, DIRECT_TX);

        UseItemUseCase.UseItemResult result = useCase.execute(CHARACTER_ID, PILL_ITEM_ID);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.OK);
        assertThat(result.effect()).isEqualTo(ItemUseEffect.HEAL_HP);
        assertThat(result.value()).isEqualTo(HEAL_HP);
        assertThat(result.remainingQuantity()).isEqualTo(2);
        assertThat(inventoryPort.saved).isNotNull();
        // Audit BẮT BUỘC: đây là đường duy nhất làm vật phẩm biến mất mà không có giao dịch nào
        // đối chiếu được (server/CLAUDE.md).
        assertThat(audited).containsExactly("USE_ITEM");
    }

    @Test
    void shouldRemoveTheStack_whenTheLastOneIsUsed() {
        FakeInventoryPort inventoryPort =
                new FakeInventoryPort(inventoryWith(new ItemStack(PILL_ITEM_ID, PILL_TEMPLATE_ID, 1, 0)));
        UseItemUseCase useCase = new UseItemUseCase(inventoryPort, catalogPort(), (tx, c, a, d) -> {}, DIRECT_TX);

        UseItemUseCase.UseItemResult result = useCase.execute(CHARACTER_ID, PILL_ITEM_ID);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.OK);
        assertThat(result.remainingQuantity()).isZero(); // ô túi biến mất, không phải "còn 0 cái"
        assertThat(inventoryPort.saved.items()).isEmpty();
    }

    /**
     * Món của NGƯỜI KHÁC (hoặc không tồn tại) đi ra cùng một kết quả: {@code findByCharacter} chỉ
     * trả túi của người gửi, nên câu trả lời không rò rỉ "món đó có thật, chỉ không phải của bạn"
     * (SECURITY.md §4 — chống IDOR).
     */
    @Test
    void shouldRejectAnItemThatIsNotInTheSendersBag() {
        FakeInventoryPort inventoryPort = new FakeInventoryPort(inventoryWith());
        UseItemUseCase useCase = new UseItemUseCase(inventoryPort, catalogPort(), (tx, c, a, d) -> {}, DIRECT_TX);

        UseItemUseCase.UseItemResult result = useCase.execute(CHARACTER_ID, PILL_ITEM_ID);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.ITEM_NOT_FOUND);
        assertThat(inventoryPort.saved).isNull(); // KHÔNG mở transaction nào
    }

    @Test
    void shouldRejectAnItemWithNoUseEffect_withoutConsumingIt() {
        FakeInventoryPort inventoryPort =
                new FakeInventoryPort(inventoryWith(new ItemStack(55L, SWORD_TEMPLATE_ID, 1, 0)));
        UseItemUseCase useCase = new UseItemUseCase(inventoryPort, catalogPort(), (tx, c, a, d) -> {}, DIRECT_TX);

        UseItemUseCase.UseItemResult result = useCase.execute(CHARACTER_ID, 55L);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.NOT_USABLE);
        assertThat(inventoryPort.saved).isNull(); // thanh kiếm KHÔNG được bốc hơi vì bấm nhầm
    }

    /**
     * Template biến mất khỏi DB (dữ liệu hỏng) phải ra "không dùng được", KHÔNG được ném và cũng
     * KHÔNG được trừ món — cùng luật {@code AddItemUseCase} áp cho template thiếu.
     */
    @Test
    void shouldRejectSilently_whenTheTemplateIsMissing() {
        FakeInventoryPort inventoryPort = new FakeInventoryPort(inventoryWith(new ItemStack(66L, 12345L, 1, 0)));
        UseItemUseCase useCase = new UseItemUseCase(inventoryPort, catalogPort(), (tx, c, a, d) -> {}, DIRECT_TX);

        UseItemUseCase.UseItemResult result = useCase.execute(CHARACTER_ID, 66L);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.NOT_USABLE);
        assertThat(inventoryPort.saved).isNull();
    }
}

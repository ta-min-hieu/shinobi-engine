package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnhanceItemUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long ITEM_ID = 42L;
    private static final long TEMPLATE_ID = 100L;

    static final class FakeInventoryPort implements InventoryPort {
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

    /** Luôn cho đủ gold hoặc luôn từ chối — điều khiển được từ test, không cần DB thật. */
    static final class FakeGoldPort implements GoldPort {
        private final boolean sufficientGold;
        boolean deductCalled;

        FakeGoldPort(boolean sufficientGold) {
            this.sufficientGold = sufficientGold;
        }

        @Override
        public boolean deductGold(Transaction tx, long characterId, long amount) {
            deductCalled = true;
            return sufficientGold;
        }
    }

    static final class FakeAuditPort implements AuditPort {
        String loggedAction;
        String loggedDetail;

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            this.loggedAction = action;
            this.loggedDetail = detailJson;
        }
    }

    private static final dev.kage.inventory.domain.TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldReturnItemNotFound_whenItemDoesNotBelongToCharacter() {
        var inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of()));
        var goldPort = new FakeGoldPort(true);
        var auditPort = new FakeAuditPort();
        var useCase = new EnhanceItemUseCase(inventoryPort, goldPort, auditPort, DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, 999L);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.ITEM_NOT_FOUND);
        assertThat(goldPort.deductCalled).isFalse();
        assertThat(auditPort.loggedAction).isNull();
    }

    @Test
    void shouldReturnMaxLevel_whenItemAlreadyAtMaxEnhance() {
        var maxedItem = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, ItemStack.MAX_ENHANCE);
        var inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(maxedItem)));
        var goldPort = new FakeGoldPort(true);
        var auditPort = new FakeAuditPort();
        var useCase = new EnhanceItemUseCase(inventoryPort, goldPort, auditPort, DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.MAX_LEVEL);
        assertThat(goldPort.deductCalled).isFalse();
    }

    @Test
    void shouldReturnInsufficientGold_whenGoldPortRefusesDeduction() {
        var item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
        var inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
        var goldPort = new FakeGoldPort(false); // không đủ gold
        var auditPort = new FakeAuditPort();
        var useCase = new EnhanceItemUseCase(inventoryPort, goldPort, auditPort, DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.INSUFFICIENT_GOLD);
        assertThat(inventoryPort.saved).isNull(); // không đổi enhance_level khi không trả được tiền
        assertThat(auditPort.loggedAction).isNull(); // không ghi audit cho giao dịch không xảy ra
    }

    @Test
    void shouldChangeLevelAndAudit_whenEnoughGold() {
        // Tỉ lệ thành công +0→+1 là 95% (EnhancementTable) — không mock random, chỉ verify BẤT
        // KỂ thành công hay thất bại thì gold đã bị trừ và audit đã ghi (cùng tinh thần LootRoller:
        // verify hành vi, không ép định tất định 1 kết quả random cụ thể).
        var item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
        var inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
        var goldPort = new FakeGoldPort(true);
        var auditPort = new FakeAuditPort();
        var useCase = new EnhanceItemUseCase(inventoryPort, goldPort, auditPort, DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID);

        assertThat(result).isIn(
                EnhanceItemUseCase.EnhanceResult.SUCCESS, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP);
        assertThat(goldPort.deductCalled).isTrue();
        assertThat(auditPort.loggedAction).isEqualTo("ENHANCE_ITEM");
        assertThat(auditPort.loggedDetail).contains("\"itemId\":" + ITEM_ID);
        assertThat(inventoryPort.saved).isNotNull();
    }

    @Test
    void shouldDropOneLevel_onFailure_whenAtOrAboveDropThreshold() {
        // currentLevel=15... nhưng 15 là MAX nên dùng 14 (rate 5%, gần chắc chắn fail) để test
        // nhánh FAILED_DROPPED mà không cần mock random — chạy đủ nhiều lần để bắt được ít nhất
        // 1 lần fail (thống kê, cùng cách LootRollerTest đã dùng).
        boolean sawDropped = false;
        boolean sawSuccess = false;
        for (int i = 0; i < 200 && !(sawDropped && sawSuccess); i++) {
            var item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 14);
            var inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
            var useCase = new EnhanceItemUseCase(inventoryPort, new FakeGoldPort(true), new FakeAuditPort(), DIRECT_TX);

            var result = useCase.execute(CHARACTER_ID, ITEM_ID);
            if (result == EnhanceItemUseCase.EnhanceResult.FAILED_DROPPED) {
                sawDropped = true;
                assertThat(inventoryPort.saved.findItem(ITEM_ID).orElseThrow().enhanceLevel()).isEqualTo(13);
            } else if (result == EnhanceItemUseCase.EnhanceResult.SUCCESS) {
                sawSuccess = true;
                assertThat(inventoryPort.saved.findItem(ITEM_ID).orElseThrow().enhanceLevel()).isEqualTo(15);
            }
        }
        assertThat(sawDropped).isTrue();
    }
}

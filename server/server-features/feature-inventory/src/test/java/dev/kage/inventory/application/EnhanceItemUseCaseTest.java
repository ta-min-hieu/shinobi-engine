package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.IdempotencyKeyReusedException;
import dev.kage.inventory.domain.IdempotencyPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;

class EnhanceItemUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long ITEM_ID = 42L;
    private static final long TEMPLATE_ID = 100L;
    private static final long PROTECTION_TEMPLATE_ID = 900L;
    private static final long PROTECTION_ITEM_ID = 901L;

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

    /** {@code hasProtectionCharm=true} nghĩa là túi CÓ seed 1 item template {@code PROTECTION_TEMPLATE_ID}. */
    static final class FakeItemTemplatePort implements ItemTemplatePort {
        private final boolean hasProtectionCharm;

        FakeItemTemplatePort(boolean hasProtectionCharm) {
            this.hasProtectionCharm = hasProtectionCharm;
        }

        @Override
        public Optional<dev.kage.inventory.domain.ItemTemplate> findById(long itemTemplateId) {
            // Món của test là TRANG BỊ; Bảo Vệ là vật phẩm xếp chồng, không mặc được.
            dev.kage.inventory.domain.EquipmentSlotType slot = itemTemplateId == PROTECTION_TEMPLATE_ID
                    ? null
                    : dev.kage.inventory.domain.EquipmentSlotType.WEAPON;
            return Optional.of(
                    new dev.kage.inventory.domain.ItemTemplate(itemTemplateId, slot, 1, 0, 0, 0, 0, 1, null, 0));
        }

        @Override
        public Optional<Long> findIdByCode(String code) {
            return hasProtectionCharm && code.equals(EnhancementTable.PROTECTION_CHARM_CODE)
                    ? Optional.of(PROTECTION_TEMPLATE_ID)
                    : Optional.empty();
        }
    }

    static final class FakeIdempotencyPort implements IdempotencyPort {
        private final java.util.Set<String> usedKeys = new java.util.HashSet<>();

        @Override
        public void recordOrThrow(Transaction tx, String idempotencyKey) {
            if (!usedKeys.add(idempotencyKey)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
        }
    }

    private static final dev.kage.inventory.domain.TransactionRunner DIRECT_TX = action -> action.accept(null);
    private static final dev.kage.inventory.domain.TradeOfferPort NOT_IN_TRADE = (tx, characterId, itemId) -> 0;
    private static final String IDEMPOTENCY_KEY = "key-1";

    private static EnhanceItemUseCase newUseCase(
            FakeInventoryPort inventoryPort,
            FakeGoldPort goldPort,
            FakeAuditPort auditPort,
            FakeItemTemplatePort itemTemplatePort) {
        return newUseCase(new EnhancePorts(
                inventoryPort, goldPort, auditPort, itemTemplatePort, new FakeIdempotencyPort(), NOT_IN_TRADE));
    }

    private static EnhanceItemUseCase newUseCase(EnhancePorts ports) {
        return new EnhanceItemUseCase(ports, DIRECT_TX);
    }

    @Test
    void shouldReturnItemNotFound_whenItemDoesNotBelongToCharacter() {
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of()));
        FakeGoldPort goldPort = new FakeGoldPort(true);
        FakeAuditPort auditPort = new FakeAuditPort();
        EnhanceItemUseCase useCase = newUseCase(inventoryPort, goldPort, auditPort, new FakeItemTemplatePort(false));

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, 999L, false, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.ITEM_NOT_FOUND);
        assertThat(goldPort.deductCalled).isFalse();
        assertThat(auditPort.loggedAction).isNull();
    }

    /**
     * Quyết định chủ dự án 2026-09-29: món đang chào trong giao dịch còn mở không được cường hoá —
     * không trừ vàng, không đổi cấp, không ghi audit, và không TIÊU khoá idempotency (huỷ giao dịch
     * xong thử lại bằng chính khoá đó vẫn phải chạy).
     */
    @Test
    void shouldRejectWithoutSpendingAnything_whenItemIsOfferedInAnActiveTrade() {
        ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
        FakeGoldPort goldPort = new FakeGoldPort(true);
        FakeAuditPort auditPort = new FakeAuditPort();
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        boolean[] inTrade = {true};
        EnhanceItemUseCase useCase = newUseCase(new EnhancePorts(
                inventoryPort,
                goldPort,
                auditPort,
                new FakeItemTemplatePort(false),
                idempotencyPort,
                (tx, characterId, itemId) -> inTrade[0] && characterId == CHARACTER_ID && itemId == ITEM_ID ? 1 : 0));

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.ITEM_IN_TRADE);
        assertThat(goldPort.deductCalled).isFalse();
        assertThat(inventoryPort.saved).isNull();
        assertThat(auditPort.loggedAction).isNull();
        inTrade[0] = false;
        assertThat(useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY))
                .isIn(EnhanceItemUseCase.EnhanceResult.SUCCESS, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP);
    }

    /**
     * Chỉ trang bị mới cường hoá được (quyết định chủ dự án 2026-10-11). Trước đó cường hoá một dòng
     * vật phẩm xếp chồng là cả chồng lên cấp, và đồ đã cường hoá không gộp chồng lại được nữa. Phải
     * từ chối trước khi đụng tới vàng, túi đồ hay khoá idempotency.
     */
    @Test
    void shouldRefuseWithoutSpendingAnything_whenTheItemIsNotEquipment() {
        ItemStack charms = new ItemStack(ITEM_ID, PROTECTION_TEMPLATE_ID, 5, 0);
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(charms)));
        FakeGoldPort goldPort = new FakeGoldPort(true);
        FakeAuditPort auditPort = new FakeAuditPort();
        EnhanceItemUseCase useCase = newUseCase(inventoryPort, goldPort, auditPort, new FakeItemTemplatePort(true));

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.NOT_EQUIPMENT);
        assertThat(goldPort.deductCalled).isFalse();
        assertThat(inventoryPort.saved).isNull();
        assertThat(auditPort.loggedAction).isNull();
    }

    @Test
    void shouldReturnMaxLevel_whenItemAlreadyAtMaxEnhance() {
        ItemStack maxedItem = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, ItemStack.MAX_ENHANCE);
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(maxedItem)));
        FakeGoldPort goldPort = new FakeGoldPort(true);
        FakeAuditPort auditPort = new FakeAuditPort();
        EnhanceItemUseCase useCase = newUseCase(inventoryPort, goldPort, auditPort, new FakeItemTemplatePort(false));

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.MAX_LEVEL);
        assertThat(goldPort.deductCalled).isFalse();
    }

    @Test
    void shouldReturnInsufficientGold_whenGoldPortRefusesDeduction() {
        ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
        FakeGoldPort goldPort = new FakeGoldPort(false); // không đủ gold
        FakeAuditPort auditPort = new FakeAuditPort();
        EnhanceItemUseCase useCase = newUseCase(inventoryPort, goldPort, auditPort, new FakeItemTemplatePort(false));

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.INSUFFICIENT_GOLD);
        assertThat(inventoryPort.saved).isNull(); // không đổi enhance_level khi không trả được tiền
        assertThat(auditPort.loggedAction).isNull(); // không ghi audit cho giao dịch không xảy ra
    }

    @Test
    void shouldChangeLevelAndAudit_whenEnoughGold() {
        // Tỉ lệ thành công +0→+1 là 95% (EnhancementTable) — không mock random, chỉ verify BẤT
        // KỂ thành công hay thất bại thì gold đã bị trừ và audit đã ghi (cùng tinh thần LootRoller:
        // verify hành vi, không ép định tất định 1 kết quả random cụ thể).
        ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
        FakeGoldPort goldPort = new FakeGoldPort(true);
        FakeAuditPort auditPort = new FakeAuditPort();
        EnhanceItemUseCase useCase = newUseCase(inventoryPort, goldPort, auditPort, new FakeItemTemplatePort(false));

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        assertThat(result)
                .isIn(EnhanceItemUseCase.EnhanceResult.SUCCESS, EnhanceItemUseCase.EnhanceResult.FAILED_NO_DROP);
        assertThat(goldPort.deductCalled).isTrue();
        assertThat(auditPort.loggedAction).isEqualTo("ENHANCE_ITEM");
        assertThat(auditPort.loggedDetail).contains("\"itemId\":" + ITEM_ID);
        assertThat(inventoryPort.saved).isNotNull();
    }

    @Test
    void shouldReturnDuplicateRequest_whenIdempotencyKeyReused() {
        ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
        FakeGoldPort goldPort = new FakeGoldPort(true);
        FakeAuditPort auditPort = new FakeAuditPort();
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        EnhanceItemUseCase useCase = newUseCase(new EnhancePorts(
                inventoryPort, goldPort, auditPort, new FakeItemTemplatePort(false), idempotencyPort, NOT_IN_TRADE));
        useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(EnhanceItemUseCase.EnhanceResult.DUPLICATE_REQUEST);
    }

    @Test
    void shouldDropOneLevel_onFailure_whenAtOrAboveDropThresholdAndNoProtectionRequested() {
        // currentLevel=15... nhưng 15 là MAX nên dùng 14 (rate 5%, gần chắc chắn fail) để test
        // nhánh FAILED_DROPPED mà không cần mock random — chạy đủ nhiều lần để bắt được ít nhất
        // 1 lần fail (thống kê, cùng cách LootRollerTest đã dùng). useProtection=false nên dù túi
        // không có Bảo Vệ cũng không quan trọng.
        boolean sawDropped = false;
        boolean sawSuccess = false;
        for (int i = 0; i < 200 && !(sawDropped && sawSuccess); i++) {
            ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 14);
            FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
            EnhanceItemUseCase useCase = newUseCase(
                    inventoryPort, new FakeGoldPort(true), new FakeAuditPort(), new FakeItemTemplatePort(false));

            EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, false, IDEMPOTENCY_KEY);
            if (result == EnhanceItemUseCase.EnhanceResult.FAILED_DROPPED) {
                sawDropped = true;
                assertThat(inventoryPort.saved.findItem(ITEM_ID).orElseThrow().enhanceLevel())
                        .isEqualTo(13);
            } else if (result == EnhanceItemUseCase.EnhanceResult.SUCCESS) {
                sawSuccess = true;
                assertThat(inventoryPort.saved.findItem(ITEM_ID).orElseThrow().enhanceLevel())
                        .isEqualTo(15);
            }
        }
        assertThat(sawDropped).isTrue();
    }

    @Test
    void shouldConsumeProtectionAndKeepLevel_onFailure_whenProtectionRequestedAndOwned() {
        boolean sawProtected = false;
        for (int i = 0; i < 200 && !sawProtected; i++) {
            ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 14);
            ItemStack charm = new ItemStack(PROTECTION_ITEM_ID, PROTECTION_TEMPLATE_ID, 1, 0);
            FakeInventoryPort inventoryPort =
                    new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item, charm)));
            EnhanceItemUseCase useCase = newUseCase(
                    inventoryPort, new FakeGoldPort(true), new FakeAuditPort(), new FakeItemTemplatePort(true));

            EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, true, IDEMPOTENCY_KEY);
            if (result == EnhanceItemUseCase.EnhanceResult.FAILED_PROTECTED) {
                sawProtected = true;
                assertThat(inventoryPort.saved.findItem(ITEM_ID).orElseThrow().enhanceLevel())
                        .isEqualTo(14);
                // Bảo Vệ đã tiêu — quantity 1 nên dòng bị XOÁ hẳn khỏi túi.
                assertThat(inventoryPort.saved.findByTemplate(PROTECTION_TEMPLATE_ID))
                        .isEmpty();
            }
        }
        assertThat(sawProtected).isTrue();
    }

    @Test
    void shouldStillDropLevel_whenProtectionRequestedButNotOwned() {
        boolean sawDropped = false;
        for (int i = 0; i < 200 && !sawDropped; i++) {
            ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 14);
            FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item)));
            // FakeItemTemplatePort(true) tra được template id, nhưng túi KHÔNG có item nào khớp.
            EnhanceItemUseCase useCase = newUseCase(
                    inventoryPort, new FakeGoldPort(true), new FakeAuditPort(), new FakeItemTemplatePort(true));

            EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, true, IDEMPOTENCY_KEY);
            if (result == EnhanceItemUseCase.EnhanceResult.FAILED_DROPPED) {
                sawDropped = true;
                assertThat(inventoryPort.saved.findItem(ITEM_ID).orElseThrow().enhanceLevel())
                        .isEqualTo(13);
            }
        }
        assertThat(sawDropped).isTrue();
    }

    @Test
    void shouldNotConsumeProtection_whenSuccessDespiteUseProtectionTrue() {
        // Tỉ lệ +14→+15 chỉ 5% (gần chắc chắn fail) — dùng +0→+1 (95%, gần chắc chắn thành công)
        // để verify Bảo Vệ KHÔNG bị tiêu dù client bật cờ, vì roll ra thành công.
        boolean sawSuccess = false;
        for (int i = 0; i < 200 && !sawSuccess; i++) {
            ItemStack item = new ItemStack(ITEM_ID, TEMPLATE_ID, 1, 0);
            ItemStack charm = new ItemStack(PROTECTION_ITEM_ID, PROTECTION_TEMPLATE_ID, 1, 0);
            FakeInventoryPort inventoryPort =
                    new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of(item, charm)));
            EnhanceItemUseCase useCase = newUseCase(
                    inventoryPort, new FakeGoldPort(true), new FakeAuditPort(), new FakeItemTemplatePort(true));

            EnhanceItemUseCase.EnhanceResult result = useCase.execute(CHARACTER_ID, ITEM_ID, true, IDEMPOTENCY_KEY);
            if (result == EnhanceItemUseCase.EnhanceResult.SUCCESS) {
                sawSuccess = true;
                assertThat(inventoryPort.saved.findByTemplate(PROTECTION_TEMPLATE_ID))
                        .isPresent();
            }
        }
        assertThat(sawSuccess).isTrue();
    }
}

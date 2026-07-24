package dev.kage.shop.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.shop.domain.AuditPort;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.IdempotencyKeyReusedException;
import dev.kage.shop.domain.IdempotencyPort;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.ShopCatalogPort;
import dev.kage.shop.domain.ShopListing;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuyItemUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;

    static final class FakeCatalogPort implements ShopCatalogPort {
        private final ShopListing listing;

        FakeCatalogPort(ShopListing listing) {
            this.listing = listing;
        }

        @Override
        public Optional<ShopListing> findListing(long itemTemplateId) {
            return listing != null && listing.itemTemplateId() == itemTemplateId
                    ? Optional.of(listing) : Optional.empty();
        }

        @Override
        public List<ShopListing> findAll() {
            return listing == null ? List.of() : List.of(listing);
        }
    }

    static final class FakeGoldPort implements GoldPort {
        private final boolean sufficientGold;
        boolean deductCalled;
        long deductedAmount;

        FakeGoldPort(boolean sufficientGold) {
            this.sufficientGold = sufficientGold;
        }

        @Override
        public boolean deductGold(Transaction tx, long characterId, long amount) {
            deductCalled = true;
            deductedAmount = amount;
            return sufficientGold;
        }

        @Override
        public void creditGold(Transaction tx, long characterId, long amount) {
            throw new UnsupportedOperationException("Buy không credit gold");
        }
    }

    static final class FakeInventoryGatewayPort implements InventoryGatewayPort {
        private final boolean canGrant;
        boolean grantCalled;

        FakeInventoryGatewayPort(boolean canGrant) {
            this.canGrant = canGrant;
        }

        @Override
        public Optional<OwnedItem> findItem(long characterId, long itemId) {
            throw new UnsupportedOperationException("Buy không cần findItem");
        }

        @Override
        public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            grantCalled = true;
            return canGrant;
        }

        @Override
        public boolean removeQuantity(Transaction tx, long characterId, long itemId, int quantity) {
            throw new UnsupportedOperationException("Buy không cần removeQuantity");
        }
    }

    static final class FakeAuditPort implements AuditPort {
        String loggedAction;

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            this.loggedAction = action;
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

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);
    private static final String IDEMPOTENCY_KEY = "key-1";

    private static BuyItemUseCase newUseCase(
            ShopCatalogPort catalogPort, FakeGoldPort goldPort, FakeInventoryGatewayPort inventoryGatewayPort,
            FakeAuditPort auditPort) {
        return newUseCase(catalogPort, goldPort, inventoryGatewayPort, auditPort, new FakeIdempotencyPort());
    }

    private static BuyItemUseCase newUseCase(
            ShopCatalogPort catalogPort, FakeGoldPort goldPort, FakeInventoryGatewayPort inventoryGatewayPort,
            FakeAuditPort auditPort, FakeIdempotencyPort idempotencyPort) {
        var ports = new ShopPorts(catalogPort, goldPort, inventoryGatewayPort, auditPort, idempotencyPort);
        return new BuyItemUseCase(ports, DIRECT_TX);
    }

    @Test
    void shouldRejectBuy_whenItemNotInShop() {
        var catalogPort = new FakeCatalogPort(null);
        var goldPort = new FakeGoldPort(true);
        var inventoryGatewayPort = new FakeInventoryGatewayPort(true);
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, goldPort, inventoryGatewayPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, TEMPLATE_ID, 1, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.ITEM_NOT_IN_SHOP);
        assertThat(goldPort.deductCalled).isFalse();
        assertThat(inventoryGatewayPort.grantCalled).isFalse();
    }

    @Test
    void shouldRejectBuy_whenGoldPortRefusesDeduction() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var goldPort = new FakeGoldPort(false);
        var inventoryGatewayPort = new FakeInventoryGatewayPort(true);
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, goldPort, inventoryGatewayPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, TEMPLATE_ID, 3, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INSUFFICIENT_GOLD);
        assertThat(goldPort.deductedAmount).isEqualTo(15); // 5 * 3
        assertThat(inventoryGatewayPort.grantCalled).isFalse(); // return sớm, không grant
        assertThat(auditPort.loggedAction).isNull();
    }

    @Test
    void shouldRollbackAndReturnInventoryFull_whenGrantFails() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var goldPort = new FakeGoldPort(true);
        var inventoryGatewayPort = new FakeInventoryGatewayPort(false); // túi đầy
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, goldPort, inventoryGatewayPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, TEMPLATE_ID, 1, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.INVENTORY_FULL);
        assertThat(goldPort.deductCalled).isTrue(); // đã thử trừ (rollback ở tầng transaction thật)
        assertThat(auditPort.loggedAction).isNull(); // KHÔNG ghi audit cho giao dịch bị rollback
    }

    @Test
    void shouldSucceed_whenEnoughGoldAndRoomInInventory() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var goldPort = new FakeGoldPort(true);
        var inventoryGatewayPort = new FakeInventoryGatewayPort(true);
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, goldPort, inventoryGatewayPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, TEMPLATE_ID, 2, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.OK);
        assertThat(auditPort.loggedAction).isEqualTo("SHOP_BUY");
    }

    @Test
    void shouldReturnDuplicateRequest_whenIdempotencyKeyReused() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var goldPort = new FakeGoldPort(true);
        var inventoryGatewayPort = new FakeInventoryGatewayPort(true);
        var auditPort = new FakeAuditPort();
        var idempotencyPort = new FakeIdempotencyPort();
        var useCase = newUseCase(catalogPort, goldPort, inventoryGatewayPort, auditPort, idempotencyPort);
        useCase.execute(CHARACTER_ID, TEMPLATE_ID, 1, IDEMPOTENCY_KEY);

        var result = useCase.execute(CHARACTER_ID, TEMPLATE_ID, 1, IDEMPOTENCY_KEY);

        assertThat(result).isEqualTo(BuyItemUseCase.BuyResult.DUPLICATE_REQUEST);
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var useCase = newUseCase(catalogPort, new FakeGoldPort(true), new FakeInventoryGatewayPort(true),
                new FakeAuditPort());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> useCase.execute(CHARACTER_ID, TEMPLATE_ID, 0, IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

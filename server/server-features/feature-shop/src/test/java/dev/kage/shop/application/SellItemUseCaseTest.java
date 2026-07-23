package dev.kage.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.shop.domain.AuditPort;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.ShopCatalogPort;
import dev.kage.shop.domain.ShopListing;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SellItemUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long ITEM_ID = 42L;
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
        boolean creditCalled;
        long creditedAmount;

        @Override
        public boolean deductGold(Transaction tx, long characterId, long amount) {
            throw new UnsupportedOperationException("Sell không deduct gold");
        }

        @Override
        public void creditGold(Transaction tx, long characterId, long amount) {
            creditCalled = true;
            creditedAmount = amount;
        }
    }

    static final class FakeInventoryGatewayPort implements InventoryGatewayPort {
        private final OwnedItem owned;
        private final boolean canRemove;
        boolean removeCalled;

        FakeInventoryGatewayPort(OwnedItem owned, boolean canRemove) {
            this.owned = owned;
            this.canRemove = canRemove;
        }

        @Override
        public Optional<OwnedItem> findItem(long characterId, long itemId) {
            return Optional.ofNullable(owned);
        }

        @Override
        public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            throw new UnsupportedOperationException("Sell không grant item");
        }

        @Override
        public boolean removeQuantity(Transaction tx, long characterId, long itemId, int quantity) {
            removeCalled = true;
            return canRemove;
        }
    }

    static final class FakeAuditPort implements AuditPort {
        String loggedAction;

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            this.loggedAction = action;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    private static SellItemUseCase newUseCase(
            ShopCatalogPort catalogPort, FakeInventoryGatewayPort inventoryGatewayPort, FakeGoldPort goldPort,
            FakeAuditPort auditPort) {
        var ports = new ShopPorts(catalogPort, goldPort, inventoryGatewayPort, auditPort);
        return new SellItemUseCase(ports, DIRECT_TX);
    }

    @Test
    void shouldReturnItemNotFound_whenItemDoesNotBelongToCharacter() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var inventoryGatewayPort = new FakeInventoryGatewayPort(null, true);
        var goldPort = new FakeGoldPort();
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, inventoryGatewayPort, goldPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID, 1);

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.ITEM_NOT_FOUND);
        assertThat(goldPort.creditCalled).isFalse();
    }

    @Test
    void shouldReturnNotSellable_whenItemNotInCatalog() {
        var owned = new OwnedItem(ITEM_ID, TEMPLATE_ID, 3);
        var catalogPort = new FakeCatalogPort(null);
        var inventoryGatewayPort = new FakeInventoryGatewayPort(owned, true);
        var goldPort = new FakeGoldPort();
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, inventoryGatewayPort, goldPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID, 1);

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.ITEM_NOT_SELLABLE);
        assertThat(inventoryGatewayPort.removeCalled).isFalse();
    }

    @Test
    void shouldReturnInsufficientQuantity_whenRemoveQuantityRefuses() {
        var owned = new OwnedItem(ITEM_ID, TEMPLATE_ID, 1);
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var inventoryGatewayPort = new FakeInventoryGatewayPort(owned, false);
        var goldPort = new FakeGoldPort();
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, inventoryGatewayPort, goldPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID, 5);

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.INSUFFICIENT_QUANTITY);
        assertThat(goldPort.creditCalled).isFalse();
        assertThat(auditPort.loggedAction).isNull();
    }

    @Test
    void shouldCreditGoldAndAudit_whenSellSucceeds() {
        var owned = new OwnedItem(ITEM_ID, TEMPLATE_ID, 3);
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var inventoryGatewayPort = new FakeInventoryGatewayPort(owned, true);
        var goldPort = new FakeGoldPort();
        var auditPort = new FakeAuditPort();
        var useCase = newUseCase(catalogPort, inventoryGatewayPort, goldPort, auditPort);

        var result = useCase.execute(CHARACTER_ID, ITEM_ID, 3);

        assertThat(result).isEqualTo(SellItemUseCase.SellResult.OK);
        assertThat(goldPort.creditedAmount).isEqualTo(6); // 2 * 3
        assertThat(auditPort.loggedAction).isEqualTo("SHOP_SELL");
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        var catalogPort = new FakeCatalogPort(new ShopListing(TEMPLATE_ID, 5, 2));
        var useCase = newUseCase(
                catalogPort, new FakeInventoryGatewayPort(null, true), new FakeGoldPort(), new FakeAuditPort());

        assertThatThrownBy(() -> useCase.execute(CHARACTER_ID, ITEM_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

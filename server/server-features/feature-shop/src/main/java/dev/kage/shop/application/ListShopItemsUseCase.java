package dev.kage.shop.application;

import dev.kage.shop.domain.ShopCatalogPort;
import dev.kage.shop.domain.ShopListing;
import java.util.List;

/** Trả toàn bộ danh mục shop — client hỏi trước khi mua/bán để biết giá. */
public final class ListShopItemsUseCase {

    private final ShopCatalogPort catalogPort;

    public ListShopItemsUseCase(ShopCatalogPort catalogPort) {
        this.catalogPort = catalogPort;
    }

    public List<ShopListing> execute() {
        return catalogPort.findAll();
    }
}

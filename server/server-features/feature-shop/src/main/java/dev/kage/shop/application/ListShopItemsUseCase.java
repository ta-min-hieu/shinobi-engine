package dev.kage.shop.application;

import java.util.List;

import dev.kage.shop.domain.ShopCatalogPort;
import dev.kage.shop.domain.ShopListing;

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

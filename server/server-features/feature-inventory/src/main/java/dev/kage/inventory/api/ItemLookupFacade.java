package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.domain.ItemLookupPort;

/** API tra cứu vật phẩm (ADR-279). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class ItemLookupFacade {

    /** Không có dữ liệu (composition test). */
    public static final ItemLookupFacade DISABLED =
            new ItemLookupFacade(id -> new ItemLookupPort.Lookup(0L, 0L, List.of()));

    private final ItemLookupPort port;

    /** Dựng ở composition root. */
    public ItemLookupFacade(ItemLookupPort port) {
        this.port = port;
    }

    /** Giá + nguồn rơi của mẫu {@code itemTemplateId}. */
    public ItemLookupPort.Lookup lookup(long itemTemplateId) {
        return port.lookup(itemTemplateId);
    }
}

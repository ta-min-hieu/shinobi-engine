package dev.kage.inventory.domain;

import java.util.List;

/** Danh mục bộ pháp khí (ADR-065) — hiện thực ở server-database. */
@FunctionalInterface
public interface ItemSetPort {

    /** Không có bộ nào (composition test). */
    ItemSetPort NONE = List::of;

    /** Mọi bộ pháp khí. Tham số (Điều 27) — đọc một lần, đổi thì khởi động lại. */
    List<ItemSet> itemSets();
}

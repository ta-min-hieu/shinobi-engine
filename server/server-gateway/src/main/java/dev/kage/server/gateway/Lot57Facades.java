package dev.kage.server.gateway;

import dev.kage.friend.api.InvisibleModeFacade;

/**
 * Facade lô 57 (ADR-383…) gom thành MỘT thành phần của {@link Lot56Facades}. Xếp hạng thích tuần dùng facade ADR-298;
 * xếp hạng cấp trong bang dùng facade bang + nhân vật; dọn thư cũ chạy trong {@code RetentionJanitor}.
 */
record Lot57Facades(InvisibleModeFacade invisible, Lot58Facades lot58) {

    /** Composition không có các tính năng này (test). */
    static final Lot57Facades DISABLED = new Lot57Facades(InvisibleModeFacade.DISABLED, Lot58Facades.DISABLED);
}

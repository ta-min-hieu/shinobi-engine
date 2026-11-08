package dev.kage.quest.domain;

import java.util.List;
import java.util.Optional;

/** Danh mục nhiệm vụ theo thứ tự cốt truyện (ADR-228) — mã, nhiệm vụ tiên quyết, lặp hằng ngày hay không. */
public interface QuestCatalogPort {

    /**
     * Một nhiệm vụ trong danh mục.
     *
     * @param prerequisiteCode nhiệm vụ phải trả xong trước (rỗng = không có)
     * @param daily nhiệm vụ lặp hằng ngày (ADR-055)
     */
    record Entry(String code, Optional<String> prerequisiteCode, boolean daily) {}

    /** Mọi nhiệm vụ, theo thứ tự thêm vào game (id tăng dần ≈ thứ tự cốt truyện). */
    List<Entry> all();
}

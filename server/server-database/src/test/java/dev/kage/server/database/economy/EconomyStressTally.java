package dev.kage.server.database.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Sổ phía bộ lái: mỗi thao tác THÀNH CÔNG ghi lại vật phẩm nó vừa đẻ ra (mua, rơi đồ, thưởng
 * quest) hoặc huỷ đi (bán, uống đan, Bảo Vệ bị tiêu) — theo đúng kết quả use case trả về, không
 * đọc DB. Bất biến "tổng SUM(quantity) theo loại = ban đầu + tạo − huỷ" so sổ này với DB.
 *
 * <p>Cần sổ riêng vì không phải nguồn/hố nào cũng ghi {@code audit_log} đủ để dựng lại theo LOẠI
 * vật phẩm (rơi đồ không ghi gì; {@code SHOP_SELL}/{@code QUEST_CLAIM} không ghi
 * {@code itemTemplateId}) — xem javadoc của bài kiểm. Những nguồn CÓ ghi thì được đối chiếu chéo.
 */
final class EconomyStressTally {

    private final Map<Long, LongAdder> createdByTemplate = new ConcurrentHashMap<>();
    private final Map<Long, LongAdder> destroyedByTemplate = new ConcurrentHashMap<>();
    private final Map<Long, LongAdder> boughtByTemplate = new ConcurrentHashMap<>();
    private final Map<Long, LongAdder> usedByTemplate = new ConcurrentHashMap<>();
    private final LongAdder soldQuantity = new LongAdder();
    private final Map<String, LongAdder> outcomes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();

    void bought(long templateId, int quantity) {
        add(boughtByTemplate, templateId, quantity);
        created(templateId, quantity);
    }

    void created(long templateId, int quantity) {
        add(createdByTemplate, templateId, quantity);
    }

    void sold(long templateId, int quantity) {
        soldQuantity.add(quantity);
        destroyed(templateId, quantity);
    }

    void used(long templateId) {
        add(usedByTemplate, templateId, 1);
        destroyed(templateId, 1);
    }

    void destroyed(long templateId, int quantity) {
        add(destroyedByTemplate, templateId, quantity);
    }

    void outcome(String operation, Object result) {
        outcomes.computeIfAbsent(operation + "=" + result, key -> new LongAdder())
                .increment();
    }

    /** Vi phạm phát hiện NGAY trong luồng (vd khoá idempotency dùng lại vẫn thành công lần hai). */
    void violation(String message) {
        violations.add(message);
    }

    long netCreated(long templateId) {
        return sum(createdByTemplate, templateId) - sum(destroyedByTemplate, templateId);
    }

    long boughtCount(long templateId) {
        return sum(boughtByTemplate, templateId);
    }

    long usedCount(long templateId) {
        return sum(usedByTemplate, templateId);
    }

    long soldQuantity() {
        return soldQuantity.sum();
    }

    List<String> violations() {
        return new ArrayList<>(violations);
    }

    /** Bảng đếm kết quả theo thao tác — in ra để biết bài kiểm thật sự đã đi qua những nhánh nào. */
    Map<String, Long> outcomeCounts() {
        Map<String, Long> sorted = new TreeMap<>();
        outcomes.forEach((key, adder) -> sorted.put(key, adder.sum()));
        return sorted;
    }

    private static void add(Map<Long, LongAdder> map, long templateId, int quantity) {
        map.computeIfAbsent(templateId, key -> new LongAdder()).add(quantity);
    }

    private static long sum(Map<Long, LongAdder> map, long templateId) {
        LongAdder adder = map.get(templateId);
        return adder == null ? 0L : adder.sum();
    }
}

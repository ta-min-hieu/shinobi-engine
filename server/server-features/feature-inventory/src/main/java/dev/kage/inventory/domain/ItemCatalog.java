package dev.kage.inventory.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Toàn bộ catalog vật phẩm kèm {@code version} do SERVER tính (ADR-035 Decision-2, phương án A′).
 *
 * <p>Điểm mấu chốt của A′: <b>server</b> là bên phán quyết cache của client còn hợp lệ hay không,
 * không phải client. {@code version} là hash NỘI DUNG (SHA-256 của các bản ghi đã sắp xếp ổn định
 * theo {@code id}) chứ không phải số phiên bản tự tăng tay — chỉnh cân bằng trong DB là version tự
 * đổi, không ai phải nhớ bump gì cả, và hai server chạy cùng dữ liệu thì ra cùng version.
 *
 * <p>Domain thuần: chỉ dùng JDK ({@link MessageDigest}), không import infrastructure (Điều 18).
 */
public record ItemCatalog(String version, List<ItemCatalogEntry> entries) {

    private static final String HASH_ALGORITHM = "SHA-256";

    // Ký tự phân tách trường/bản ghi — chọn ký tự ĐIỀU KHIỂN không xuất hiện được trong code/nameKey
    // (VARCHAR nội dung ASCII in được), nếu không hai catalog khác nhau có thể băm ra cùng chuỗi.
    private static final char FIELD_SEPARATOR = (char) 0x1F; // UNIT SEPARATOR
    private static final char RECORD_SEPARATOR = (char) 0x1E; // RECORD SEPARATOR

    public ItemCatalog {
        entries = List.copyOf(entries);
    }

    /** Sắp xếp ổn định theo {@code id} rồi băm — thứ tự trả về từ DB không được ảnh hưởng version. */
    public static ItemCatalog of(List<ItemCatalogEntry> entries) {
        List<ItemCatalogEntry> sorted = entries.stream()
                .sorted(Comparator.comparingLong(ItemCatalogEntry::id))
                .toList();
        return new ItemCatalog(hashOf(sorted), sorted);
    }

    private static String hashOf(List<ItemCatalogEntry> sorted) {
        StringBuilder canonical = new StringBuilder();
        for (ItemCatalogEntry entry : sorted) {
            canonical
                    .append(entry.id())
                    .append(FIELD_SEPARATOR)
                    .append(entry.code())
                    .append(FIELD_SEPARATOR)
                    .append(entry.nameKey())
                    .append(FIELD_SEPARATOR)
                    .append(entry.maxStack())
                    .append(FIELD_SEPARATOR)
                    .append(entry.equipSlot() == null ? "" : entry.equipSlot().name())
                    .append(FIELD_SEPARATOR)
                    .append(entry.bonusAtk())
                    .append(FIELD_SEPARATOR)
                    .append(entry.bonusDef())
                    .append(FIELD_SEPARATOR)
                    .append(entry.bonusRes())
                    .append(FIELD_SEPARATOR)
                    .append(entry.bonusHp())
                    .append(FIELD_SEPARATOR)
                    .append(entry.requiredLevel())
                    .append(FIELD_SEPARATOR)
                    .append(entry.useEffect() == null ? "" : entry.useEffect().name())
                    .append(FIELD_SEPARATOR)
                    .append(entry.useValue())
                    .append(RECORD_SEPARATOR);
        }
        return HexFormat.of().formatHex(digest(canonical.toString()));
    }

    private static byte[] digest(String canonical) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán BẮT BUỘC có trong mọi JDK (javadoc MessageDigest) — tới được
            // đây nghĩa là JVM hỏng, không phải lỗi nghiệp vụ để nuốt.
            throw new IllegalStateException("JVM không có " + HASH_ALGORITHM, e);
        }
    }
}

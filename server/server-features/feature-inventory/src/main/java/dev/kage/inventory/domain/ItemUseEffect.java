package dev.kage.inventory.domain;

/**
 * Vật phẩm này làm gì khi được DÙNG (ADR-046 D5) — cột {@code item_template.use_effect}.
 *
 * <p>{@code null} ở phía {@link ItemTemplate#useEffect()} nghĩa là món KHÔNG dùng được (nguyên
 * liệu, vật phẩm nhiệm vụ, trang bị) — không có hằng {@code NONE} ở đây vì "không dùng được" không
 * phải một hiệu ứng, và một enum có nhánh NONE luôn kết thúc bằng việc quên kiểm nó ở đâu đó.
 *
 * <p>Enum thay vì chuỗi tự do (Điều 11 cấm {@code public static final int}): chuỗi lạ trong DB
 * phải làm món đó thành "không dùng được" chứ không được làm sập server — xem
 * {@code PostgresItemTemplateAdapter}.
 */
public enum ItemUseEffect {
    /** Hồi {@link ItemTemplate#useValue()} máu ngay lập tức. Đan dược của arc 1. */
    HEAL_HP
}

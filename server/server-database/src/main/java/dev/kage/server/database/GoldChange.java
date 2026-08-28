package dev.kage.server.database;

/**
 * Một lần vàng của một nhân vật ĐÃ ĐỔI THẬT và đã commit (ADR-035 Decision-3).
 *
 * <p>{@code gold} là số dư TUYỆT ĐỐI sau thay đổi (đọc bằng {@code RETURNING gold} trong CHÍNH câu
 * UPDATE, không phải một câu SELECT thứ hai — nếu không sẽ có cửa sổ race với giao dịch song song).
 * {@code delta} chỉ để client hiện hiệu ứng "-500"; mất một gói giữa chừng thì gói kế tự sửa lại vì
 * số dư tuyệt đối không tích luỹ sai lệch.
 *
 * <p>{@code reasonKey} là KHOÁ i18n ({@code "gold.reason.buy_item"}), không phải câu tiếng Việt cứng.
 */
public record GoldChange(long characterId, long gold, long delta, String reasonKey) {}

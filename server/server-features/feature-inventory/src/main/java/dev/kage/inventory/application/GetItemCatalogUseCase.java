package dev.kage.inventory.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.ItemCatalog;
import dev.kage.inventory.domain.ItemTemplatePort;

/**
 * Trả TOÀN BỘ catalog vật phẩm kèm {@code version} do server tính (ADR-035 Decision-2, phương án
 * A′) — đọc thuần, không ghi.
 *
 * <p><b>Cache trong bộ nhớ, và điều kiện làm mất hiệu lực của nó:</b> {@code item_template} là bảng
 * CONFIG TĨNH — không UseCase nào ghi vào nó lúc chạy (xem {@link ItemTemplatePort}: cổng ĐỌC
 * THUẦN). Vì vậy catalog được đọc từ DB đúng MỘT lần cho cả vòng đời tiến trình, rồi giữ nguyên.
 *
 * <p>Hệ quả phải biết trước khi đổi số cân bằng: <b>sửa {@code item_template} bằng migration hay
 * bằng tay trong lúc server ĐANG CHẠY sẽ KHÔNG được phản ánh cho tới khi khởi động lại server</b>.
 * Đây là đánh đổi CHỦ Ý (một câu SELECT bảng 3 dòng mỗi lần đăng nhập là lãng phí thật, và
 * migration vốn chạy lúc khởi động — {@code FlywayMigrator} trong constructor
 * {@code ServerDatabaseBootstrap}). Nếu sau này cần đổi catalog nóng, thêm một đường vô hiệu hoá
 * cache TƯỜNG MINH — đừng đổi thầm sang đọc lại mỗi lần, vì {@code version} là hash nội dung nên
 * client sẽ tự nhận bản mới ngay khi server có bản mới.
 *
 * <p>{@link AtomicReference} chứ không phải field thường: nhiều Virtual Thread (mỗi connection một
 * thread) có thể gọi lần đầu cùng lúc. Trường hợp xấu nhất là vài thread cùng đọc DB rồi cùng ghi —
 * vô hại vì kết quả giống hệt nhau (cùng dữ liệu → cùng hash), rẻ hơn hẳn một lock.
 */
public final class GetItemCatalogUseCase {

    private final ItemTemplatePort itemTemplatePort;
    private final AtomicReference<ItemCatalog> cached = new AtomicReference<ItemCatalog>();

    public GetItemCatalogUseCase(ItemTemplatePort itemTemplatePort) {
        this.itemTemplatePort = itemTemplatePort;
    }

    public ItemCatalog execute() {
        ItemCatalog current = cached.get();
        if (current != null) {
            return current;
        }
        ItemCatalog loaded = ItemCatalog.of(itemTemplatePort.listCatalog());
        cached.set(loaded);
        return loaded;
    }
}

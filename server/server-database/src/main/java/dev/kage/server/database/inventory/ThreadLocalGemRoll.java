package dev.kage.server.database.inventory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Nguồn random của lần khảm Linh Ngọc (ADR-064 D3) — mỗi lần gọi chuyển thẳng tới
 * {@link ThreadLocalRandom#current()} của luồng ĐANG gọi (Virtual Thread của lệnh).
 *
 * <p>Tồn tại vì giữ lại {@code ThreadLocalRandom.current()} lấy ở luồng khởi động rồi dùng trên luồng
 * khác là sai hợp đồng của JDK — cùng lý do {@code ThreadLocalRandomSource} của server-world. Không giữ
 * trạng thái nên chia sẻ một thể hiện là an toàn. Test tiêm nguồn cố định thay vào.
 */
public final class ThreadLocalGemRoll implements RandomGenerator {

    public static final ThreadLocalGemRoll INSTANCE = new ThreadLocalGemRoll();

    private ThreadLocalGemRoll() {}

    @Override
    public long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}

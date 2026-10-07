package dev.kage.server.world;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Nguồn random MẶC ĐỊNH của World — mỗi lần gọi chuyển thẳng tới {@link ThreadLocalRandom#current()}
 * của luồng ĐANG gọi (luồng tick), đúng hành vi trước khi nguồn random được tiêm vào (2026-10-06).
 *
 * <p>Tồn tại vì {@code ThreadLocalRandom.current()} trả về một đối tượng gắn với luồng gọi nó: giữ
 * lại tham chiếu đó từ luồng dựng {@link WorldFacade} rồi dùng trên luồng tick là sai hợp đồng của
 * JDK. Lớp này không giữ gì, nên chia sẻ một thể hiện cho mọi World là an toàn.
 *
 * <p>Test thay bằng một {@link java.util.SplittableRandom} seed cố định để cùng seed ⇒ cùng kết quả
 * trúng/né/bạo kích/hướng tuần tra (xem {@code WorldSimulationPropertyTest}).
 */
final class ThreadLocalRandomSource implements RandomGenerator {

    static final ThreadLocalRandomSource INSTANCE = new ThreadLocalRandomSource();

    private ThreadLocalRandomSource() {}

    @Override
    public long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Override
    public int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    @Override
    public float nextFloat() {
        return ThreadLocalRandom.current().nextFloat();
    }

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    @Override
    public double nextDouble(double origin, double bound) {
        return ThreadLocalRandom.current().nextDouble(origin, bound);
    }
}

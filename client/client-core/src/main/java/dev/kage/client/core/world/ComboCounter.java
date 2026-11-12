package dev.kage.client.core.world;

/**
 * Đếm combo đòn đánh (ADR-293): mỗi đòn TRÚNG do chính mình gây ra trong vòng {@value #WINDOW_SECONDS} giây kể từ đòn
 * trước cộng một; quá hạn thì đếm lại từ 1. Chỉ là hiển thị, không ảnh hưởng sát thương. Không cấp phát.
 */
final class ComboCounter {

    /** Khoảng tối đa giữa hai đòn để còn tính là một chuỗi. */
    static final float WINDOW_SECONDS = 3f;

    /** Chỉ hiện chữ combo từ đòn thứ bấy nhiêu trở đi. */
    static final int SHOW_FROM = 3;

    private int count;
    private float sinceHit = Float.MAX_VALUE;

    /** Một đòn trúng — trả số đòn của chuỗi hiện tại. */
    int hit() {
        count = sinceHit <= WINDOW_SECONDS ? count + 1 : 1;
        sinceHit = 0f;
        return count;
    }

    /** Thời gian trôi (giây thật, mỗi khung vẽ). */
    void tick(float deltaTime) {
        if (sinceHit < Float.MAX_VALUE) {
            sinceHit += deltaTime;
        }
    }

    /** Chuỗi hiện tại (0 nếu đã đứt). */
    int current() {
        return sinceHit <= WINDOW_SECONDS ? count : 0;
    }
}

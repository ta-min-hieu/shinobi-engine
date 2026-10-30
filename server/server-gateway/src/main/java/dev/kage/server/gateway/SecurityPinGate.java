package dev.kage.server.gateway;

/**
 * Trạng thái khoá an toàn của MỘT kết nối (ADR-115): tài khoản có PIN không, phiên này đã kiểm PIN đúng chưa. Nạp lúc
 * đăng nhập (Virtual Thread), đọc trên EventLoop trước các lệnh bị chặn — chỉ đọc bộ nhớ, không chạm DB.
 */
final class SecurityPinGate {

    private volatile boolean hasPin;
    private volatile boolean unlocked;

    /** Đăng nhập (lại) — phiên mới luôn bắt đầu ở trạng thái khoá nếu tài khoản có PIN. */
    void reset(boolean accountHasPin) {
        hasPin = accountHasPin;
        unlocked = false;
    }

    /** Vừa đặt PIN (người đặt đã chứng minh biết PIN) hoặc kiểm đúng ⇒ mở cho phiên này. */
    void unlock() {
        hasPin = true;
        unlocked = true;
    }

    /** GM xoá PIN của tài khoản đang online — không ai cần cổng nữa. */
    void cleared() {
        hasPin = false;
        unlocked = false;
    }

    boolean hasPin() {
        return hasPin;
    }

    /** {@code true} nếu phiên này chưa được làm các việc bị chặn. */
    boolean blocks() {
        return hasPin && !unlocked;
    }

    /** Có PIN và đã mở — hoặc chưa có PIN (không có gì để mở). */
    boolean isOpen() {
        return !blocks();
    }
}

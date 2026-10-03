package dev.kage.client.core.ui.hud;

import java.util.Locale;

/**
 * Chữ đếm ngược hồi chiêu trên nút skill ({@link CombatHud#setSkillState}) — một chữ số thập phân,
 * rỗng khi đã sẵn sàng.
 *
 * <p>Tồn tại vì {@code setSkillState} chạy MỖI KHUNG render: dựng chữ bằng {@code String.format}
 * mỗi khung là cấp phát trong game loop (/CLAUDE.md §6) suốt cả quãng hồi chiêu, dù chữ trên nút
 * chỉ đổi mười lần mỗi giây. Lớp này nhớ chữ đã dựng cho phần mười giây đang hiện và chỉ dựng lại
 * khi con số đó đổi. Tách khỏi {@link CombatHud} để test được không cần context GL.
 */
final class CooldownText {

    /** Một chữ số thập phân ⇒ đơn vị hiển thị là một phần mười giây. */
    private static final float TENTHS_PER_SECOND = 10f;

    private static final int NO_TEXT_YET = Integer.MIN_VALUE;

    private int lastTenths = NO_TEXT_YET;
    private String lastText = "";

    /**
     * Chữ đếm ngược cho số giây hồi chiêu còn lại.
     *
     * @return chữ hiển thị cho {@code remainingSeconds} giây còn lại; chuỗi rỗng khi {@code <= 0}.
     *     Cùng phần mười giây với lần gọi trước ⇒ trả lại ĐÚNG chuỗi cũ, không cấp phát.
     */
    String textFor(float remainingSeconds) {
        if (remainingSeconds <= 0f) {
            return "";
        }
        int tenths = Math.round(remainingSeconds * TENTHS_PER_SECOND);
        if (tenths != lastTenths) {
            lastTenths = tenths;
            lastText = String.format(Locale.ROOT, "%.1f", tenths / TENTHS_PER_SECOND);
        }
        return lastText;
    }
}

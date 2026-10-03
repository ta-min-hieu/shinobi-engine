package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CooldownTextTest {

    @Test
    void shouldShowOneDecimal_andNothingWhenReady() {
        CooldownText text = new CooldownText();

        assertThat(text.textFor(2.94f)).isEqualTo("2.9");
        assertThat(text.textFor(0f)).isEmpty();
        assertThat(text.textFor(-0.5f)).isEmpty();
    }

    /**
     * {@code CombatHud#setSkillState} được gọi MỖI KHUNG render. Trước bản vá nó gọi
     * {@code String.format} mỗi khung suốt thời gian hồi chiêu (60 lần/giây × 3–5 giây, mỗi lần một
     * {@code Formatter} + chuỗi mới) dù chữ trên nút chỉ đổi 10 lần/giây — cấp phát trong game loop,
     * thứ /CLAUDE.md §6 cấm. Cùng một chữ ⇒ phải trả lại ĐÚNG chuỗi cũ, không dựng chuỗi mới.
     */
    @Test
    void shouldReuseTheSameString_whileTheShownTenthDoesNotChange() {
        CooldownText text = new CooldownText();

        String first = text.textFor(2.94f);
        String second = text.textFor(2.91f);

        assertThat(second).isSameAs(first);
        assertThat(text.textFor(2.84f)).isEqualTo("2.8");
    }
}

package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Luật tên bang = luật tên nhân vật (quyết định chủ dự án 2026-09-29): 2-16 ký tự, chỉ chữ (mọi
 * ngôn ngữ, kể cả tiếng Việt có dấu dựng sẵn), số và {@code _}; KHÔNG tự cắt khoảng trắng.
 */
class GuildNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"Konoha", "AB", "ThanhVânMôn", "Kiếm_Tông", "Đạo123", "ABCDEFGHIJKLMNOP", "天剑宗", "_x"})
    void shouldAccept_validNames(String name) {
        assertThat(GuildName.isValid(name)).as(name).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "A",
                "ABCDEFGHIJKLMNOPQ",
                "Thanh Vân",
                " Konoha",
                "Konoha ",
                "Kiếm-Tông",
                "abc!",
                "a.b",
                "<script>",
                "Tông​môn", // khoảng trắng độ rộng 0
                "Konoha\n",
                "Kíem" // dấu tổ hợp (NFD) — luật tên nhân vật cũng không nhận
            })
    void shouldReject_invalidNames(String name) {
        assertThat(GuildName.isValid(name)).as("'%s'", name).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"🐉🐉", "Rồng🐉"})
    void shouldReject_emoji(String name) {
        assertThat(GuildName.isValid(name)).isFalse();
    }

    /**
     * Quyết định chủ dự án 2026-10-11: ký tự lấp chỗ Hangul (chữ cái theo Unicode, khoảng trống trên
     * màn hình) và dạng tương thích (khổ toàn phần, chữ toán học) không dùng được cho tên bang MỚI.
     * Dựng bằng mã điểm để mẫu thử nhìn thấy được trong mã.
     */
    @org.junit.jupiter.api.Test
    void shouldReject_invisibleAndCompatibilityCharacters() {
        for (int filler : new int[] {0x115F, 0x1160, 0x3164, 0xFFA0}) {
            assertThat(GuildName.isValid("A" + Character.toString(filler) + "B"))
                    .as("U+%04X", filler)
                    .isFalse();
        }
        assertThat(GuildName.isValid(Character.toString(0xFF2B) + Character.toString(0xFF21)))
                .as("chữ khổ toàn phần")
                .isFalse();
        assertThat(GuildName.isValid(Character.toString(0x1D40A) + Character.toString(0x1D41A)))
                .as("chữ toán học đậm")
                .isFalse();
    }

    @org.junit.jupiter.api.Test
    void shouldReject_null() {
        assertThat(GuildName.isValid(null)).isFalse();
    }
}

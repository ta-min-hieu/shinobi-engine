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

    @org.junit.jupiter.api.Test
    void shouldReject_null() {
        assertThat(GuildName.isValid(null)).isFalse();
    }
}

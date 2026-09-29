package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.Character;
import dev.kage.guild.domain.GuildName;

/**
 * Luật tên bang PHẢI trùng luật tên nhân vật (quyết định chủ dự án 2026-09-29). Hai bản sống ở hai
 * module không được phụ thuộc nhau (feature-guild cấm phụ thuộc feature-character), nên luật bị chép
 * — test này ở server-gateway, module duy nhất thấy cả hai, bắt lúc một bên đổi mà bên kia không.
 */
class GuildNameParityTest {

    private static final List<String> SAMPLES = List.of(
            "",
            "A",
            "AB",
            "Konoha",
            "ThanhVânMôn",
            "Kiếm_Tông",
            "Đạo123",
            "天剑宗",
            "_x",
            "ABCDEFGHIJKLMNOP",
            "ABCDEFGHIJKLMNOPQ",
            "Thanh Vân",
            " Konoha",
            "Konoha ",
            "Kiếm-Tông",
            "abc!",
            "a.b",
            "<script>",
            "Konoha\n",
            "Kíem",
            "Tông​môn",
            "Rồng🐉");

    @Test
    void guildNameRuleShouldMatchCharacterNameRule() {
        for (String sample : SAMPLES) {
            assertThat(GuildName.isValid(sample)).as("'%s'", sample).isEqualTo(characterNameAccepts(sample));
        }
    }

    private static boolean characterNameAccepts(String name) {
        try {
            Character.validateName(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

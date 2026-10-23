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
            "Rồng🐉",
            // 2026-10-11 — tên vô hình và dạng tương thích: bốn ký tự lấp chỗ Hangul, chữ khổ toàn
            // phần (KAGE), chữ toán học đậm, chữ số mũ. Bốn mẫu đầu chứa ký tự KHÔNG nhìn thấy được giữa A và B.
            "AᅟB",
            "AᅠB",
            "AㅤB",
            "AﾠB",
            "ㅤㅤ",
            "ＫＡＧＥ",
            "𝐊𝐚",
            "Kage²",
            // 2026-10-23 (dòng 42) — trộn hệ chữ trông giống nhau: а Cyrillic trong Kage, ο Hy Lạp; đối chứng
            // thuần Cyrillic, thuần Hy Lạp, tiếng Việt, Latin + chữ Hán (không giống nhau nên không chặn).
            "Kаge",
            "Kagο",
            "Каге",
            "Καγε",
            "Kiếm_Tiên9",
            "Kage龍");

    @Test
    void guildNameRuleShouldMatchCharacterNameRule() {
        for (String sample : SAMPLES) {
            assertThat(GuildName.isValid(sample)).as("'%s'", sample).isEqualTo(characterNameAccepts(sample));
        }
    }

    /**
     * 2026-10-14 (README dòng 42): tên gõ dạng NFD được chuẩn hoá NFC trước khi kiểm, ở CẢ hai luật —
     * cùng dạng lưu, cùng kết quả.
     */
    @Test
    void bothRulesShouldNormalizeDecomposedVietnameseToTheSameAcceptedName() {
        String decomposed = "Kie\u0302n_Tu\u031B"; // "Kiên_Tư" gõ dấu tách rời (NFD)
        String composed = "Ki\u00EAn_T\u01B0";

        assertThat(GuildName.normalize(decomposed)).isEqualTo(composed);
        assertThat(Character.normalizeNewName(decomposed)).isEqualTo(composed);
        assertThat(GuildName.isValid(GuildName.normalize(decomposed))).isTrue();
        assertThat(characterNameAccepts(Character.normalizeNewName(decomposed))).isTrue();
    }

    private static boolean characterNameAccepts(String name) {
        try {
            Character.validateNewName(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

package dev.kage.server.gateway;

import java.util.List;

/**
 * Bộ username/tên nhân vật độc dùng chung cho các test đăng nhập/tạo nhân vật qua dây: rỗng, khoảng
 * trắng, ký tự vô hình (zero-width, điều khiển hướng RTL/LTR), NUL, xuống dòng, dấu tổ hợp (dạng NFD),
 * khoảng trắng Unicode, emoji, nửa cặp surrogate lẻ, ký hiệu SQL/HTML/format, quá dài.
 *
 * <p>Mọi ký tự ngoài ASCII viết bằng escape để nhìn thấy được trong mã nguồn.
 */
final class HostileInputs {

    private HostileInputs() {}

    static List<String> usernames() {
        return List.of(
                "",
                " ",
                "\t\t\t",
                "kage hero",
                "kagehero\n",
                "kage\u0000hero",
                "kage\u200Bhero",
                "\u200B\u200B\u200B",
                "kage\u202Eorehc",
                "\u05E9\u05DC\u05D5\u05DD",
                "k\u0430ge",
                "\uFF4B\uFF41\uFF47\uFF45",
                "kag\u00E9",
                "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00",
                "a".repeat(21),
                "a".repeat(10_000),
                "kage'; DROP TABLE account; --",
                "%s%s%s%n");
    }

    static List<String> characterNames() {
        return List.of(
                "",
                " ",
                "A",
                "Kage Hero",
                "Kage\n",
                "Kage\u0000",
                "Kage\u200BHero",
                "\u200B\u200B",
                "Kage\u202EoreH",
                "\u202E\u202E",
                "Kage\u200EHero",
                "Kage\u00A0Hero",
                "Kage\u3000Hero",
                "\uD83D\uDE00\uD83D\uDE00",
                "Kage-Hero",
                "<b>Kage</b>",
                "Kage'--",
                "%s%n",
                "A".repeat(17),
                "A".repeat(10_000));
    }

    /** Mô tả theo code point — để thông báo lỗi của test đọc được cả ký tự vô hình. */
    static String describe(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().limit(24).forEach(cp -> out.append(String.format("U+%04X ", cp)));
        return out.append("(len=").append(value.length()).append(')').toString();
    }
}

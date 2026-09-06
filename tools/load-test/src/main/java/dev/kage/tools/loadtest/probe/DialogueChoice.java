package dev.kage.tools.loadtest.probe;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dev.kage.protocol.DialogueOptionView;

/**
 * Đổi một bước người dùng gõ ở dòng lệnh thành {@code option_index} thật gửi trong
 * {@code SelectDialogueOptionCommand}.
 *
 * <p>Hai cách viết, vì mỗi cách hỏng theo một kiểu khác nhau: gõ CHỈ SỐ ({@code 1}) đúng nguyên văn
 * thứ server hiểu nhưng vỡ im lặng khi cây hội thoại chèn thêm lựa chọn ở giữa (chính điều mà
 * {@code town_elder.js} phải ghi cảnh báo để tránh); gõ KHOÁ ({@code accept_quest:night_watch_bandit}
 * hay {@code dialogue.option.bye}) thì bám vào nội dung nên sống sót qua việc chèn thêm, và sai thì
 * sai to tiếng chứ không âm thầm chọn nhầm nhánh.
 *
 * <p>Hàm THUẦN, không I/O — test được không cần server ({@code DialogueChoiceTest}).
 */
final class DialogueChoice {

    private DialogueChoice() {}

    /**
     * Tìm vị trí lựa chọn khớp {@code token} trong node hiện tại.
     *
     * @param token chỉ số (chuỗi toàn chữ số) hoặc khoá khớp {@code action} rồi tới {@code label_key}
     * @return vị trí 0-based để đặt vào {@code SelectDialogueOptionCommand.option_index}
     * @throws IllegalArgumentException nếu không khớp lựa chọn nào, hoặc chỉ số nằm ngoài node —
     *     gõ sai thì DỪNG kèm danh sách lựa chọn thật, đừng đoán hộ người dùng một nhánh khác
     */
    static int resolve(String token, List<DialogueOptionView> options) {
        return isIndex(token) ? checkedIndex(Integer.parseInt(token), options) : matchByKey(token, options);
    }

    private static int checkedIndex(int index, List<DialogueOptionView> options) {
        if (index >= options.size()) {
            throw new IllegalArgumentException("Chỉ số lựa chọn " + index + " nằm ngoài node (" + options.size()
                    + " lựa chọn). Node đang có: " + describe(options));
        }
        return index;
    }

    // action TRƯỚC label_key: action là thứ server thật sự chạy (nhánh nào, quest nào), label_key
    // chỉ là khoá hiển thị — trùng nhau thì chọn theo hành vi, không theo chữ.
    private static int matchByKey(String token, List<DialogueOptionView> options) {
        int byAction = indexOfField(token, options, DialogueOptionView::getAction);
        if (byAction >= 0) {
            return byAction;
        }
        int byLabel = indexOfField(token, options, DialogueOptionView::getLabelKey);
        if (byLabel >= 0) {
            return byLabel;
        }
        throw new IllegalArgumentException(
                "Không có lựa chọn nào khớp '" + token + "'. Node đang có: " + describe(options));
    }

    private static int indexOfField(
            String token, List<DialogueOptionView> options, Function<DialogueOptionView, String> field) {
        for (int index = 0; index < options.size(); index++) {
            if (token.equals(field.apply(options.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    /** Bảng lựa chọn của node hiện tại — in ra để người dùng biết bước kế tiếp gõ gì. */
    static String describe(List<DialogueOptionView> options) {
        return IntStream.range(0, options.size())
                .mapToObj(index -> "[" + index + "] " + options.get(index).getLabelKey() + " -> "
                        + options.get(index).getAction())
                .collect(Collectors.joining(" | "));
    }

    private static boolean isIndex(String token) {
        if (token.isEmpty()) {
            return false;
        }
        return token.chars().allMatch(Character::isDigit);
    }
}

package dev.kage.client.core.screen;

import java.util.Optional;

import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.SelectCharacterResponse;

/**
 * Quyết định câu trả lời của một bước đăng nhập là "được" hay "bị từ chối vì lý do gì" — tách khỏi
 * các Screen (cần GL) để test được thẳng ({@code ReplyVerdictTest}).
 *
 * <p>Luật chung cho cả ba bước: bị từ chối thì mang {@code error_key} của server ra màn hình, không
 * thay bằng câu lỗi chung. Trước bản này {@code CharacterSelectScreen} bỏ {@code error_key} của
 * {@code SelectCharacterResponse} — chọn nhân vật đã bị xoá ({@code error.character_not_found}) thì
 * thấy đúng câu lỗi của lúc máy chủ sập. Khoá rỗng/lạ vẫn rơi về câu chung ở {@code KageI18n#errorText}.
 */
final class ReplyVerdict {

    /**
     * @param errorKey khoá i18n server gửi — có thể rỗng; bên hiện chữ tự rơi về câu chung
     */
    record Rejection(String errorKey) {}

    private ReplyVerdict() {}

    /** {@code ConnectResponse} — rỗng khi đăng nhập được. */
    static Optional<Rejection> of(ConnectResponse response) {
        return verdict(response.getSuccess(), response.getErrorKey());
    }

    /** {@code CreateCharacterResponse} — rỗng khi tạo được. */
    static Optional<Rejection> of(CreateCharacterResponse response) {
        return verdict(response.getSuccess(), response.getErrorKey());
    }

    /** {@code SelectCharacterResponse} — rỗng khi vào world được. */
    static Optional<Rejection> of(SelectCharacterResponse response) {
        return verdict(response.getSuccess(), response.getErrorKey());
    }

    private static Optional<Rejection> verdict(boolean success, String errorKey) {
        return success ? Optional.empty() : Optional.of(new Rejection(errorKey));
    }
}

package dev.kage.client.core.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.SelectCharacterResponse;

/**
 * Câu trả lời {@code success=false} của ba bước đăng nhập phải mang {@code error_key} của server ra
 * màn hình, không phải một câu lỗi chung.
 *
 * <p>Tái hiện lỗi thật: {@code CharacterSelectScreen} bỏ {@code error_key} của
 * {@code SelectCharacterResponse} và luôn hiện {@code charselect.error.generic} — người chơi chọn
 * một nhân vật đã bị xoá ({@code error.character_not_found}) thấy đúng câu họ thấy khi máy chủ sập.
 */
class ReplyVerdictTest {

    @Test
    void shouldCarryTheServerReason_whenSelectCharacterIsRejected() {
        SelectCharacterResponse rejected = SelectCharacterResponse.newBuilder()
                .setSuccess(false)
                .setErrorKey("error.character_not_found")
                .build();

        assertThat(ReplyVerdict.of(rejected))
                .map(ReplyVerdict.Rejection::errorKey)
                .contains("error.character_not_found");
    }

    @Test
    void shouldCarryTheServerReason_whenCreateCharacterIsRejected() {
        CreateCharacterResponse rejected = CreateCharacterResponse.newBuilder()
                .setSuccess(false)
                .setErrorKey("error.duplicate_character_name")
                .build();

        assertThat(ReplyVerdict.of(rejected))
                .map(ReplyVerdict.Rejection::errorKey)
                .contains("error.duplicate_character_name");
    }

    @Test
    void shouldCarryTheServerReason_whenLoginIsRejected() {
        ConnectResponse rejected = ConnectResponse.newBuilder()
                .setSuccess(false)
                .setErrorKey("error.invalid_username")
                .build();

        assertThat(ReplyVerdict.of(rejected))
                .map(ReplyVerdict.Rejection::errorKey)
                .contains("error.invalid_username");
    }

    @Test
    void shouldNotReject_whenTheServerAccepted() {
        assertThat(ReplyVerdict.of(
                        SelectCharacterResponse.newBuilder().setSuccess(true).build()))
                .isEmpty();
        assertThat(ReplyVerdict.of(
                        CreateCharacterResponse.newBuilder().setSuccess(true).build()))
                .isEmpty();
        assertThat(ReplyVerdict.of(ConnectResponse.newBuilder().setSuccess(true).build()))
                .isEmpty();
    }
}

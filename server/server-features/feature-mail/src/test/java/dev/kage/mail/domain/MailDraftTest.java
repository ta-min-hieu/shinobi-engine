package dev.kage.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Ranh giới "dữ liệu client bẩn → outcome, không phải exception". Đây là chỗ duy nhất trong
 * feature-mail biết bề rộng cột {@code mail.subject}/{@code mail.body} (V13) — sai ở đây thì chuỗi
 * quá dài lại rơi xuống {@code SQLException} và người chơi không nhận được gói trả lời nào.
 */
class MailDraftTest {

    private static MailDraft draft(String subject, String body, long gold, List<MailAttachmentDraft> attachments) {
        return new MailDraft(7L, 5L, subject, body, gold, attachments);
    }

    @Test
    void shouldAcceptCleanDraft() {
        assertThat(draft("s", "b", 100, List.of(new MailAttachmentDraft(1, 2))).validate())
                .isEmpty();
    }

    @Test
    void shouldRejectNegativeGoldAmount() {
        assertThat(draft("s", "b", -1, List.of()).validate()).contains(MailContentViolation.GOLD_AMOUNT_NEGATIVE);
    }

    @Test
    void shouldAcceptZeroGoldAmount() {
        assertThat(draft("s", "b", 0, List.of()).validate()).isEmpty();
    }

    @Test
    void shouldRejectSubjectOverColumnWidth() {
        String tooLong = "s".repeat(MailDraft.MAX_SUBJECT_LENGTH + 1);
        assertThat(draft(tooLong, "b", 0, List.of()).validate()).contains(MailContentViolation.SUBJECT_TOO_LONG);
    }

    @Test
    void shouldAcceptSubjectExactlyAtColumnWidth() {
        String exact = "s".repeat(MailDraft.MAX_SUBJECT_LENGTH);
        assertThat(draft(exact, "b", 0, List.of()).validate()).isEmpty();
    }

    @Test
    void shouldRejectBodyOverColumnWidth() {
        String tooLong = "b".repeat(MailDraft.MAX_BODY_LENGTH + 1);
        assertThat(draft("s", tooLong, 0, List.of()).validate()).contains(MailContentViolation.BODY_TOO_LONG);
    }

    @Test
    void shouldAcceptBodyExactlyAtColumnWidth() {
        String exact = "b".repeat(MailDraft.MAX_BODY_LENGTH);
        assertThat(draft("s", exact, 0, List.of()).validate()).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        assertThat(draft("s", "b", 0, List.of(new MailAttachmentDraft(1, 0))).validate())
                .contains(MailContentViolation.ATTACHMENT_INVALID);
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThat(draft("s", "b", 0, List.of(new MailAttachmentDraft(1, -3))).validate())
                .contains(MailContentViolation.ATTACHMENT_INVALID);
    }

    @Test
    void shouldRejectNonPositiveItemTemplateId() {
        assertThat(draft("s", "b", 0, List.of(new MailAttachmentDraft(0, 1))).validate())
                .contains(MailContentViolation.ATTACHMENT_INVALID);
    }

    /** Vi phạm ở dòng đính kèm thứ hai cũng phải bắt được — không chỉ kiểm dòng đầu. */
    @Test
    void shouldRejectInvalidAttachmentAtAnyPosition() {
        List<MailAttachmentDraft> attachments = List.of(new MailAttachmentDraft(1, 1), new MailAttachmentDraft(2, -1));

        assertThat(draft("s", "b", 0, attachments).validate()).contains(MailContentViolation.ATTACHMENT_INVALID);
    }

    @Test
    void shouldConvertToNewMail_whenValid() {
        NewMail newMail =
                draft("s", "b", 42, List.of(new MailAttachmentDraft(1, 2))).toNewMail();

        assertThat(newMail.goldAmount()).isEqualTo(42);
        assertThat(newMail.attachments()).containsExactly(new MailAttachment(1, 2));
    }

    /** Chốt phòng thủ: bỏ qua validate() thì NewMail vẫn không cho dựng bản sai. */
    @Test
    void shouldStillThrow_whenConvertingInvalidDraft() {
        MailDraft invalid = draft("s", "b", -1, List.of());

        assertThatThrownBy(invalid::toNewMail).isInstanceOf(IllegalArgumentException.class);
    }
}

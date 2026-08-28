package dev.kage.mail.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Yêu cầu gửi thư CHƯA kiểm — đúng những gì client gửi lên, chưa tin gì cả (NETWORK.md §12).
 * {@link #validate()} biến nó thành outcome; chỉ khi hợp lệ mới dựng được {@link NewMail}.
 *
 * <p><b>Vì sao có type riêng thay vì kiểm ngay trong {@link NewMail}.</b> {@link NewMail} là thứ
 * đã hợp lệ — mọi chỗ nhận nó (repository, audit) được quyền tin. Nếu để nó nhận cả dữ liệu bẩn
 * rồi tự kiểm, cách duy nhất báo lỗi ra ngoài là ném exception, mà exception ném trong
 * {@code authExecutor} thì client chỉ thấy im lặng. Tách draft ra giữ được cả hai: biên giới bẩn
 * có chỗ đứng, biên giới sạch vẫn bất biến.
 *
 * <p><b>Giới hạn độ dài lấy từ đâu.</b> Đúng bằng bề rộng cột {@code mail.subject VARCHAR(64)} /
 * {@code mail.body VARCHAR(1024)} (migration V13). ⚠️ Đổi migration thì phải đổi ở đây — không
 * đồng bộ thì chuỗi quá dài lại rơi xuống {@code SQLException} như trước.
 */
public record MailDraft(
        Long senderId,
        long recipientId,
        String subject,
        String body,
        long goldAmount,
        List<MailAttachmentDraft> attachments) {

    /** Bằng {@code mail.subject VARCHAR(64)} (V13). */
    public static final int MAX_SUBJECT_LENGTH = 64;

    /** Bằng {@code mail.body VARCHAR(1024)} (V13). */
    public static final int MAX_BODY_LENGTH = 1024;

    public MailDraft {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(attachments, "attachments");
        attachments = List.copyOf(attachments);
    }

    /**
     * Kiểm mọi ràng buộc nội dung. Trả về vi phạm ĐẦU TIÊN tìm được (client sửa một lỗi rồi gửi
     * lại — báo hết một lượt không giúp gì thêm mà lại phải chở một danh sách qua wire).
     *
     * @return rỗng nghĩa là hợp lệ, {@link #toNewMail()} gọi được an toàn
     */
    public Optional<MailContentViolation> validate() {
        if (goldAmount < 0) {
            return Optional.of(MailContentViolation.GOLD_AMOUNT_NEGATIVE);
        }
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            return Optional.of(MailContentViolation.SUBJECT_TOO_LONG);
        }
        if (body.length() > MAX_BODY_LENGTH) {
            return Optional.of(MailContentViolation.BODY_TOO_LONG);
        }
        for (MailAttachmentDraft attachment : attachments) {
            if (!attachment.isValid()) {
                return Optional.of(MailContentViolation.ATTACHMENT_INVALID);
            }
        }
        return Optional.empty();
    }

    /**
     * Dựng {@link NewMail} tương ứng — chỉ gọi sau khi {@link #validate()} trả rỗng.
     *
     * @throws IllegalArgumentException nếu chưa qua {@link #validate()} — chốt phòng thủ, không
     *     phải đường báo lỗi cho client
     */
    public NewMail toNewMail() {
        List<MailAttachment> validated =
                attachments.stream().map(MailAttachmentDraft::toAttachment).toList();
        return new NewMail(senderId, recipientId, subject, body, goldAmount, validated);
    }
}

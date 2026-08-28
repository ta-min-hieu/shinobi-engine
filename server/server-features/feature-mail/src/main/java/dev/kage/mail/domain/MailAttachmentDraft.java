package dev.kage.mail.domain;

/**
 * 1 dòng đính kèm CHƯA kiểm — bản "thô" của {@link MailAttachment}, dùng cho dữ liệu đến từ client.
 *
 * <p>Cố ý KHÔNG có compact constructor: {@link MailAttachment} ném khi {@code quantity <= 0}, mà
 * ném ở đây thì client không nhận được outcome nào (xem {@link MailContentViolation}). Giá trị sai
 * phải sống đủ lâu để {@link MailDraft#validate()} biến nó thành một outcome tử tế.
 */
public record MailAttachmentDraft(long itemTemplateId, int quantity) {

    boolean isValid() {
        return itemTemplateId > 0 && quantity > 0;
    }

    MailAttachment toAttachment() {
        return new MailAttachment(itemTemplateId, quantity);
    }
}

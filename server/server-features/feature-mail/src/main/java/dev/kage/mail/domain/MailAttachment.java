package dev.kage.mail.domain;

/** 1 dòng item đính kèm thư — bản sao ý tưởng {@code dev.kage.shop.domain.OwnedItem}. */
public record MailAttachment(long itemTemplateId, int quantity) {

    public MailAttachment {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId phải > 0: " + itemTemplateId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
    }
}

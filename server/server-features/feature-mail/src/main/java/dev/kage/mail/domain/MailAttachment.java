package dev.kage.mail.domain;

import java.util.List;

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

    /**
     * Mảng JSON {@code [{"itemTemplateId":..,"quantity":..}]} cho {@code audit_log.detail} (dòng 66(b),
     * 2026-10-17) — trước đó audit thư chỉ có {@code itemCount}, không dựng lại được vật phẩm NÀO đã đi
     * qua thư. Chỉ có số nên không cần thoát ký tự.
     */
    public static String auditJson(List<MailAttachment> attachments) {
        StringBuilder json = new StringBuilder("[");
        for (MailAttachment attachment : attachments) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"itemTemplateId\":")
                    .append(attachment.itemTemplateId())
                    .append(",\"quantity\":")
                    .append(attachment.quantity())
                    .append('}');
        }
        return json.append(']').toString();
    }
}

package dev.kage.mail.domain;

import java.util.List;

/**
 * 1 dòng item đính kèm thư — bản sao ý tưởng {@code dev.kage.shop.domain.OwnedItem}. {@code enhanceLevel} (ADR-083):
 * món cường hoá đi qua thư GIỮ nguyên cấp; 0 = chưa cường hoá.
 */
public record MailAttachment(long itemTemplateId, int quantity, int enhanceLevel) {

    /** Bằng {@code ItemStack.MAX_ENHANCE} của feature-inventory (module này không import nó) và CHECK của V79. */
    public static final int MAX_ENHANCE = 15;

    /** Món chưa cường hoá. */
    public MailAttachment(long itemTemplateId, int quantity) {
        this(itemTemplateId, quantity, 0);
    }

    public MailAttachment {
        if (enhanceLevel < 0 || enhanceLevel > MAX_ENHANCE) {
            throw new IllegalArgumentException("enhanceLevel ngoài 0.." + MAX_ENHANCE + ": " + enhanceLevel);
        }
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
                    .append(attachment.quantity());
            if (attachment.enhanceLevel() > 0) {
                json.append(",\"enhanceLevel\":").append(attachment.enhanceLevel());
            }
            json.append('}');
        }
        return json.append(']').toString();
    }
}

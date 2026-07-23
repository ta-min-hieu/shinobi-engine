package dev.kage.chat.domain;

/**
 * Cổng ghi {@code audit_log} (bảng chung, V7) cho MỌI tin nhắn hợp lệ — không giữ nội dung chat
 * thật (không bảng riêng), audit_log là dấu vết DUY NHẤT khi có báo cáo (GAMEPLAY.md §10). Không
 * cần {@code Transaction} — 1 câu INSERT đơn, không có ghi nào khác cần nguyên tử hoá cùng lúc
 * (khác feature-shop/feature-mail).
 */
public interface ChatAuditPort {

    void log(long characterId, String action, String detailJson);
}

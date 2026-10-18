package dev.kage.quest.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Bỏ một quest đang làm (ADR-054) — xoá dòng tiến độ để người chơi nhận lại được từ đầu.
 *
 * <p>Chỉ quest {@code IN_PROGRESS}: {@code COMPLETED} chỉ còn việc lĩnh thưởng, {@code CLAIMED} không
 * bao giờ lùi. Đồ đã giao cho quest qua {@code ctx.take_item} KHÔNG được trả lại — script không khai đã
 * lấy gì; audit {@code QUEST_ABANDON} ghi nguyên {@code progress_json} lúc bỏ để hoàn tay được.
 */
public final class AbandonQuestUseCase {

    /** {@code quest_template.code VARCHAR(32)} (V11). */
    private static final int MAX_QUEST_CODE_LENGTH = 32;

    private final QuestProgressPort progressPort;
    private final QuestTemplatePort templatePort;
    private final AuditPort auditPort;
    private final TransactionRunner tx;

    public AbandonQuestUseCase(
            QuestProgressPort progressPort, QuestTemplatePort templatePort, AuditPort auditPort, TransactionRunner tx) {
        this.progressPort = progressPort;
        this.templatePort = templatePort;
        this.auditPort = auditPort;
        this.tx = tx;
    }

    public AbandonResult execute(long characterId, String questCode) {
        // Mã đến thẳng từ gói của client: chặn TRƯỚC khi chạm DB. Postgres từ chối chuỗi có U+0000
        // bằng một lỗi 500 (GatewayOpcodeFuzzDatabaseTest bắt được ở bản đầu); mã thật chỉ có chữ
        // thường, số, gạch dưới, ≤ 32 ký tự (V11 `code VARCHAR(32)`).
        if (!isWellFormedCode(questCode)) {
            return AbandonResult.QUEST_NOT_FOUND;
        }
        Optional<QuestTemplate> template = templatePort.findByCode(questCode);
        if (template.isEmpty()) {
            return AbandonResult.QUEST_NOT_FOUND;
        }
        long questTemplateId = template.get().id();
        AtomicReference<AbandonResult> result = new AtomicReference<>(AbandonResult.NOT_IN_PROGRESS);
        tx.inTransaction((Transaction t) -> progressPort
                .deleteIfInProgress(t, characterId, questTemplateId)
                .ifPresent(progressJson -> {
                    auditPort.log(t, characterId, "QUEST_ABANDON", detailJson(questCode, progressJson));
                    result.set(AbandonResult.OK);
                }));
        return result.get();
    }

    private static boolean isWellFormedCode(String code) {
        if (code.isEmpty() || code.length() > MAX_QUEST_CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code progress_json} là chuỗi đục do script tự quản (V11) — nhúng như một CHUỖI JSON đã thoát
     * ký tự, không nhúng thô: nội dung nó không do Java kiểm, nhúng thô thì một script ghi chuỗi lạ là
     * đủ làm hỏng cột {@code detail} của audit.
     */
    private static String detailJson(String questCode, String progressJson) {
        return "{\"questCode\":" + jsonString(questCode) + ",\"progress\":" + jsonString(progressJson) + "}";
    }

    private static String jsonString(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public enum AbandonResult {
        OK,
        QUEST_NOT_FOUND,
        /** Chưa nhận, đã hoàn thành (chờ lĩnh), hoặc đã lĩnh — không có gì để bỏ. */
        NOT_IN_PROGRESS
    }
}

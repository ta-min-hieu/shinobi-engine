package dev.kage.gm.domain;

import java.util.OptionalLong;

/**
 * Một dòng audit lúc MỞ (ADR-052 D5).
 *
 * @param commandLine nguyên văn dòng gõ — cắt về 256 ký tự (cột {@code VARCHAR(256)})
 */
public record GmAuditEntry(
        long actorAccountId,
        long actorCharacterId,
        GmRole actorRole,
        String command,
        String commandLine,
        OptionalLong targetCharacterId) {

    /** Trần cột {@code gm_audit_log.command_line}. */
    public static final int MAX_COMMAND_LINE = 256;

    public GmAuditEntry {
        if (commandLine.length() > MAX_COMMAND_LINE) {
            commandLine = commandLine.substring(0, MAX_COMMAND_LINE);
        }
    }
}

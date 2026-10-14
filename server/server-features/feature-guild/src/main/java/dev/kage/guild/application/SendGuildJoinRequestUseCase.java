package dev.kage.guild.application;

import java.util.Optional;

import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildDisbandedException;
import dev.kage.guild.domain.GuildName;
import dev.kage.guild.domain.GuildPort;

/**
 * Xin gia nhập bang theo tên — leader duyệt/từ chối sau (quyết định cùng chủ dự án 2026-07-23,
 * khác feature-friend là mời trực tiếp). Không chặn 1 nhân vật có đơn PENDING ở bang này trong
 * lúc TỰ TẠO bang khác — xem {@link GuildPort.ApproveResult#TARGET_ALREADY_IN_GUILD}.
 */
public final class SendGuildJoinRequestUseCase {

    private final GuildPort port;

    public SendGuildJoinRequestUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId, String guildName) {
        if (port.findMembershipOf(characterId).isPresent()) {
            return Result.ALREADY_IN_GUILD;
        }
        // KHÔNG lọc bằng GuildName.isValid: bang tạo trước 2026-09-29 có thể mang tên ngoài luật mới
        // (xem javadoc GuildName) mà vẫn phải xin vào được. Chỉ chặn đúng thứ chắc chắn không thể có
        // trong DB — U+0000, ký tự Postgres từ chối ngay cả ở câu SELECT (từng ra 500, 2026-10-02).
        if (guildName.indexOf('\0') >= 0) {
            return Result.GUILD_NOT_FOUND;
        }
        Optional<Guild> guild = findExactThenNormalized(guildName);
        if (guild.isEmpty()) {
            return Result.GUILD_NOT_FOUND;
        }
        if (port.findPendingRequestOfCharacter(characterId).isPresent()) {
            return Result.REQUEST_ALREADY_PENDING;
        }
        try {
            port.sendJoinRequest(guild.get().id(), characterId);
        } catch (DuplicateGuildJoinRequestException e) {
            return Result.REQUEST_ALREADY_PENDING;
        } catch (GuildDisbandedException e) {
            // findByName ở trên rồi mới ghi đơn — leader giải tán đúng giữa hai bước (2026-10-03,
            // trước đó thành 500). Bang không còn thì đúng là "không tìm thấy".
            return Result.GUILD_NOT_FOUND;
        }
        return Result.OK;
    }

    /**
     * Nguyên văn trước, dạng NFC sau (2026-10-14, README dòng 42): bang mới lưu tên NFC
     * ({@link GuildName#normalize}), còn bang tạo trước 2026-09-29 mang tên dạng nào cũng phải xin vào được.
     */
    private Optional<Guild> findExactThenNormalized(String guildName) {
        Optional<Guild> exact = port.findByName(guildName);
        if (exact.isPresent()) {
            return exact;
        }
        String normalized = GuildName.normalize(guildName);
        return normalized.equals(guildName) ? exact : port.findByName(normalized);
    }

    public enum Result {
        OK,
        ALREADY_IN_GUILD,
        GUILD_NOT_FOUND,
        REQUEST_ALREADY_PENDING
    }
}

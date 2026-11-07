package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Set;

import dev.kage.protocol.ListStorageRequest;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.StorageKind;

/**
 * Kho bang (ADR-198) — lệnh chat {@code /khobang} (hoặc {@code /guildbank}) hỏi kho bang; cửa sổ kho
 * ({@code StorageCoordinator}) tự mở khi server trả lời. Dùng ở đâu cũng được; server kiểm thành viên / quyền rút.
 */
final class GuildBankCoordinator implements WindowFlow {

    private static final Set<String> COMMANDS = Set.of("/khobang", "/guildbank");

    private final WorldCommandSender sender;

    GuildBankCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_STORAGE_REQUEST_VALUE,
                ListStorageRequest.newBuilder()
                        .setStorageKind(StorageKind.STORAGE_KIND_GUILD)
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(dev.kage.protocol.wire.PacketCodec.Frame frame) {
        return false; // chỉ gửi lệnh — StorageCoordinator nhận trả lời
    }
}

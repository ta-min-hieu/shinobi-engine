package dev.kage.client.core.world;

import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tab tự hoàn thành lệnh chat (ADR-380) — đang gõ {@code /tr} rồi bấm Tab: một lệnh khớp ⇒ điền đủ kèm dấu cách;
 * nhiều lệnh ⇒ điền phần chung dài nhất, nếu không thêm được chữ nào thì in tối đa {@value #MAX_LISTED} lệnh khớp.
 * Danh sách lệnh lấy từ bảng {@link CommandHelpCoordinator#GROUPS} (cùng nguồn với {@code /trogiup}).
 */
final class ChatCompletionCoordinator implements WindowFlow {

    /** Số lệnh khớp in ra tối đa. */
    static final int MAX_LISTED = 8;

    static final List<String> COMMANDS = CommandHelpCoordinator.GROUPS.stream()
            .flatMap(group -> group.commands().stream())
            .distinct()
            .sorted()
            .toList();

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ChatCompletionCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.panel().setCompleter(this::complete);
    }

    private String complete(String typed) {
        List<String> matches = matches(typed);
        String completed = completion(typed, matches);
        if (completed.equals(typed) && matches.size() > 1) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "chat_complete.many",
                    String.join(" ", matches.subList(0, Math.min(MAX_LISTED, matches.size()))),
                    matches.size()));
        }
        return completed;
    }

    /** Lệnh bắt đầu bằng chữ đang gõ (chỉ khi đang gõ tên lệnh — có "/" và chưa có dấu cách). Tĩnh để test. */
    static List<String> matches(String typed) {
        if (!typed.startsWith("/") || typed.indexOf(' ') >= 0) {
            return List.of();
        }
        String prefix = typed.toLowerCase(Locale.ROOT);
        return COMMANDS.stream().filter(command -> command.startsWith(prefix)).toList();
    }

    /** Chữ sau khi hoàn thành theo danh sách khớp. Tĩnh để test. */
    static String completion(String typed, List<String> matches) {
        if (matches.isEmpty()) {
            return typed;
        }
        if (matches.size() == 1) {
            return matches.getFirst() + " ";
        }
        String common = matches.getFirst();
        for (String match : matches) {
            int length = 0;
            while (length < common.length()
                    && length < match.length()
                    && common.charAt(length) == match.charAt(length)) {
                length++;
            }
            common = common.substring(0, length);
        }
        return common.length() > typed.length() ? common : typed;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}

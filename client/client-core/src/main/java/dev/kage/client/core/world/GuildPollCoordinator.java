package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CreateGuildPollCommand;
import dev.kage.protocol.CreateGuildPollOutcome;
import dev.kage.protocol.CreateGuildPollResponse;
import dev.kage.protocol.GetGuildPollRequest;
import dev.kage.protocol.GetGuildPollResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.VoteGuildPollCommand;
import dev.kage.protocol.VoteGuildPollOutcome;
import dev.kage.protocol.VoteGuildPollResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bình chọn trong bang (ADR-248) — {@code /binhchon} (hoặc {@code /poll}) xem cuộc gần nhất; {@code /binhchon <số>} bỏ
 * phiếu; {@code /binhchon tao <câu hỏi> | <lựa chọn 1> | <lựa chọn 2> …} (bang chủ / phó) mở cuộc mới.
 */
final class GuildPollCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/binhchon";
    private static final String COMMAND_EN = "/poll";
    private static final String CREATE_OUTCOME_PREFIX = "CREATE_GUILD_POLL_OUTCOME_";
    private static final String VOTE_OUTCOME_PREFIX = "VOTE_GUILD_POLL_OUTCOME_";
    private static final long SECONDS_PER_HOUR = 3600L;

    /** Câu hỏi + các lựa chọn đã tách. */
    record Draft(String question, List<String> options) {}

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildPollCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Tách {@code câu hỏi | a | b …}; thiếu câu hỏi hoặc dưới 2 lựa chọn ⇒ rỗng. Tĩnh để test. */
    static Optional<Draft> parseCreate(String argument) {
        String[] parts = argument.split("\\|", -1);
        List<String> options = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                options.add(parts[i].strip());
            }
        }
        if (parts.length == 0 || parts[0].isBlank() || options.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new Draft(parts[0].strip(), options));
    }

    /** Số thứ tự người chơi gõ (1-based, 1–4) ⇒ chỉ số 0-based; khác ⇒ rỗng. Tĩnh để test. */
    static Optional<Integer> parseVote(String argument) {
        String trimmed = argument.strip();
        return trimmed.matches("[1-4]") ? Optional.of(Integer.parseInt(trimmed) - 1) : Optional.empty();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        String argument = words.length > 1 ? words[1].strip() : "";
        if (argument.isEmpty()) {
            sender.sendReadRequest(
                    Opcode.OPCODE_GET_GUILD_POLL_REQUEST_VALUE, GetGuildPollRequest.getDefaultInstance());
        } else if (parseVote(argument).isPresent()) {
            sender.sendReadRequest(
                    Opcode.OPCODE_VOTE_GUILD_POLL_COMMAND_VALUE,
                    VoteGuildPollCommand.newBuilder()
                            .setOptionIndex(parseVote(argument).get())
                            .build());
        } else {
            create(argument);
        }
        return true;
    }

    private void create(String argument) {
        String[] head = argument.split("\\s+", 2);
        boolean createWord = "tao".equalsIgnoreCase(head[0]) || "new".equalsIgnoreCase(head[0]);
        Optional<Draft> draft = createWord && head.length > 1 ? parseCreate(head[1]) : Optional.empty();
        if (draft.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "poll.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CREATE_GUILD_POLL_COMMAND_VALUE,
                CreateGuildPollCommand.newBuilder()
                        .setQuestion(draft.get().question())
                        .addAllOptions(draft.get().options())
                        .build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_GUILD_POLL_RESPONSE_VALUE) {
            show(GetGuildPollResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_CREATE_GUILD_POLL_RESPONSE_VALUE) {
            CreateGuildPollOutcome outcome =
                    CreateGuildPollResponse.parseFrom(frame.payload()).getOutcome();
            chat.showSystemNotice(
                    KageI18n.textOrKey(i18n, "poll.create." + suffix(outcome.name(), CREATE_OUTCOME_PREFIX)));
            return true;
        }
        if (opcode == Opcode.OPCODE_VOTE_GUILD_POLL_RESPONSE_VALUE) {
            VoteGuildPollOutcome outcome =
                    VoteGuildPollResponse.parseFrom(frame.payload()).getOutcome();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "poll.vote." + suffix(outcome.name(), VOTE_OUTCOME_PREFIX)));
            return true;
        }
        return false;
    }

    private static String suffix(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()).toLowerCase(Locale.ROOT) : "unspecified";
    }

    private void show(GetGuildPollResponse poll) {
        if (!poll.getHasPoll()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "poll.none"));
            return;
        }
        long hours = (poll.getSecondsLeft() + SECONDS_PER_HOUR - 1) / SECONDS_PER_HOUR;
        chat.showSystemNotice(
                poll.getSecondsLeft() > 0
                        ? KageI18n.formatOrKey(i18n, "poll.header.open", poll.getQuestion(), hours)
                        : KageI18n.formatOrKey(i18n, "poll.header.closed", poll.getQuestion()));
        for (int i = 0; i < poll.getOptionsCount(); i++) {
            int votes = i < poll.getVotesCount() ? poll.getVotes(i) : 0;
            String key = i == poll.getMyVote() ? "poll.option.mine" : "poll.option";
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, i + 1, poll.getOptions(i), votes));
        }
    }
}

package dev.kage.client.core.world;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CancelGuildEventCommand;
import dev.kage.protocol.CancelGuildEventResponse;
import dev.kage.protocol.CreateGuildEventCommand;
import dev.kage.protocol.CreateGuildEventResponse;
import dev.kage.protocol.GuildEventView;
import dev.kage.protocol.ListGuildEventsRequest;
import dev.kage.protocol.ListGuildEventsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch hoạt động bang (ADR-256) — {@code /lichbang} xem; {@code /lichbang them <HH:mm> <nội dung>} (bang chủ / phó) hẹn
 * lúc HH:mm giờ máy mình — đã qua thì là ngày mai; {@code /lichbang huy <số>} huỷ buổi số đó trong danh sách vừa xem.
 */
final class GuildEventCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/lichbang";
    private static final String COMMAND_EN = "/guildevent";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Long> lastListed = new ArrayList<>();

    GuildEventCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Giờ {@code HH:mm} hôm nay theo {@code zone}; đã qua (so với {@code now}) thì ngày mai. Sai dạng ⇒ rỗng. */
    static Optional<Instant> nextAt(String hhmm, Instant now, ZoneId zone) {
        if (!hhmm.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]")) {
            return Optional.empty();
        }
        String[] parts = hhmm.split(":", 2);
        LocalTime time = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        LocalDateTime today = LocalDateTime.of(now.atZone(zone).toLocalDate(), time);
        Instant at = today.atZone(zone).toInstant();
        return Optional.of(at.isAfter(now) ? at : today.plusDays(1).atZone(zone).toInstant());
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        handle(words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "", words.length > 2 ? words[2].strip() : "");
        return true;
    }

    private void handle(String action, String rest) {
        switch (action) {
            case "" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_LIST_GUILD_EVENTS_REQUEST_VALUE, ListGuildEventsRequest.getDefaultInstance());
            case "them", "add" -> create(rest);
            case "huy", "cancel" -> cancel(rest);
            default -> chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.usage"));
        }
    }

    private void create(String rest) {
        String[] parts = rest.split("\\s+", 2);
        Optional<Instant> at = nextAt(parts[0], Instant.now(), ZoneId.systemDefault());
        if (at.isEmpty() || parts.length < 2 || parts[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CREATE_GUILD_EVENT_COMMAND_VALUE,
                CreateGuildEventCommand.newBuilder()
                        .setTitle(parts[1].strip())
                        .setStartsAtEpochSeconds(at.get().getEpochSecond())
                        .build());
    }

    private void cancel(String rest) {
        int index = rest.matches("[0-9]{1,2}") ? Integer.parseInt(rest) - 1 : -1;
        if (index < 0 || index >= lastListed.size()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.cancel_usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CANCEL_GUILD_EVENT_COMMAND_VALUE,
                CancelGuildEventCommand.newBuilder()
                        .setEventId(lastListed.get(index))
                        .build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_LIST_GUILD_EVENTS_RESPONSE_VALUE) {
            show(ListGuildEventsResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_CREATE_GUILD_EVENT_RESPONSE_VALUE) {
            String name = CreateGuildEventResponse.parseFrom(frame.payload())
                    .getOutcome()
                    .name();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.create." + suffix(name)));
            return true;
        }
        if (opcode == Opcode.OPCODE_CANCEL_GUILD_EVENT_RESPONSE_VALUE) {
            String name = CancelGuildEventResponse.parseFrom(frame.payload())
                    .getOutcome()
                    .name();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.cancel." + suffix(name)));
            return true;
        }
        return false;
    }

    /** Phần đuôi của tên outcome ({@code ..._OUTCOME_NOT_OFFICER} ⇒ {@code not_officer}). */
    private static String suffix(String outcomeName) {
        int at = outcomeName.indexOf("_OUTCOME_");
        return at < 0
                ? "unspecified"
                : outcomeName.substring(at + "_OUTCOME_".length()).toLowerCase(Locale.ROOT);
    }

    private void show(ListGuildEventsResponse response) {
        lastListed.clear();
        if (response.getEventsCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.none"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildevent.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (GuildEventView event : response.getEventsList()) {
            lastListed.add(event.getEventId());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "guildevent.line",
                    lastListed.size(),
                    TIME.format(Instant.ofEpochSecond(event.getStartsAtEpochSeconds())
                            .atZone(zone)),
                    event.getTitle()));
        }
    }
}

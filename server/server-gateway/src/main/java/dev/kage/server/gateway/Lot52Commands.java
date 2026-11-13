package dev.kage.server.gateway;

import java.util.Locale;
import java.util.Optional;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.NameHistoryPort;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildTagFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.FriendRemovedEvent;
import dev.kage.protocol.GetNameHistoryRequest;
import dev.kage.protocol.GetNameHistoryResponse;
import dev.kage.protocol.InspectCharacterOutcome;
import dev.kage.protocol.InspectCharacterResponse;
import dev.kage.protocol.OldName;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildTagCommand;
import dev.kage.protocol.SetGuildTagOutcome;
import dev.kage.protocol.SetGuildTagResponse;

/** Lớp lệnh lô 52 (ADR-343…) — MỘT thành phần của {@link Lot51Commands}. Gọi trên Virtual Thread. */
record Lot52Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Tên dài hơn mức này chắc chắn không phải tên nhân vật (tên tối đa 16 ký tự). */
    static final int MAX_NAME_LENGTH = 32;

    /** Lớp lệnh lô 53 (ADR-351…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot53Commands lot53() {
        return new Lot53Commands(gateway, registry);
    }

    /** ADR-343 — tên cũ của người đang mang (hoặc từng mang) tên {@code name}. */
    GetNameHistoryResponse nameHistory(GetNameHistoryRequest request) {
        String name = request.getName().strip();
        if (!searchableName(name)) {
            return GetNameHistoryResponse.newBuilder().setFound(false).build();
        }
        Optional<Character> found = gateway.characterFacade()
                .findByName(name)
                .or(() -> facades().nameHistory().characterWithOldName(name).flatMap(id -> gateway.characterFacade()
                        .findById(id)));
        if (found.isEmpty()) {
            return GetNameHistoryResponse.newBuilder().setFound(false).build();
        }
        GetNameHistoryResponse.Builder response = GetNameHistoryResponse.newBuilder()
                .setFound(true)
                .setCurrentName(found.get().name());
        for (NameHistoryPort.Entry entry :
                facades().nameHistory().history(found.get().id())) {
            response.addOldNames(OldName.newBuilder()
                    .setName(entry.oldName())
                    .setChangedAtEpochSeconds(entry.changedAt().getEpochSecond()));
        }
        return response.build();
    }

    /** Tên đáng tra: không rỗng, ≤ 32 ký tự, không ký tự điều khiển (NUL làm Postgres từ chối). Tĩnh để test. */
    static boolean searchableName(String name) {
        return !name.isEmpty()
                && name.length() <= MAX_NAME_LENGTH
                && name.chars().noneMatch(java.lang.Character::isISOControl);
    }

    /** ADR-344 — hỏi / đặt / xoá ký hiệu bang (chỉ bang chủ đặt). */
    SetGuildTagResponse setGuildTag(long characterId, SetGuildTagCommand command) {
        Optional<GuildFacade.MyGuildView> mine =
                gateway.contentFacades().socialFacades().guildFacade().findMyGuild(characterId);
        if (mine.isEmpty()) {
            return tag(SetGuildTagOutcome.SET_GUILD_TAG_OUTCOME_NOT_IN_GUILD, "");
        }
        GuildTagFacade tags = facades().guildTags();
        long guildId = mine.get().guildId();
        if (command.getQueryOnly()) {
            return tag(SetGuildTagOutcome.SET_GUILD_TAG_OUTCOME_OK, tags.tagOf(guildId));
        }
        if (!mine.get().isLeader()) {
            return tag(SetGuildTagOutcome.SET_GUILD_TAG_OUTCOME_NOT_LEADER, tags.tagOf(guildId));
        }
        String wanted = command.getTag().strip().toUpperCase(Locale.ROOT);
        if (!wanted.isEmpty() && !GuildTagFacade.valid(wanted)) {
            return tag(SetGuildTagOutcome.SET_GUILD_TAG_OUTCOME_INVALID, tags.tagOf(guildId));
        }
        if (!tags.setTag(guildId, wanted)) {
            return tag(SetGuildTagOutcome.SET_GUILD_TAG_OUTCOME_TAKEN, tags.tagOf(guildId));
        }
        return tag(SetGuildTagOutcome.SET_GUILD_TAG_OUTCOME_OK, wanted);
    }

    private static SetGuildTagResponse tag(SetGuildTagOutcome outcome, String tag) {
        return SetGuildTagResponse.newBuilder().setOutcome(outcome).setTag(tag).build();
    }

    /** ADR-344 — ký hiệu bang gắn vào tin chat kênh thế giới / giao dịch ("" ở kênh khác hoặc khi không có). */
    String chatTagOf(ChatChannel channel, long senderId) {
        return channel == ChatChannel.CHAT_CHANNEL_WORLD || channel == ChatChannel.CHAT_CHANNEL_TRADE
                ? facades().guildTags().tagOfCharacter(senderId)
                : "";
    }

    /** ADR-344 — kèm ký hiệu bang vào kết quả soi trang bị. */
    InspectCharacterResponse withGuildTag(InspectCharacterResponse inspected) {
        if (inspected.getOutcome() != InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_OK) {
            return inspected;
        }
        return gateway.characterFacade()
                .findByName(inspected.getName())
                .map(found -> inspected.toBuilder()
                        .setGuildTag(facades().guildTags().tagOfCharacter(found.id()))
                        .build())
                .orElse(inspected);
    }

    /** ADR-345 — {@code removerId} vừa xoá {@code removedId} khỏi danh sách bạn bè: người bị xoá (online) được báo. */
    void friendRemoved(long removerId, long removedId) {
        gateway.characterFacade()
                .findById(removerId)
                .ifPresent(remover -> registry.sendToCharacterIfOnline(
                        removedId,
                        Opcode.OPCODE_FRIEND_REMOVED_EVENT_VALUE,
                        0,
                        FriendRemovedEvent.newBuilder()
                                .setCharacterId(removerId)
                                .setName(remover.name())
                                .build()
                                .toByteArray()));
    }

    private Lot52Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45()
                .lot46()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52();
    }
}

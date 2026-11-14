package dev.kage.server.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.Character;
import dev.kage.chat.domain.ChatMessage;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.friend.api.LoginGreetingFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.domain.GuildNameHistoryPort;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetGuildNameHistoryRequest;
import dev.kage.protocol.GetGuildNameHistoryResponse;
import dev.kage.protocol.GuildRename;
import dev.kage.protocol.InviteOnlineFriendsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameGuildResponse;
import dev.kage.protocol.SetEnemyAlertCommand;
import dev.kage.protocol.SetEnemyAlertResponse;
import dev.kage.protocol.SetLoginGreetingCommand;
import dev.kage.protocol.SetLoginGreetingResponse;

/** Lớp lệnh lô 62 (ADR-423…) — MỘT thành phần của {@link Lot61Commands}. Gọi trên Virtual Thread. */
record Lot62Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    private static final Logger LOG = LoggerFactory.getLogger(Lot62Commands.class);

    /** Lớp lệnh lô 63 (ADR-431…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot63Commands lot63() {
        return new Lot63Commands(gateway, registry);
    }

    /** ADR-423 — tên bang hiện tại của người gọi (trước khi đổi tên). */
    Optional<GuildFacade.MyGuildView> myGuild(long characterId) {
        return guilds().findMyGuild(characterId);
    }

    /** ADR-423 — đổi tên thành công ⇒ ghi một dòng lịch sử (tên cũ → tên mới). Trả nguyên phản hồi. */
    RenameGuildResponse rememberRename(Optional<GuildFacade.MyGuildView> before, RenameGuildResponse response) {
        if (response.getOutcome() == dev.kage.protocol.RenameGuildOutcome.RENAME_GUILD_OUTCOME_OK
                && before.isPresent()) {
            facades()
                    .guildNames()
                    .record(before.get().guildId(), before.get().guildName(), response.getName(), Instant.now());
        }
        return response;
    }

    /** ADR-423 — lịch sử tên của một bang (tên rỗng = bang mình; nhận cả tên cũ). */
    GetGuildNameHistoryResponse guildNameHistory(long characterId, GetGuildNameHistoryRequest request) {
        String name = request.getGuildName().strip();
        Optional<Long> guildId = name.isEmpty()
                ? myGuild(characterId).map(GuildFacade.MyGuildView::guildId)
                : facades().guildNames().findGuildId(name);
        if (guildId.isEmpty()) {
            return GetGuildNameHistoryResponse.newBuilder().setFound(false).build();
        }
        List<GuildNameHistoryPort.Rename> renames = facades().guildNames().history(guildId.get());
        GetGuildNameHistoryResponse.Builder response =
                GetGuildNameHistoryResponse.newBuilder().setFound(true);
        for (GuildNameHistoryPort.Rename rename : renames) {
            response.addRenames(GuildRename.newBuilder()
                    .setOldName(rename.oldName())
                    .setNewName(rename.newName())
                    .setAtEpochSeconds(rename.at().getEpochSecond()));
        }
        response.setCurrentName(renames.isEmpty() ? name : renames.getLast().newName());
        myGuild(characterId)
                .filter(mine -> mine.guildId() == guildId.get())
                .ifPresent(mine -> response.setCurrentName(mine.guildName()));
        return response.build();
    }

    /** ADR-424 — mời mọi bạn bè đang online (chưa ở nhóm) vào nhóm; chưa có nhóm thì lập nhóm trước. */
    InviteOnlineFriendsResponse inviteOnlineFriends(long characterId) {
        PartyFacade parties = gateway.contentFacades().socialFacades().partyFacade();
        Optional<PartyFacade.MyPartyView> party = parties.findMyParty(characterId);
        if (party.isPresent() && !party.get().isLeader()) {
            return InviteOnlineFriendsResponse.newBuilder().setNotLeader(true).build();
        }
        if (party.isEmpty()) {
            parties.createParty(characterId);
        }
        int invited = 0;
        for (long friend :
                gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId)) {
            if (!registry.isOnline(friend)) {
                continue;
            }
            PartyFacade.InviteResult result = parties.invite(characterId, friend);
            if (result == PartyFacade.InviteResult.OK) {
                invited++;
            } else if (result == PartyFacade.InviteResult.PARTY_FULL) {
                break;
            }
        }
        return InviteOnlineFriendsResponse.newBuilder().setInvited(invited).build();
    }

    /** ADR-425 — đặt / xoá / hỏi câu chào khi vào game. */
    SetLoginGreetingResponse setLoginGreeting(long characterId, SetLoginGreetingCommand command) {
        LoginGreetingFacade greetings = facades().greetings();
        if (command.getQueryOnly()) {
            return SetLoginGreetingResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage(greetings.greetingOf(characterId))
                    .build();
        }
        Optional<String> clean = cleanGreeting(command.getMessage());
        clean.ifPresent(message -> greetings.set(characterId, message));
        return SetLoginGreetingResponse.newBuilder()
                .setAccepted(clean.isPresent())
                .setMessage(greetings.greetingOf(characterId))
                .build();
    }

    /** Một dòng, bỏ ký tự điều khiển, ≤ 60 ký tự, không từ cấm; rỗng = xoá. Tĩnh để test. */
    static Optional<String> cleanGreeting(String raw) {
        String line = ChatMessage.singleLine(raw)
                .codePoints()
                .filter(cp -> !java.lang.Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        if (line.length() > LoginGreetingFacade.MAX_LENGTH || ProfanityFilter.containsBannedWord(line)) {
            return Optional.empty();
        }
        return Optional.of(line);
    }

    /** ADR-426 — bật / tắt / hỏi báo cừu nhân online. */
    SetEnemyAlertResponse setEnemyAlert(long characterId, SetEnemyAlertCommand command) {
        if (!command.getQueryOnly()) {
            facades().enemyAlerts().setEnabled(characterId, command.getEnabled());
        }
        return SetEnemyAlertResponse.newBuilder()
                .setEnabled(facades().enemyAlerts().enabled(characterId))
                .build();
    }

    /**
     * Vào world: câu chào tới bạn bè đang online (ADR-425, không gửi khi đang ẩn — ADR-383) và báo những ai ghi mình là
     * cừu nhân (ADR-426). Việc phụ — lỗi dừng ở đây.
     */
    // PMD AvoidCatchingGenericException: việc phụ của lần vào world.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onEnteredWorld(long characterId) {
        try {
            String name = gateway.characterFacade()
                    .findById(characterId)
                    .map(Character::name)
                    .orElse("?");
            String greeting = facades().greetings().greetingOf(characterId);
            if (!greeting.isEmpty() && !new Lot57Commands(gateway, registry).isInvisible(characterId)) {
                byte[] line = systemLine(name + ": \"" + greeting + "\"");
                for (long friend :
                        gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId)) {
                    registry.sendToCharacterIfOnline(friend, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, line);
                }
            }
            byte[] alert = systemLine("Cừu nhân " + name + " vừa vào game. / Your enemy " + name + " is online.");
            for (long watcher : facades().enemyAlerts().watchersOf(characterId)) {
                registry.sendToCharacterIfOnline(watcher, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, alert);
            }
        } catch (RuntimeException e) {
            LOG.warn("Không gửi được câu chào / báo cừu nhân: characterId={}", characterId, e);
        }
    }

    /** Dòng hệ thống (không người gửi). Tĩnh để test. */
    static byte[] systemLine(String body) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build()
                .toByteArray();
    }

    private GuildFacade guilds() {
        return gateway.contentFacades().socialFacades().guildFacade();
    }

    private Lot62Facades facades() {
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
                .lot52()
                .lot53()
                .lot54()
                .lot56()
                .lot57()
                .lot58()
                .lot59()
                .lot61()
                .lot62();
    }
}

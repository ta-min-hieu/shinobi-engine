package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildPollFacade;
import dev.kage.guild.domain.GuildPollPort;
import dev.kage.inventory.domain.ItemHistoryPort;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.CreateGuildPollCommand;
import dev.kage.protocol.CreateGuildPollOutcome;
import dev.kage.protocol.CreateGuildPollResponse;
import dev.kage.protocol.GetGuildPollResponse;
import dev.kage.protocol.GetLootHistoryResponse;
import dev.kage.protocol.LootHistoryEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.VoteGuildPollCommand;
import dev.kage.protocol.VoteGuildPollOutcome;
import dev.kage.protocol.VoteGuildPollResponse;

/** Lớp lệnh lô 40 (ADR-247…) — MỘT thành phần của {@link Lot39Commands}. Gọi trên Virtual Thread. */
record Lot40Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 41 (ADR-255…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot41Commands lot41() {
        return new Lot41Commands(gateway, registry);
    }

    /** ADR-248 — bang chủ / phó tạo cuộc bình chọn (lọc từ cấm như chat); OK ⇒ báo cả bang đang online. */
    CreateGuildPollResponse createPoll(long characterId, CreateGuildPollCommand command) {
        boolean banned = ProfanityFilter.containsBannedWord(command.getQuestion())
                || command.getOptionsList().stream().anyMatch(ProfanityFilter::containsBannedWord);
        if (banned) {
            return CreateGuildPollResponse.newBuilder()
                    .setOutcome(CreateGuildPollOutcome.CREATE_GUILD_POLL_OUTCOME_BANNED_WORD)
                    .build();
        }
        GuildPollPort.CreateOutcome outcome =
                polls().create(characterId, command.getQuestion(), command.getOptionsList(), Instant.now());
        if (outcome == GuildPollPort.CreateOutcome.OK) {
            announcePoll(characterId, command.getQuestion().strip());
        }
        return CreateGuildPollResponse.newBuilder()
                .setOutcome(CreateGuildPollOutcome.valueOf("CREATE_GUILD_POLL_OUTCOME_" + outcome.name()))
                .build();
    }

    private void announcePoll(long creatorId, String question) {
        byte[] event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Bang có cuộc bình chọn mới: \"" + question + "\" — gõ /binhchon để xem. / New guild poll: \""
                        + question + "\" — type /poll to view.")
                .build()
                .toByteArray();
        GuildFacade guilds = gateway.contentFacades().socialFacades().guildFacade();
        for (GuildFacade.MemberView member : guilds.listMembers(creatorId)) {
            registry.sendToCharacterIfOnline(member.characterId(), Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event);
        }
    }

    /** ADR-248 — bỏ / đổi phiếu. */
    VoteGuildPollResponse vote(long characterId, VoteGuildPollCommand command) {
        GuildPollPort.VoteOutcome outcome = polls().vote(characterId, command.getOptionIndex(), Instant.now());
        return VoteGuildPollResponse.newBuilder()
                .setOutcome(VoteGuildPollOutcome.valueOf("VOTE_GUILD_POLL_OUTCOME_" + outcome.name()))
                .build();
    }

    /** ADR-248 — cuộc gần nhất của bang (mở hoặc đã đóng) kèm số phiếu. */
    GetGuildPollResponse poll(long characterId) {
        Optional<GuildPollPort.Poll> latest = polls().latest(characterId);
        if (latest.isEmpty()) {
            return GetGuildPollResponse.newBuilder()
                    .setHasPoll(false)
                    .setMyVote(-1)
                    .build();
        }
        GuildPollPort.Poll poll = latest.get();
        Instant now = Instant.now();
        return GetGuildPollResponse.newBuilder()
                .setHasPoll(true)
                .setQuestion(poll.question())
                .addAllOptions(poll.options())
                .addAllVotes(poll.votes())
                .setMyVote(poll.myVote())
                .setSecondsLeft(
                        now.isBefore(poll.closesAt())
                                ? Duration.between(now, poll.closesAt()).toSeconds()
                                : 0L)
                .build();
    }

    /** ADR-253 — 20 lần nhặt đồ gần nhất (đọc audit {@code LOOT_GRANT}). */
    GetLootHistoryResponse lootHistory(long characterId) {
        GetLootHistoryResponse.Builder response = GetLootHistoryResponse.newBuilder();
        for (ItemHistoryPort.LootRecord loot : gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .itemHistory()
                .lootHistory(characterId)) {
            response.addEntries(LootHistoryEntry.newBuilder()
                    .setItemTemplateId(loot.itemTemplateId())
                    .setQuantity(loot.quantity())
                    .setSource(loot.source())
                    .setAtEpochSeconds(loot.at().getEpochSecond()));
        }
        return response.build();
    }

    private GuildPollFacade polls() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .guildPoll();
    }
}

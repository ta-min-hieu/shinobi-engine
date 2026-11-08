package dev.kage.server.gateway;

import java.time.Instant;
import java.util.Optional;

import dev.kage.guild.api.GuildBuffFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.domain.GuildBuffPort;
import dev.kage.protocol.ActivateGuildBuffCommand;
import dev.kage.protocol.ActivateGuildBuffOutcome;
import dev.kage.protocol.ActivateGuildBuffResponse;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetPersonalRecordsResponse;
import dev.kage.protocol.GetQuestRewardPreviewRequest;
import dev.kage.protocol.GetQuestRewardPreviewResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestRewardPreviewOutcome;
import dev.kage.quest.application.PreviewQuestRewardUseCase;

/** Lớp lệnh lô 39 (ADR-239…) — MỘT thành phần của {@link Lot38Commands}. Gọi trên Virtual Thread. */
record Lot39Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 40 (ADR-247…) — dựng mỗi lần gọi (record rỗng trạng thái, rẻ). */
    Lot40Commands lot40() {
        return new Lot40Commands(gateway, registry);
    }

    /** ADR-240 — vào world: nạp kỷ lục đã lưu vào bộ theo dõi RAM. */
    void onEnteredWorld(long characterId) {
        registry.records.load(characterId);
    }

    /** ADR-239 — bang chủ bật buff (hoặc ai cũng hỏi còn bao lâu); bật xong báo cả bang đang online. */
    ActivateGuildBuffResponse guildBuff(long characterId, ActivateGuildBuffCommand command) {
        GuildBuffFacade buffs = facades().guildBuff();
        Instant now = Instant.now();
        ActivateGuildBuffResponse.Builder response = ActivateGuildBuffResponse.newBuilder()
                .setPercent(GuildBuffFacade.PERCENT)
                .setCost(GuildBuffFacade.COST);
        if (command.getQueryOnly()) {
            return response.setOutcome(ActivateGuildBuffOutcome.ACTIVATE_GUILD_BUFF_OUTCOME_STATUS)
                    .setSecondsLeft(buffs.secondsLeft(characterId, now))
                    .build();
        }
        GuildBuffPort.Activation activation = buffs.activate(characterId, now);
        if (activation.outcome() == GuildBuffPort.Outcome.OK) {
            announce(characterId);
        }
        long left = now.isBefore(activation.until())
                ? java.time.Duration.between(now, activation.until()).toSeconds()
                : 0L;
        return response.setOutcome(ActivateGuildBuffOutcome.valueOf(
                        "ACTIVATE_GUILD_BUFF_OUTCOME_" + activation.outcome().name()))
                .setSecondsLeft(left)
                .setGuildExpLeft(activation.guildExpLeft())
                .build();
    }

    private void announce(long leaderId) {
        GuildFacade guilds = gateway.contentFacades().socialFacades().guildFacade();
        byte[] event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Bang chủ đã bật buff bang: +" + GuildBuffFacade.PERCENT
                        + "% kinh nghiệm trong 1 giờ! / The guild leader activated a guild buff: +"
                        + GuildBuffFacade.PERCENT + "% EXP for 1 hour!")
                .build()
                .toByteArray();
        for (GuildFacade.MemberView member : guilds.listMembers(leaderId)) {
            registry.sendToCharacterIfOnline(member.characterId(), Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event);
        }
    }

    /** ADR-240 — kỷ lục đã lưu + chuỗi hiện tại. */
    GetPersonalRecordsResponse personalRecords(long characterId) {
        java.util.Map<dev.kage.character.domain.PersonalRecordPort.Kind, Long> saved =
                facades().personalRecords().recordsOf(characterId);
        return GetPersonalRecordsResponse.newBuilder()
                .setHighestHit(saved.getOrDefault(dev.kage.character.domain.PersonalRecordPort.Kind.HIGHEST_HIT, 0L))
                .setLongestKillStreak(
                        saved.getOrDefault(dev.kage.character.domain.PersonalRecordPort.Kind.LONGEST_KILL_STREAK, 0L))
                .setCurrentKillStreak(registry.records.currentStreak(characterId))
                .build();
    }

    /** ADR-242 — chạy on_claim không ghi gì để xem trước thưởng. */
    GetQuestRewardPreviewResponse questPreview(long characterId, GetQuestRewardPreviewRequest request) {
        GetQuestRewardPreviewResponse.Builder response =
                GetQuestRewardPreviewResponse.newBuilder().setQuestCode(request.getQuestCode());
        Optional<PreviewQuestRewardUseCase.Preview> preview =
                facades().questPreview().preview(characterId, request.getQuestCode());
        if (preview.isEmpty()) {
            return response.setOutcome(QuestRewardPreviewOutcome.QUEST_REWARD_PREVIEW_OUTCOME_NOT_FOUND)
                    .build();
        }
        if (!preview.get().available()) {
            return response.setOutcome(QuestRewardPreviewOutcome.QUEST_REWARD_PREVIEW_OUTCOME_UNAVAILABLE)
                    .build();
        }
        preview.get()
                .items()
                .forEach(item -> response.addItems(ItemRewardView.newBuilder()
                        .setItemTemplateCode(item.itemTemplateCode())
                        .setQuantity(item.quantity())));
        return response.setOutcome(QuestRewardPreviewOutcome.QUEST_REWARD_PREVIEW_OUTCOME_OK)
                .setExp(preview.get().exp())
                .setGold(preview.get().gold())
                .build();
    }

    /** ADR-241 — nguồn bảng giàu có (chạm DB; bộ đệm bảng xếp hạng gọi trên Virtual Thread). */
    java.util.Map<Long, Long> richest() {
        return facades().wealth().richest();
    }

    private Lot39Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39();
    }
}

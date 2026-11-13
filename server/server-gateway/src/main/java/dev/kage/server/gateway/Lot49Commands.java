package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.ReferralFacade;
import dev.kage.character.domain.BreakthroughLogPort;
import dev.kage.character.domain.Character;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildMemberNoteFacade;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.BreakthroughEntry;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.EnterReferralCommand;
import dev.kage.protocol.EnterReferralOutcome;
import dev.kage.protocol.EnterReferralResponse;
import dev.kage.protocol.GetBreakthroughHistoryResponse;
import dev.kage.protocol.GuildMemberNote;
import dev.kage.protocol.LinkedItemView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildMemberNoteCommand;
import dev.kage.protocol.SetGuildMemberNoteOutcome;
import dev.kage.protocol.SetGuildMemberNoteResponse;

/** Lớp lệnh lô 49 (ADR-319…) — MỘT thành phần của {@link Lot48Commands}. Gọi trên Virtual Thread. */
record Lot49Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-321 — cường hoá thành công từ mức này trở lên thì báo toàn server. */
    static final int ANNOUNCE_ENHANCE_LEVEL = 10;

    /** ADR-320 — linh thạch mỗi bên nhận khi người mới tới cấp {@value ReferralFacade#REWARD_LEVEL}. */
    static final long REFERRAL_GOLD = 2_000L;

    private static final Logger LOG = LoggerFactory.getLogger(Lot49Commands.class);
    private static final String REFERRAL_SUBJECT = "Quà giới thiệu / Referral gift";

    /** Lớp lệnh lô 50 (ADR-327…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot50Commands lot50() {
        return new Lot50Commands(gateway, registry);
    }

    /** ADR-319 — đọc mọi ghi chú, hoặc đặt / xoá ghi chú cho một thành viên (chỉ bang chủ, phó bang chủ). */
    SetGuildMemberNoteResponse setMemberNote(long characterId, SetGuildMemberNoteCommand command) {
        Optional<GuildFacade.MyGuildView> mine = guilds().findMyGuild(characterId);
        if (mine.isEmpty()) {
            return note(SetGuildMemberNoteOutcome.SET_GUILD_MEMBER_NOTE_OUTCOME_NOT_IN_GUILD, Map.of());
        }
        if (!mine.get().isLeader() && !mine.get().isViceLeader()) {
            return note(SetGuildMemberNoteOutcome.SET_GUILD_MEMBER_NOTE_OUTCOME_NOT_OFFICER, Map.of());
        }
        long guildId = mine.get().guildId();
        GuildMemberNoteFacade notes = facades().memberNotes();
        if (!command.getQueryOnly()) {
            String text = command.getNote().strip();
            if (!validNote(text)) {
                return note(
                        SetGuildMemberNoteOutcome.SET_GUILD_MEMBER_NOTE_OUTCOME_INVALID_TEXT, notes.notesOf(guildId));
            }
            Optional<Character> member =
                    gateway.characterFacade().findByName(command.getMemberName().strip());
            if (member.isEmpty() || !notes.setNote(guildId, member.get().id(), text)) {
                return note(SetGuildMemberNoteOutcome.SET_GUILD_MEMBER_NOTE_OUTCOME_NOT_MEMBER, notes.notesOf(guildId));
            }
        }
        return note(SetGuildMemberNoteOutcome.SET_GUILD_MEMBER_NOTE_OUTCOME_OK, notes.notesOf(guildId));
    }

    /** ≤ 100 ký tự, không ký tự điều khiển, không từ cấm. Tĩnh để test. */
    static boolean validNote(String text) {
        return text.length() <= GuildMemberNoteFacade.MAX_LENGTH
                && text.chars().noneMatch(java.lang.Character::isISOControl)
                && !ProfanityFilter.containsBannedWord(text);
    }

    private SetGuildMemberNoteResponse note(SetGuildMemberNoteOutcome outcome, Map<Long, String> notes) {
        SetGuildMemberNoteResponse.Builder response =
                SetGuildMemberNoteResponse.newBuilder().setOutcome(outcome);
        notes.forEach((memberId, text) -> response.addNotes(GuildMemberNote.newBuilder()
                .setName(gateway.characterFacade()
                        .findById(memberId)
                        .map(Character::name)
                        .orElse("?"))
                .setNote(text)));
        return response.build();
    }

    /**
     * ADR-320 — nhập mã giới thiệu (= tên nhân vật người giới thiệu). Chỉ khi mình dưới cấp {@value
     * ReferralFacade#REWARD_LEVEL}, chưa từng nhập, không tự giới thiệu, không cùng tài khoản. Tên rỗng ⇒ trả mã của
     * chính mình.
     */
    EnterReferralResponse enterReferral(long characterId, EnterReferralCommand command) {
        Character me = gateway.characterFacade().findById(characterId).orElseThrow();
        EnterReferralResponse.Builder response =
                EnterReferralResponse.newBuilder().setCode(me.name()).setRewardLevel(ReferralFacade.REWARD_LEVEL);
        String name = command.getReferrerName().strip();
        if (name.isEmpty()) {
            return response.setOutcome(EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_MY_CODE)
                    .build();
        }
        Optional<Character> referrer = gateway.characterFacade().findByName(name);
        return response.setOutcome(referralOutcome(me, referrer)).build();
    }

    private EnterReferralOutcome referralOutcome(Character me, Optional<Character> referrer) {
        if (referrer.isEmpty()) {
            return EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_NOT_FOUND;
        }
        if (referrer.get().id() == me.id()) {
            return EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_SELF;
        }
        if (referrer.get().accountId() == me.accountId()) {
            return EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_SAME_ACCOUNT;
        }
        if (me.level() >= ReferralFacade.REWARD_LEVEL) {
            return EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_LEVEL_TOO_HIGH;
        }
        return facades().referral().register(me.id(), referrer.get().id())
                ? EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_OK
                : EnterReferralOutcome.ENTER_REFERRAL_OUTCOME_ALREADY_REFERRED;
    }

    /** ADR-322 — lịch sử đột phá của mình, cũ trước. */
    GetBreakthroughHistoryResponse breakthroughHistory(long characterId) {
        GetBreakthroughHistoryResponse.Builder response = GetBreakthroughHistoryResponse.newBuilder();
        for (BreakthroughLogPort.Entry entry : facades().breakthroughs().history(characterId)) {
            response.addEntries(BreakthroughEntry.newBuilder()
                    .setRealm(entry.realm())
                    .setLevel(entry.level())
                    .setAtEpochSeconds(entry.at().getEpochSecond()));
        }
        return response.build();
    }

    /**
     * Lên cấp (Virtual Thread): ADR-322 ghi lịch sử nếu đây là cấp mở đầu một cảnh giới; ADR-320 tới cấp
     * {@value ReferralFacade#REWARD_LEVEL} thì chốt quà giới thiệu (một lần) và gửi thư cho cả hai.
     */
    // PMD AvoidCatchingGenericException: việc phụ của lên cấp — lỗi không được làm hỏng đường cộng EXP.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onLevelReached(long characterId, int level) {
        try {
            BreakthroughHooks.realmOpenedAt(level)
                    .ifPresent(realm -> facades().breakthroughs().record(characterId, realm, level));
            if (level == ReferralFacade.REWARD_LEVEL) {
                facades()
                        .referral()
                        .claimReward(characterId)
                        .ifPresent(referrer -> rewardReferral(characterId, referrer));
            }
        } catch (RuntimeException e) {
            LOG.warn("Việc phụ lô 49 khi lên cấp lỗi: characterId={}, level={}", characterId, level, e);
        }
    }

    private void rewardReferral(long referredId, long referrerId) {
        MailFacade mail = gateway.contentFacades().socialFacades().mailFacade();
        String body = "Người được giới thiệu đã tới cấp " + ReferralFacade.REWARD_LEVEL
                + ". / Your referral reached level " + ReferralFacade.REWARD_LEVEL + ".";
        for (long recipient : List.of(referredId, referrerId)) {
            mail.sendMail(
                    new MailFacade.SendMailRequest(null, recipient, REFERRAL_SUBJECT, body, REFERRAL_GOLD, List.of()),
                    referralKey(referredId, recipient));
        }
    }

    /** Khoá chống trùng thư quà giới thiệu. Tĩnh để test. */
    static String referralKey(long referredId, long recipientId) {
        return UUID.nameUUIDFromBytes(("referral:" + referredId + ":" + recipientId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    /** ADR-321 — cường hoá thành công lên ≥ +10 thì báo toàn server (nhãn món do client dựng từ dữ liệu server). */
    // PMD AvoidCatchingGenericException: việc phụ sau khi đã trả kết quả cường hoá.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void announceEnhance(long characterId, long itemId) {
        try {
            gateway.inventoryFacade()
                    .listInventory(characterId)
                    .inventory()
                    .findItem(itemId)
                    .filter(item -> item.enhanceLevel() >= ANNOUNCE_ENHANCE_LEVEL)
                    .ifPresent(item -> gateway.characterFacade()
                            .findById(characterId)
                            .ifPresent(character -> registry.broadcastToAll(
                                    Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, enhanceNotice(character.name(), item))));
        } catch (RuntimeException e) {
            LOG.warn("Không báo được cường hoá cao: characterId={}", characterId, e);
        }
    }

    /** Dòng báo cường hoá cao, kèm món (client ghi nhãn tên món). Tĩnh để test. */
    static byte[] enhanceNotice(String name, ItemStack item) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(name + " cường hoá thành công lên +" + item.enhanceLevel() + "! / " + name
                        + " enhanced an item to +" + item.enhanceLevel() + "!")
                .setLinkedItem(LinkedItemView.newBuilder()
                        .setItemTemplateId(item.itemTemplateId())
                        .setEnhanceLevel(item.enhanceLevel())
                        .setQuantity(item.quantity()))
                .build()
                .toByteArray();
    }

    private GuildFacade guilds() {
        return gateway.contentFacades().socialFacades().guildFacade();
    }

    private Lot49Facades facades() {
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
                .lot49();
    }
}

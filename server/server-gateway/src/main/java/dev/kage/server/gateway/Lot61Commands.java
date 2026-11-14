package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.NewPlayersFacade;
import dev.kage.character.domain.Character;
import dev.kage.guild.api.GuildFacade;
import dev.kage.protocol.AbsentFriend;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetAbsentFriendsResponse;
import dev.kage.protocol.GetNewPlayersResponse;
import dev.kage.protocol.Opcode;

/** Lớp lệnh lô 61 (ADR-415…) — MỘT thành phần của {@link Lot60Commands}. Gọi trên Virtual Thread. */
record Lot61Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-416 — vắng ít nhất ngần này ngày mới tính là "lâu vắng". */
    static final int ABSENT_DAYS = 7;

    /** ADR-418 — hộp thư đầy tới tỷ lệ này (phần trăm) thì nhắc. */
    static final int MAILBOX_WARN_PERCENT = 80;

    private static final int PERCENT = 100;
    private static final Logger LOG = LoggerFactory.getLogger(Lot61Commands.class);

    /**
     * Vào world: tóm tắt bạn bè / đồng môn đang online (ADR-415) và nhắc hộp thư sắp đầy (ADR-418). Việc phụ — lỗi dừng
     * ở đây, không làm hỏng vào world.
     */
    // PMD AvoidCatchingGenericException: việc phụ của lần vào world.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onEnteredWorld(long characterId) {
        try {
            int friends = onlineFriends(characterId);
            int guildmates = onlineGuildmates(characterId);
            if (friends + guildmates > 0) {
                notice(
                        characterId,
                        "Đang online: " + friends + " bạn bè, " + guildmates + " đồng môn / Online: " + friends
                                + " friends, " + guildmates + " guildmates");
            }
            int mails = gateway.contentFacades()
                    .socialFacades()
                    .mailFacade()
                    .listMail(characterId)
                    .size();
            int capacity = dev.kage.mail.application.SendMailUseCase.MAILBOX_CAPACITY;
            if (nearlyFull(mails, capacity)) {
                notice(
                        characterId,
                        "Hộp thư sắp đầy (" + mails + "/" + capacity + ") — hãy dọn bớt thư. / Mailbox "
                                + "almost full (" + mails + "/" + capacity + ") — please clear some mail.");
            }
        } catch (RuntimeException e) {
            LOG.warn("Không gửi được tóm tắt vào world: characterId={}", characterId, e);
        }
    }

    /** ADR-418 — đã tới ngưỡng nhắc chưa. Tĩnh để test. */
    static boolean nearlyFull(int mails, int capacity) {
        return capacity > 0 && mails * PERCENT >= capacity * MAILBOX_WARN_PERCENT;
    }

    /** ADR-416 — bạn bè đã vắng ít nhất {@value #ABSENT_DAYS} ngày, vắng lâu nhất trước. */
    GetAbsentFriendsResponse absentFriends(long characterId) {
        List<Long> friends =
                gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId);
        Map<Long, Instant> lastOnline =
                gateway.progression().expansion().lot33().lastOnline().lastOnlineOf(friends);
        Instant now = Instant.now();
        List<AbsentFriend> absent = new ArrayList<>();
        for (long friend : friends) {
            Instant left = lastOnline.get(friend);
            if (left == null || registry.isOnline(friend)) {
                continue;
            }
            int days = (int) Duration.between(left, now).toDays();
            if (days >= ABSENT_DAYS) {
                gateway.characterFacade()
                        .findById(friend)
                        .map(Character::name)
                        .ifPresent(name -> absent.add(AbsentFriend.newBuilder()
                                .setName(name)
                                .setDays(days)
                                .build()));
            }
        }
        absent.sort(Comparator.comparingInt(AbsentFriend::getDays).reversed());
        return GetAbsentFriendsResponse.newBuilder().addAllEntries(absent).build();
    }

    /** ADR-417 — số nhân vật tạo hôm nay + vài tên mới nhất. */
    GetNewPlayersResponse newPlayers() {
        NewPlayersFacade.Today today = facades().newPlayers().today(Instant.now());
        return GetNewPlayersResponse.newBuilder()
                .setCount(today.count())
                .addAllNewest(today.newest())
                .build();
    }

    private int onlineFriends(long characterId) {
        Lot57Commands lot57 = new Lot57Commands(gateway, registry);
        int online = 0;
        for (long friend :
                gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId)) {
            if (lot57.shownOnline(friend)) {
                online++;
            }
        }
        return online;
    }

    private int onlineGuildmates(long characterId) {
        int online = 0;
        for (GuildFacade.MemberView member :
                gateway.contentFacades().socialFacades().guildFacade().listMembers(characterId)) {
            if (member.characterId() != characterId && registry.isOnline(member.characterId())) {
                online++;
            }
        }
        return online;
    }

    private void notice(long characterId, String body) {
        registry.sendToCharacterIfOnline(
                characterId,
                Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                0,
                ChatMessageEvent.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                        .setSenderName("")
                        .setBody(body)
                        .build()
                        .toByteArray());
    }

    private Lot61Facades facades() {
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
                .lot61();
    }
}

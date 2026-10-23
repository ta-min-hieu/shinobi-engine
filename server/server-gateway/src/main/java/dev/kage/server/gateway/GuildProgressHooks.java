package dev.kage.server.gateway;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.guild.api.GuildProgressionFacade;
import dev.kage.guild.domain.GuildWeeklyQuest;
import dev.kage.guild.domain.WeeklyCompletion;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.GuildChangeReason;
import dev.kage.protocol.GuildChangedEvent;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;

/**
 * Nối lượt hạ quái vào nhiệm vụ bang tuần (ADR-069) và báo bang lên cấp (ADR-068). Mọi hàm chạm DB: chỉ gọi
 * trên Virtual Thread. Việc PHỤ: lỗi dừng ở đây (log WARN), không làm hỏng phần thưởng chính của lượt hạ.
 */
final class GuildProgressHooks {

    /** Gửi một gói cho một nhân vật nếu đang online. */
    @FunctionalInterface
    interface Sink {
        void send(long characterId, int opcode, byte[] payload);
    }

    /** Gửi một thư hệ thống — {@code MailFacade#sendMail}. */
    @FunctionalInterface
    interface Mailer {
        MailFacade.SendOutcome send(MailFacade.SendMailRequest request);
    }

    private static final Logger LOG = LoggerFactory.getLogger(GuildProgressHooks.class);

    private static final String SUBJECT = "Nhiệm vụ bang tuần / Weekly guild quest";
    private static final String BODY =
            "Bang đã hạ đủ yêu thú tuần này — phần thưởng của ngươi. / Your guild finished this week's hunt.";

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng lý do AchievementHooks.
    private volatile GuildProgressionFacade progression;
    private volatile Mailer mail;
    private volatile Sink sink;

    private GuildProgressHooks(GuildProgressionFacade progression, Mailer mail, Sink sink) {
        this.progression = progression;
        this.mail = mail;
        this.sink = sink;
    }

    /** Chưa nối: không bang nào, không gửi gì. */
    static GuildProgressHooks unbound() {
        return new GuildProgressHooks(
                GuildProgressionFacade.DISABLED,
                request -> {
                    throw new IllegalStateException("GuildProgressHooks chưa bind");
                },
                (id, opcode, payload) -> {});
    }

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(GuildProgressionFacade realProgression, MailFacade realMail, Sink realSink) {
        bind(realProgression, realMail::sendMail, realSink);
    }

    /** Như trên với một {@link Mailer} bất kỳ — test thay thư thật bằng bản ném lỗi. */
    void bind(GuildProgressionFacade realProgression, Mailer realMail, Sink realSink) {
        this.progression = realProgression;
        this.mail = realMail;
        this.sink = realSink;
    }

    // PMD AvoidCatchingGenericException: việc phụ — mọi lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onMonsterKilled(long characterId) {
        try {
            progression.recordKill(characterId).ifPresent(this::reward);
        } catch (RuntimeException e) {
            LOG.warn("Ghi nhiệm vụ bang tuần lỗi: characterId={}", characterId, e);
        }
    }

    /** Bang vừa lên cấp (góp linh thạch) — báo mọi thành viên đang online. */
    void announceLevelUp(long guildId) {
        notifyAll(progression.contributionsOf(guildId).keySet(), GuildChangeReason.GUILD_CHANGE_REASON_LEVEL_UP);
    }

    private void reward(WeeklyCompletion completion) {
        for (long member : completion.memberIds()) {
            rewardMember(completion.guildId(), member);
        }
        notifyAll(completion.memberIds(), GuildChangeReason.GUILD_CHANGE_REASON_WEEKLY_QUEST_COMPLETED);
        if (completion.leveledUp()) {
            notifyAll(completion.memberIds(), GuildChangeReason.GUILD_CHANGE_REASON_LEVEL_UP);
        }
    }

    /**
     * Thư của MỘT thành viên. Lỗi dừng ở đây (docs/README.md dòng 81, phương án (b)): tuần đã chốt trong
     * transaction riêng, một thư ném lỗi mà bỏ dở cả vòng thì những người sau mất thưởng vĩnh viễn. Vẫn mất
     * thưởng nếu server sập giữa vòng — rủi ro đó chờ outbox (phương án (a)).
     */
    // PMD AvoidCatchingGenericException: lỗi của một thư không được chặn thư của người khác.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void rewardMember(long guildId, long member) {
        try {
            MailFacade.SendOutcome outcome = mail.send(new MailFacade.SendMailRequest(
                    null, member, SUBJECT, BODY, GuildWeeklyQuest.REWARD_GOLD_PER_MEMBER, List.of()));
            if (outcome.result() == MailFacade.SendResult.OK) {
                sink.send(
                        member,
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        NewMailEvent.newBuilder()
                                .setMailId(outcome.mailId())
                                .build()
                                .toByteArray());
            } else {
                LOG.warn(
                        "Thư thưởng bang tuần không gửi được: guildId={}, characterId={}, result={}",
                        guildId,
                        member,
                        outcome.result());
            }
        } catch (RuntimeException e) {
            LOG.warn("Thư thưởng bang tuần lỗi: guildId={}, characterId={}", guildId, member, e);
        }
    }

    private void notifyAll(Collection<Long> members, GuildChangeReason reason) {
        byte[] payload =
                GuildChangedEvent.newBuilder().setReason(reason).build().toByteArray();
        for (long member : members) {
            sink.send(member, Opcode.OPCODE_GUILD_CHANGED_EVENT_VALUE, payload);
        }
    }
}

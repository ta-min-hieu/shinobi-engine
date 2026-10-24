package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.MentorshipFacade;
import dev.kage.friend.application.MentorshipUseCase;
import dev.kage.friend.domain.MentorshipOutcome;
import dev.kage.protocol.AcceptDiscipleResponse;
import dev.kage.protocol.LeaveMentorshipResponse;
import dev.kage.protocol.MentorRequestReceivedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestMentorResponse;

/**
 * Sư đồ (ADR-093) — tra tên, đọc cấp, gọi feature-friend, báo người kia, và gửi THƯ thưởng mốc cấp cho cả hai (gọi từ
 * {@link CharacterLevelSync} mỗi cú lên cấp). Chạy trên Virtual Thread (chạm DB).
 */
final class MentorshipCommands {

    private static final Logger LOG = LoggerFactory.getLogger(MentorshipCommands.class);

    private static final Map<MentorshipOutcome, dev.kage.protocol.MentorshipOutcome> OUTCOMES =
            new EnumMap<>(MentorshipOutcome.class);

    static {
        for (MentorshipOutcome outcome : MentorshipOutcome.values()) {
            OUTCOMES.put(outcome, dev.kage.protocol.MentorshipOutcome.valueOf("MENTORSHIP_OUTCOME_" + outcome.name()));
        }
    }

    private final MentorshipFacade mentorship;
    private final CharacterFacade characters;
    private final BlockCommands blocks;
    private final PlayerNotices notices;

    MentorshipCommands(
            MentorshipFacade mentorship, CharacterFacade characters, BlockCommands blocks, PlayerNotices notices) {
        this.mentorship = mentorship;
        this.characters = characters;
        this.blocks = blocks;
        this.notices = notices;
    }

    RequestMentorResponse request(long discipleId, String mentorName) {
        Optional<Character> mentor = characters.findByName(mentorName.strip());
        Optional<Character> disciple = characters.findById(discipleId);
        if (mentor.isEmpty() || disciple.isEmpty()) {
            return requestResponse(dev.kage.protocol.MentorshipOutcome.MENTORSHIP_OUTCOME_TARGET_NOT_FOUND);
        }
        blocks.requireNotBlocked(discipleId, mentor.get().id());
        if (!notices.isOnline(mentor.get().id())) {
            return requestResponse(dev.kage.protocol.MentorshipOutcome.MENTORSHIP_OUTCOME_TARGET_OFFLINE);
        }
        MentorshipOutcome outcome = mentorship.request(
                discipleId,
                disciple.get().level(),
                mentor.get().id(),
                mentor.get().level());
        if (outcome == MentorshipOutcome.OK) {
            notices.event(
                    mentor.get().id(),
                    Opcode.OPCODE_MENTOR_REQUEST_RECEIVED_EVENT_VALUE,
                    MentorRequestReceivedEvent.newBuilder()
                            .setDiscipleId(discipleId)
                            .setDiscipleName(disciple.get().name())
                            .setDiscipleLevel(disciple.get().level())
                            .build()
                            .toByteArray());
        }
        return requestResponse(OUTCOMES.get(outcome));
    }

    AcceptDiscipleResponse accept(long mentorId, String discipleName) {
        Optional<Character> disciple = characters.findByName(discipleName.strip());
        Optional<Character> mentor = characters.findById(mentorId);
        if (disciple.isEmpty() || mentor.isEmpty()) {
            return AcceptDiscipleResponse.newBuilder()
                    .setOutcome(dev.kage.protocol.MentorshipOutcome.MENTORSHIP_OUTCOME_TARGET_NOT_FOUND)
                    .build();
        }
        MentorshipOutcome outcome = mentorship.accept(
                mentorId,
                mentor.get().level(),
                disciple.get().id(),
                disciple.get().level());
        if (outcome == MentorshipOutcome.OK) {
            notices.systemLine(
                    disciple.get().id(),
                    "%s đã nhận ngươi làm đồ đệ. / %s accepted you as a disciple."
                            .formatted(mentor.get().name(), mentor.get().name()));
        }
        return AcceptDiscipleResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .build();
    }

    LeaveMentorshipResponse leave(long characterId, String otherName) {
        Optional<Character> other = characters.findByName(otherName.strip());
        MentorshipOutcome outcome = other.isEmpty()
                ? MentorshipOutcome.NOT_RELATED
                : mentorship.leave(characterId, other.get().id());
        return LeaveMentorshipResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .build();
    }

    /**
     * Gọi mỗi cú lên cấp ({@link CharacterLevelSync}) — thưởng mốc qua thư cho cả hai. Việc PHỤ: lỗi dừng ở đây, không
     * làm hỏng cú lên cấp.
     */
    // PMD AvoidCatchingGenericException: việc phụ chạy theo cú lên cấp — mọi lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onLevelReached(long characterId, int level) {
        try {
            List<MentorshipUseCase.MilestoneReward> rewards = mentorship.onLevelReached(characterId, level);
            for (MentorshipUseCase.MilestoneReward reward : rewards) {
                reward(characterId, reward);
            }
        } catch (RuntimeException e) {
            LOG.warn("Thưởng sư đồ lỗi — phần thưởng này MẤT: characterId={}, level={}", characterId, level, e);
        }
    }

    private void reward(long discipleId, MentorshipUseCase.MilestoneReward reward) {
        String discipleName =
                characters.findById(discipleId).map(Character::name).orElse("#" + discipleId);
        String body = reward.graduated()
                ? "%s đạt cấp %d — xuất sư. / %s reached level %d and graduated."
                : "%s đạt cấp %d. / %s reached level %d.";
        String filled = body.formatted(discipleName, reward.milestone(), discipleName, reward.milestone());
        notices.mail(discipleId, new PlayerNotices.SystemMail("Sư đồ / Mentorship", filled, reward.discipleGold()));
        notices.mail(
                reward.mentorId(), new PlayerNotices.SystemMail("Sư đồ / Mentorship", filled, reward.mentorGold()));
    }

    private static RequestMentorResponse requestResponse(dev.kage.protocol.MentorshipOutcome outcome) {
        return RequestMentorResponse.newBuilder().setOutcome(outcome).build();
    }
}

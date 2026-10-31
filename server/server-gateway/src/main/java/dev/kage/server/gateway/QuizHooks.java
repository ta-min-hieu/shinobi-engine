package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.chat.api.SectQuiz;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuizQuestionEvent;
import dev.kage.protocol.QuizRevealEvent;

/**
 * Hỏi đáp tông môn (ADR-122) — nối {@link SectQuiz} (máy trạng thái RAM) với mạng: nhịp đồng hồ trong
 * {@link ExpansionJobs} mở/chốt câu, câu chat kênh thế giới đi qua {@link #onWorldChat}; người đúng đầu tiên nhận thư
 * linh thạch với khoá chống trùng tất định theo phiên + câu. Nối sau khi DB đã dựng.
 */
final class QuizHooks {

    private static final Logger LOG = LoggerFactory.getLogger(QuizHooks.class);
    private static final long TICK_SECONDS = 2L;
    private static final String SUBJECT = "Hỏi đáp tông môn / Sect quiz";
    private static final String BODY = "Đạo hữu trả lời đúng đầu tiên. / You answered first and correctly.";

    /** Thứ việc này cần. */
    record Deps(SectQuiz quiz, MailFacade mail, ConnectedCharacterRegistry registry, Clock clock) {}

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Nhịp đồng hồ cho {@link ExpansionJobs}. */
    ExpansionJobs.Job job() {
        return new ExpansionJobs.Job("hỏi đáp tông môn", TICK_SECONDS, this::tick);
    }

    private void tick() {
        deps.ifPresent(d -> {
            List<SectQuiz.Signal> signals = d.quiz().tick(d.clock().instant());
            for (SectQuiz.Signal signal : signals) {
                switch (signal) {
                    case SectQuiz.Signal.Ask ask -> broadcastAsk(d, ask);
                    case SectQuiz.Signal.Reveal reveal -> broadcastReveal(d, reveal);
                }
            }
        });
    }

    // PMD AvoidCatchingGenericException: việc phụ của đường chat — lỗi dừng ở đây, tin nhắn đã phát xong.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onWorldChat(long characterId, String name, String body) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        Deps d = current.get();
        try {
            d.quiz().onWorldChat(characterId, name, body).ifPresent(reveal -> {
                reveal.winner().ifPresent(winner -> reward(d, winner));
                broadcastReveal(d, reveal);
            });
        } catch (RuntimeException e) {
            LOG.warn("Chấm hỏi đáp lỗi: characterId={}", characterId, e);
        }
    }

    private static void reward(Deps d, SectQuiz.Winner winner) {
        MailFacade.SendOutcome outcome = d.mail()
                .sendMail(
                        new MailFacade.SendMailRequest(
                                null, winner.characterId(), SUBJECT, BODY, winner.rewardGold(), List.of()),
                        UUID.nameUUIDFromBytes(winner.rewardKey().getBytes(StandardCharsets.UTF_8))
                                .toString());
        if (outcome.result() == MailFacade.SendResult.OK) {
            d.registry()
                    .sendToCharacterIfOnline(
                            winner.characterId(),
                            Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                            0,
                            NewMailEvent.newBuilder()
                                    .setMailId(outcome.mailId())
                                    .build()
                                    .toByteArray());
        } else if (outcome.result() != MailFacade.SendResult.DUPLICATE_REQUEST) {
            LOG.warn(
                    "Không gửi được thư thưởng hỏi đáp: characterId={}, result={}",
                    winner.characterId(),
                    outcome.result());
        }
    }

    private static void broadcastAsk(Deps d, SectQuiz.Signal.Ask ask) {
        d.registry()
                .broadcastToAll(
                        Opcode.OPCODE_QUIZ_QUESTION_EVENT_VALUE,
                        QuizQuestionEvent.newBuilder()
                                .setIndex(ask.index())
                                .setTotal(ask.total())
                                .setQuestionKey(ask.questionKey())
                                .setEndsAtEpochMs(ask.endsAt().toEpochMilli())
                                .build()
                                .toByteArray());
    }

    private static void broadcastReveal(Deps d, SectQuiz.Signal.Reveal reveal) {
        QuizRevealEvent.Builder event =
                QuizRevealEvent.newBuilder().setIndex(reveal.index()).setAnswerKey(reveal.answerKey());
        reveal.winner().ifPresent(winner -> event.setWinnerName(winner.name()).setRewardGold(winner.rewardGold()));
        d.registry()
                .broadcastToAll(
                        Opcode.OPCODE_QUIZ_REVEAL_EVENT_VALUE, event.build().toByteArray());
    }
}

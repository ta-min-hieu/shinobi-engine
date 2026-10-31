package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuizQuestionEvent;
import dev.kage.protocol.QuizRevealEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hỏi đáp tông môn (ADR-122) — không có cửa sổ: câu hỏi và đáp án hiện thành dòng hệ thống trong khung chat, người
 * chơi trả lời bằng cách gõ vào kênh THẾ GIỚI như chat thường. Server chấm, client chỉ đọc lại.
 */
final class QuizCoordinator implements WindowFlow {

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    QuizCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_QUIZ_QUESTION_EVENT_VALUE) {
            QuizQuestionEvent event = QuizQuestionEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "quiz.notice.question",
                    event.getIndex() + 1,
                    event.getTotal(),
                    KageI18n.textOrKey(i18n, event.getQuestionKey())));
            return true;
        }
        if (opcode == Opcode.OPCODE_QUIZ_REVEAL_EVENT_VALUE) {
            QuizRevealEvent event = QuizRevealEvent.parseFrom(frame.payload());
            String answer = KageI18n.textOrKey(i18n, event.getAnswerKey());
            chat.showSystemNotice(
                    event.getWinnerName().isEmpty()
                            ? KageI18n.formatOrKey(i18n, "quiz.notice.timeout", answer)
                            : KageI18n.formatOrKey(
                                    i18n, "quiz.notice.winner", event.getWinnerName(), answer, event.getRewardGold()));
            return true;
        }
        return false;
    }
}

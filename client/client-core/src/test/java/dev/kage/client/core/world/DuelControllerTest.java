package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptDuelOutcome;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.DeclineDuelOutcome;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.ExitDuelOutcome;
import dev.kage.protocol.GetPvpRatingResponse;

/**
 * {@link DuelController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code GuildControllerTest}.
 */
class DuelControllerTest {

    private static final long OPPONENT_ID = 77L;

    private static final long SELF_ID = 42L;

    private final DuelController controller = new DuelController();

    @Test
    @DisplayName("Mới vào: không trận, không lời thách đấu, chưa có điểm xếp hạng")
    void startsEmpty() {
        assertThat(controller.inDuel()).isFalse();
        assertThat(controller.opponent()).isEmpty();
        assertThat(controller.incomingChallenge()).isEmpty();
        assertThat(controller.candidate()).isEmpty();
        assertThat(controller.rating()).isEmpty();
        assertThat(controller.hasOutgoingChallenge()).isFalse();
    }

    @Test
    @DisplayName("Chưa nghe thấy tên thì click vào người đó KHÔNG chọn được (khoảng trống của giao thức)")
    void cannotSelectUnknownCharacter() {
        // CharacterSpawnedEvent không mang tên, mà ChallengeDuelCommand lại thách đấu theo TÊN —
        // đây chính là cái hố đó, và nó phải hiện ra thành một Optional rỗng chứ không phải một cái
        // tên bịa.
        assertThat(controller.selectCandidate(OPPONENT_ID)).isEmpty();
        assertThat(controller.candidate()).isEmpty();
    }

    @Test
    @DisplayName("Nghe thấy tên ở chỗ khác (chat/danh sách) rồi thì click chọn được")
    void selectsCharacterWithRememberedName() {
        controller.rememberName(OPPONENT_ID, "Hắc Long");

        Optional<DuelController.Player> picked = controller.selectCandidate(OPPONENT_ID);

        assertThat(picked).isPresent();
        assertThat(picked.get().characterId()).isEqualTo(OPPONENT_ID);
        assertThat(picked.get().name()).isEqualTo("Hắc Long");
        assertThat(controller.candidate()).contains(picked.get());
    }

    @Test
    @DisplayName("Tên gửi lên server là tên THẬT, tên hiện ra là bản đã sanitize")
    void keepsRawNameForTheWireAndSanitizedNameForTheScreen() {
        // ★ (U+2605) không nằm trong FontFactory.VIETNAMESE_CHARACTERS — font UI im lặng nuốt nó.
        controller.rememberName(OPPONENT_ID, "Kage★");

        DuelController.Player picked = controller.selectCandidate(OPPONENT_ID).orElseThrow();

        assertThat(picked.name()).isEqualTo("Kage★");
        assertThat(picked.displayName()).isEqualTo("Kage?");
    }

    @Test
    @DisplayName("Sổ tên có TRẦN — người cũ nhất bị bỏ trước, người vừa nhắc lại thì ở lại")
    void evictsOldestRememberedName() {
        controller.rememberName(1L, "Cu Nhat");
        for (long id = 2L; id <= DuelController.MAX_REMEMBERED_NAMES; id++) {
            controller.rememberName(id, "Nguoi" + id);
        }
        controller.rememberName(1L, "Cu Nhat"); // nhắc lại -> hết cũ nhất
        controller.rememberName(999L, "Nguoi Moi"); // tràn trần -> bỏ id 2

        assertThat(controller.rememberedNameCount()).isEqualTo(DuelController.MAX_REMEMBERED_NAMES);
        assertThat(controller.selectCandidate(1L)).isPresent();
        assertThat(controller.selectCandidate(2L)).isEmpty();
        assertThat(controller.selectCandidate(999L)).isPresent();
    }

    @Test
    @DisplayName("Chọn hụt một người lạ KHÔNG xoá mất người đã chọn trước đó")
    void keepsPreviousCandidateWhenPickingAnUnknownCharacter() {
        controller.rememberName(OPPONENT_ID, "Hắc Long");
        controller.selectCandidate(OPPONENT_ID);

        assertThat(controller.selectCandidate(12345L)).isEmpty();

        assertThat(controller.candidate().orElseThrow().characterId()).isEqualTo(OPPONENT_ID);
    }

    @Test
    @DisplayName("DuelChallengeReceivedEvent nhớ luôn tên người thách đấu")
    void remembersChallengerName() {
        DuelController.Player challenger =
                controller.onChallengeReceived(challengeReceived(5L, OPPONENT_ID, "Bạch Hổ"));

        assertThat(challenger.name()).isEqualTo("Bạch Hổ");
        assertThat(controller.incomingChallenge().orElseThrow().challengeId()).isEqualTo(5L);
        assertThat(controller.selectCandidate(OPPONENT_ID)).isPresent();
    }

    @Test
    @DisplayName("Chỉ giữ lời thách đấu MỚI NHẤT (arena.proto không cho hỏi lại danh sách)")
    void keepsOnlyTheLatestIncomingChallenge() {
        controller.onChallengeReceived(challengeReceived(5L, OPPONENT_ID, "Bạch Hổ"));
        controller.onChallengeReceived(challengeReceived(6L, 88L, "Thanh Xà"));

        assertThat(controller.incomingChallenge().orElseThrow().challengeId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("Server trả lời lệnh Nhận/Từ chối thì lời thách đấu hết treo")
    void clearsIncomingChallengeOnceAnswered() {
        controller.onChallengeReceived(challengeReceived(5L, OPPONENT_ID, "Bạch Hổ"));

        controller.onChallengeAnswered();

        assertThat(controller.incomingChallenge()).isEmpty();
    }

    @Test
    @DisplayName("Lời từ chối chỉ được báo khi đúng challenge_id mình đang treo")
    void reportsOnlyDeclinesForTheOutgoingChallenge() {
        controller.onChallengeSent(9L);

        assertThat(controller.onChallengeDeclined(8L)).isFalse();
        assertThat(controller.hasOutgoingChallenge()).isTrue();

        assertThat(controller.onChallengeDeclined(9L)).isTrue();
        assertThat(controller.hasOutgoingChallenge()).isFalse();
    }

    @Test
    @DisplayName("CHỈ DuelStartedEvent mới mở được trận — không response nào làm thay")
    void onlyDuelStartedEventOpensADuel() {
        controller.onChallengeReceived(challengeReceived(5L, OPPONENT_ID, "Bạch Hổ"));
        controller.onChallengeSent(9L);
        assertThat(controller.inDuel()).isFalse();

        DuelController.Player opponent = controller.onDuelStarted(duelStarted(OPPONENT_ID, "Bạch Hổ"));

        assertThat(controller.inDuel()).isTrue();
        assertThat(opponent.characterId()).isEqualTo(OPPONENT_ID);
        // Cả hai lời thách đấu đang treo đều vô nghĩa khi đã vào trận — xem javadoc onDuelStarted.
        assertThat(controller.incomingChallenge()).isEmpty();
        assertThat(controller.hasOutgoingChallenge()).isFalse();
    }

    @Test
    @DisplayName("Đối thủ là bên THUA thì mình thắng, và điểm mới là điểm server gửi cho người thắng")
    void readsWinFromServerNumbers() {
        controller.onDuelStarted(duelStarted(OPPONENT_ID, "Bạch Hổ"));

        DuelController.Result result = controller
                .onDuelResult(DuelResultEvent.newBuilder()
                        .setWinnerCharacterId(SELF_ID)
                        .setLoserCharacterId(OPPONENT_ID)
                        .setWinnerNewRating(1240)
                        .setLoserNewRating(960)
                        .build())
                .orElseThrow();

        assertThat(result.won()).isTrue();
        assertThat(result.newRating()).isEqualTo(1240);
        assertThat(result.opponentDisplayName()).isEqualTo("Bạch Hổ");
        assertThat(controller.inDuel()).isFalse();
    }

    @Test
    @DisplayName("Đối thủ là bên THẮNG thì mình thua, và điểm mới là điểm của người thua")
    void readsLossFromServerNumbers() {
        controller.onDuelStarted(duelStarted(OPPONENT_ID, "Bạch Hổ"));

        DuelController.Result result = controller
                .onDuelResult(DuelResultEvent.newBuilder()
                        .setWinnerCharacterId(OPPONENT_ID)
                        .setLoserCharacterId(SELF_ID)
                        .setWinnerNewRating(1240)
                        .setLoserNewRating(960)
                        .build())
                .orElseThrow();

        assertThat(result.won()).isFalse();
        assertThat(result.newRating()).isEqualTo(960);
        assertThat(controller.inDuel()).isFalse();
    }

    @Test
    @DisplayName("Không biết đối thủ là ai thì KHÔNG đoán bừa bên nào thắng")
    void refusesToGuessResultWithoutAKnownOpponent() {
        assertThat(controller.onDuelResult(DuelResultEvent.newBuilder()
                        .setWinnerCharacterId(SELF_ID)
                        .setLoserCharacterId(OPPONENT_ID)
                        .build()))
                .isEmpty();
    }

    @Test
    @DisplayName("Điểm xếp hạng lấy nguyên xi từ GetPvpRatingResponse, client không tự tính gì")
    void adoptsServerRating() {
        controller.onRating(GetPvpRatingResponse.newBuilder()
                .setRating(1187)
                .setWins(9)
                .setLosses(4)
                .build());

        DuelController.Rating rating = controller.rating().orElseThrow();
        assertThat(rating.rating()).isEqualTo(1187);
        assertThat(rating.wins()).isEqualTo(9);
        assertThat(rating.losses()).isEqualTo(4);
    }

    @Test
    @DisplayName("Ô nhập rỗng: không gửi, KHÔNG báo lỗi")
    void emptyInputSendsNothing() {
        assertThat(controller.composeTargetName("   ")).isInstanceOf(DuelController.ComposeResult.Nothing.class);
    }

    @Test
    @DisplayName("Tên quá ngắn/quá dài bị chặn tại client kèm lý do")
    void rejectsNamesOutsideServerLimits() {
        assertThat(controller.composeTargetName("K"))
                .isEqualTo(new DuelController.ComposeResult.Reject("duel.error.bad_name"));
        assertThat(controller.composeTargetName("K".repeat(DuelController.MAX_NAME_LENGTH + 1)))
                .isEqualTo(new DuelController.ComposeResult.Reject("duel.error.bad_name"));
    }

    @Test
    @DisplayName("Tên hợp lệ được cắt khoảng trắng thừa rồi gửi đi")
    void trimsAndSendsValidName() {
        assertThat(controller.composeTargetName("  Hắc Long  "))
                .isEqualTo(new DuelController.ComposeResult.Send("Hắc Long"));
    }

    @Test
    @DisplayName("Mọi outcome THẬT của 4 enum arena đều có khoá i18n riêng")
    void mapsEveryRealOutcomeToItsOwnMessageKey() {
        assertEveryRealOutcomeMapped(ChallengeDuelOutcome.values(), DuelController::challengeMessageKey);
        assertEveryRealOutcomeMapped(AcceptDuelOutcome.values(), DuelController::acceptMessageKey);
        assertEveryRealOutcomeMapped(DeclineDuelOutcome.values(), DuelController::declineMessageKey);
        assertEveryRealOutcomeMapped(ExitDuelOutcome.values(), DuelController::exitMessageKey);
    }

    @Test
    @DisplayName("UNSPECIFIED/UNRECOGNIZED KHÔNG có khoá — chỗ gọi nói thẳng là không hiểu")
    void leavesPlaceholderOutcomesUnmapped() {
        assertThat(DuelController.challengeMessageKey(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(DuelController.challengeMessageKey(ChallengeDuelOutcome.UNRECOGNIZED))
                .isEmpty();
        assertThat(DuelController.acceptMessageKey(AcceptDuelOutcome.UNRECOGNIZED))
                .isEmpty();
        assertThat(DuelController.declineMessageKey(DeclineDuelOutcome.UNRECOGNIZED))
                .isEmpty();
        assertThat(DuelController.exitMessageKey(ExitDuelOutcome.UNRECOGNIZED)).isEmpty();
    }

    @Test
    @DisplayName("clear() xoá sạch mọi thứ của một phiên chơi")
    void clearsEverything() {
        controller.rememberName(OPPONENT_ID, "Bạch Hổ");
        controller.selectCandidate(OPPONENT_ID);
        controller.onChallengeSent(9L);
        controller.onDuelStarted(duelStarted(OPPONENT_ID, "Bạch Hổ"));
        controller.onRating(GetPvpRatingResponse.newBuilder().setRating(1000).build());

        controller.clear();

        assertThat(controller.inDuel()).isFalse();
        assertThat(controller.candidate()).isEmpty();
        assertThat(controller.rating()).isEmpty();
        assertThat(controller.hasOutgoingChallenge()).isFalse();
        assertThat(controller.rememberedNameCount()).isZero();
    }

    /**
     * Mọi giá trị enum TRỪ hai giá trị giữ chỗ ({@code UNSPECIFIED} = 0 do protobuf bắt buộc, và
     * {@code UNRECOGNIZED} = server mới hơn client) đều phải tra ra một khoá i18n riêng — không giá
     * trị nào được rơi vào cùng một câu "không rõ lỗi".
     */
    private static <T extends Enum<T>> void assertEveryRealOutcomeMapped(
            T[] values, java.util.function.Function<T, Optional<String>> lookup) {
        for (T outcome : values) {
            if (outcome.name().endsWith("_UNSPECIFIED") || "UNRECOGNIZED".equals(outcome.name())) {
                continue;
            }
            assertThat(lookup.apply(outcome))
                    .as("outcome %s chưa có khoá i18n riêng", outcome.name())
                    .isPresent();
        }
    }

    private static DuelChallengeReceivedEvent challengeReceived(long challengeId, long challengerId, String name) {
        return DuelChallengeReceivedEvent.newBuilder()
                .setChallengeId(challengeId)
                .setChallengerCharacterId(challengerId)
                .setChallengerName(name)
                .build();
    }

    private static DuelStartedEvent duelStarted(long opponentId, String name) {
        return DuelStartedEvent.newBuilder()
                .setOpponentCharacterId(opponentId)
                .setOpponentName(name)
                .build();
    }
}

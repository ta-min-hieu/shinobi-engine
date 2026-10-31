package dev.kage.server.gateway;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RegisterTournamentResponse;
import dev.kage.protocol.TournamentNoticeEvent;
import dev.kage.protocol.TournamentNoticeKind;
import dev.kage.protocol.TournamentRegisterOutcome;
import dev.kage.pvp.api.TournamentFacade;
import dev.kage.pvp.application.TournamentDirector;
import dev.kage.pvp.domain.TournamentPort;
import dev.kage.pvp.domain.TournamentRules;

/**
 * Luận võ đại hội (ADR-125) — nối {@link TournamentFacade} với World đấu trường: nhịp {@value #TICK_SECONDS} giây trong
 * {@link ExpansionJobs} điều khiển giải, trận đấu trường chốt thì {@link #onDuelSettled} ghi kết quả, lệnh đăng ký
 * {@link #register}. Trận giải mở bằng CHÍNH đường thách đấu + chấp nhận của ADR-051 (server làm thay hai người), nên
 * luật trận (thua khi rớt mạng, không hồi máu, Elo) giữ nguyên. Nối sau khi DB đã dựng.
 */
final class TournamentHooks {

    private static final Logger LOG = LoggerFactory.getLogger(TournamentHooks.class);
    private static final long TICK_SECONDS = 15L;
    private static final String SUBJECT = "Luận võ đại hội / Grand tournament";
    private static final String BODY = "Chúc mừng tân vô địch! / Congratulations, champion!";

    /** Thứ việc này cần. */
    record Deps(
            TournamentFacade facade,
            ArenaInstanceRegistry arena,
            ConnectedCharacterRegistry registry,
            PlayerNotices notices,
            CharacterFacade characters,
            Clock clock) {}

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Nhịp điều khiển giải cho {@link ExpansionJobs}. */
    ExpansionJobs.Job job() {
        return new ExpansionJobs.Job(
                "luận võ đại hội",
                TICK_SECONDS,
                () -> deps.ifPresent(d -> d.facade().tick(d.clock().instant(), arena(d), announcer(d))));
    }

    // PMD AvoidCatchingGenericException: việc phụ của đường chốt trận — Elo/thông báo đã xong, lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onDuelSettled(long winnerId, long loserId) {
        deps.ifPresent(d -> {
            try {
                d.facade().onDuelSettled(winnerId, loserId);
            } catch (RuntimeException e) {
                LOG.warn("Ghi kết quả trận luận võ lỗi: {} thắng {}", winnerId, loserId, e);
            }
        });
    }

    RegisterTournamentResponse register(long characterId) {
        TournamentRegisterOutcome outcome = deps.map(d -> {
                    int level = d.characters()
                            .findById(characterId)
                            .map(Character::level)
                            .orElse(1);
                    return wire(
                            d.facade().register(characterId, level, d.clock().instant()));
                })
                .orElse(TournamentRegisterOutcome.TOURNAMENT_REGISTER_OUTCOME_CLOSED);
        return RegisterTournamentResponse.newBuilder().setOutcome(outcome).build();
    }

    private static TournamentDirector.Arena arena(Deps d) {
        return new TournamentDirector.Arena() {
            @Override
            public boolean online(long characterId) {
                return d.registry().isOnline(characterId);
            }

            @Override
            public boolean startDuel(long aId, long bId) {
                ArenaInstanceRegistry.ChallengeResult challenge = d.arena().challengeDuel(aId, bId);
                return challenge.outcome() == ArenaInstanceRegistry.ChallengeOutcome.OK
                        && d.arena().acceptDuel(bId, challenge.challengeId()).outcome()
                                == ArenaInstanceRegistry.AcceptOutcome.OK;
            }

            @Override
            public void forfeit(long characterId) {
                d.arena().forfeitDuel(characterId);
            }
        };
    }

    private static TournamentDirector.Announcer announcer(Deps d) {
        return new TournamentDirector.Announcer() {
            @Override
            public void matchAssigned(long characterId, long opponentId, int round) {
                d.registry()
                        .sendToCharacterIfOnline(
                                characterId,
                                Opcode.OPCODE_TOURNAMENT_NOTICE_EVENT_VALUE,
                                0,
                                notice(TournamentNoticeKind.TOURNAMENT_NOTICE_KIND_MATCH, round, nameOf(d, opponentId))
                                        .toByteArray());
            }

            @Override
            public void champion(long characterId) {
                d.notices()
                        .mail(
                                characterId,
                                new PlayerNotices.SystemMail(SUBJECT, BODY, TournamentRules.CHAMPION_GOLD, List.of()));
                d.registry().achievements.onTournamentWon(characterId);
                d.registry()
                        .broadcastToAll(
                                Opcode.OPCODE_TOURNAMENT_NOTICE_EVENT_VALUE,
                                notice(TournamentNoticeKind.TOURNAMENT_NOTICE_KIND_CHAMPION, 0, nameOf(d, characterId))
                                        .toByteArray());
            }
        };
    }

    private static String nameOf(Deps d, long characterId) {
        return characterId == 0L
                ? ""
                : d.characters().findById(characterId).map(Character::name).orElse("");
    }

    private static TournamentNoticeEvent notice(TournamentNoticeKind kind, int round, String name) {
        return TournamentNoticeEvent.newBuilder()
                .setKind(kind)
                .setRound(round)
                .setName(name)
                .build();
    }

    private static TournamentRegisterOutcome wire(TournamentPort.RegisterOutcome outcome) {
        return switch (outcome) {
            case OK -> TournamentRegisterOutcome.TOURNAMENT_REGISTER_OUTCOME_OK;
            case CLOSED -> TournamentRegisterOutcome.TOURNAMENT_REGISTER_OUTCOME_CLOSED;
            case ALREADY_REGISTERED -> TournamentRegisterOutcome.TOURNAMENT_REGISTER_OUTCOME_ALREADY_REGISTERED;
            case FULL -> TournamentRegisterOutcome.TOURNAMENT_REGISTER_OUTCOME_FULL;
            case LEVEL_TOO_LOW -> TournamentRegisterOutcome.TOURNAMENT_REGISTER_OUTCOME_LEVEL_TOO_LOW;
        };
    }
}

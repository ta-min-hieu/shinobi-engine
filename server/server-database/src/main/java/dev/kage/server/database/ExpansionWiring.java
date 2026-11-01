package dev.kage.server.database;

import java.time.LocalTime;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.chat.api.SectQuiz;
import dev.kage.pvp.api.TournamentFacade;
import dev.kage.pvp.application.TournamentDirector;
import dev.kage.pvp.domain.TournamentRules;
import dev.kage.server.database.chat.PostgresQuizAdapter;
import dev.kage.server.database.pvp.PostgresTournamentAdapter;
import dev.kage.server.database.world.PostgresArtifactAdapter;
import dev.kage.server.database.world.PostgresEncounterAdapter;
import dev.kage.server.database.world.PostgresSpiritFieldAdapter;
import dev.kage.server.database.world.PostgresTamingAdapter;
import dev.kage.world.api.EncounterFacade;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.api.TamingFacade;

/**
 * Dựng facade lô 23 phần 2–3 (ADR-119…126) — tách khỏi {@link ServerDatabaseBootstrap} vì lớp đó chạm trần độ phức tạp
 * PMD tính cho cả lớp. Lấy qua {@link ServerDatabaseBootstrap#expansion()}.
 */
public final class ExpansionWiring {

    private final DataSource dataSource;

    ExpansionWiring(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Linh điền (ADR-119). */
    public SpiritFieldFacade wireSpiritFieldFacade() {
        return new SpiritFieldFacade(new PostgresSpiritFieldAdapter(dataSource));
    }

    /** Kỳ ngộ (ADR-121). */
    public EncounterFacade wireEncounterFacade() {
        return new EncounterFacade(new PostgresEncounterAdapter(dataSource));
    }

    /** Hỏi đáp tông môn (ADR-122) — {@code starts} là giờ bắt đầu phiên theo giờ Việt Nam. */
    public SectQuiz wireSectQuiz(List<LocalTime> starts) {
        return new SectQuiz(new PostgresQuizAdapter(dataSource), starts, System.nanoTime());
    }

    /** Thu phục yêu thú (ADR-124). */
    public TamingFacade wireTamingFacade() {
        return new TamingFacade(new PostgresTamingAdapter(dataSource));
    }

    /** Động phủ (ADR-129). */
    public dev.kage.world.api.CaveFacade wireCaveFacade() {
        return new dev.kage.world.api.CaveFacade(new dev.kage.server.database.world.PostgresCaveAdapter(dataSource));
    }

    /** Sát khí (ADR-142). */
    public dev.kage.world.api.KillingIntentFacade wireKillingIntentFacade() {
        return new dev.kage.world.api.KillingIntentFacade(
                new dev.kage.server.database.world.PostgresKillingIntentAdapter(dataSource));
    }

    /** Pháp bảo (ADR-127). */
    public dev.kage.world.api.ArtifactFacade wireArtifactFacade() {
        return new dev.kage.world.api.ArtifactFacade(new PostgresArtifactAdapter(dataSource));
    }

    /** Thiên Kiếp (ADR-126). */
    public dev.kage.character.api.TribulationFacade wireTribulationFacade() {
        return new dev.kage.character.api.TribulationFacade(
                new dev.kage.server.database.character.PostgresTribulationAdapter(dataSource));
    }

    /** Luận võ đại hội (ADR-125) — {@code schedule} là ngày + giờ giải theo giờ Việt Nam. */
    public TournamentFacade wireTournamentFacade(TournamentRules.Schedule schedule) {
        return new TournamentFacade(
                new TournamentDirector(new PostgresTournamentAdapter(dataSource), schedule, System.nanoTime()));
    }
}

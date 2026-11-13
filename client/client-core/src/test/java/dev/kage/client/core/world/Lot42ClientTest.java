package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Lô 42 thuần client — danh sách lệnh (ADR-267), giờ chat (ADR-268), ping (ADR-269), toạ độ (ADR-270). */
class Lot42ClientTest {

    @Test
    void helpTableHasNoDuplicateCommand() {
        Set<String> seen = new HashSet<>();
        for (CommandHelpCoordinator.Group group : CommandHelpCoordinator.GROUPS) {
            for (String command : group.commands()) {
                assertThat(command).startsWith("/");
                assertThat(seen.add(command)).as("trùng %s", command).isTrue();
            }
        }
    }

    @Test
    void helpFiltersByCommandOrGroupTitle() {
        List<String[]> all = CommandHelpCoordinator.matches("", key -> key);
        assertThat(all).hasSize(CommandHelpCoordinator.GROUPS.size());

        List<String[]> ping = CommandHelpCoordinator.matches("/ping", key -> key);
        assertThat(ping).hasSize(1);
        // lô 48 thêm /pinghud (ADR-316) — cùng chứa "/ping" nên cùng khớp, các lệnh khác vẫn không.
        assertThat(ping.getFirst()[1].split(" ")).containsExactlyInAnyOrder("/ping", "/pinghud");

        List<String[]> byTitle =
                CommandHelpCoordinator.matches("thu", key -> "help.group.mail".equals(key) ? "Thư" : key);
        assertThat(byTitle).anySatisfy(entry -> assertThat(entry[1]).contains("/xoathu", "/timthu"));

        assertThat(CommandHelpCoordinator.matches("khongcolenhnay", key -> key)).isEmpty();
    }

    @Test
    void timestampToggleParsesArguments() {
        assertThat(ChatTimestampCoordinator.next(false, "")).contains(true);
        assertThat(ChatTimestampCoordinator.next(true, "")).contains(false);
        assertThat(ChatTimestampCoordinator.next(false, "bat")).contains(true);
        assertThat(ChatTimestampCoordinator.next(true, "OFF")).contains(false);
        assertThat(ChatTimestampCoordinator.next(true, "xyz")).isEmpty();
    }

    @Test
    void timePrefixUsesHoursAndMinutes() {
        assertThat(ChatCoordinator.timePrefix(0L, ZoneOffset.ofHours(7))).isEqualTo("[07:00] ");
    }

    @Test
    void pingQualityBands() {
        assertThat(PingCoordinator.qualityKey(40)).isEqualTo("ping.quality.good");
        assertThat(PingCoordinator.qualityKey(PingCoordinator.GOOD_MS)).isEqualTo("ping.quality.fair");
        assertThat(PingCoordinator.qualityKey(PingCoordinator.FAIR_MS)).isEqualTo("ping.quality.poor");
    }

    @Test
    void coordinatesRoundAndNameZones() {
        assertThat(CoordinatesCoordinator.roundedX(12.6f)).isEqualTo(13L);
        assertThat(CoordinatesCoordinator.roundedX(-3.4f)).isEqualTo(-3L);
        assertThat(CoordinatesCoordinator.zoneKey(ZoneMapCoordinator.ZoneState.PVP))
                .isEqualTo("coords.zone.pvp");
        assertThat(CoordinatesCoordinator.zoneKey(ZoneMapCoordinator.ZoneState.SAFE))
                .isEqualTo("coords.zone.safe");
    }

    @Test
    void readyCheckAnswers() {
        assertThat(ReadyCheckCoordinator.answer("co")).contains(true);
        assertThat(ReadyCheckCoordinator.answer("Không")).contains(false);
        assertThat(ReadyCheckCoordinator.answer("yes")).contains(true);
        assertThat(ReadyCheckCoordinator.answer("maybe")).isEmpty();
    }

    @Test
    void countdownAndDiceArguments() {
        assertThat(PartyCountdownCoordinator.seconds("")).hasValue(PartyCountdownCoordinator.DEFAULT_SECONDS);
        assertThat(PartyCountdownCoordinator.seconds("7")).hasValue(7);
        assertThat(PartyCountdownCoordinator.seconds("x")).isEmpty();
        assertThat(DiceRollCoordinator.max("")).hasValue(0);
        assertThat(DiceRollCoordinator.max("20")).hasValue(20);
        assertThat(DiceRollCoordinator.max("-3")).isEmpty();
    }

    @Test
    void goldSplitAmountParsing() {
        assertThat(PartyGoldSplitCoordinator.amount("1.000")).hasValue(1000L);
        assertThat(PartyGoldSplitCoordinator.amount("250")).hasValue(250L);
        assertThat(PartyGoldSplitCoordinator.amount("0")).isEmpty();
        assertThat(PartyGoldSplitCoordinator.amount("abc")).isEmpty();
        assertThat(PartyGoldSplitCoordinator.outcomeKey(
                        dev.kage.protocol.SplitPartyGoldOutcome.SPLIT_PARTY_GOLD_OUTCOME_NOT_ENOUGH_GOLD))
                .isEqualTo("splitgold.not_enough_gold");
    }
}

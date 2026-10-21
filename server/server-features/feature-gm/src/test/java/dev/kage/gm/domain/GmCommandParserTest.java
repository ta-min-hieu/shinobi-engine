package dev.kage.gm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parser lệnh GM (ADR-052 D4/D8): whitelist, biên số chặt, nhận diện tiền tố. */
class GmCommandParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"/gm", "/GM help", "  /gm kick A", "/Gm   who B"})
    void shouldRecognizeGmLines(String line) {
        assertThat(GmCommandParser.isGmLine(line)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/gmx", "hello /gm", "/g m", "gm help", "/gmkick A"})
    void shouldNotTreatOtherChatAsGm(String line) {
        assertThat(GmCommandParser.isGmLine(line)).isFalse();
    }

    @Test
    void shouldParseEachCommand() {
        assertThat(ok("/gm")).isInstanceOf(GmCommand.Help.class);
        assertThat(ok("/gm kick TieuLy")).isEqualTo(new GmCommand.Kick("TieuLy"));
        assertThat(ok("/gm mute A 30")).isEqualTo(new GmCommand.Mute("A", 30));
        assertThat(ok("/gm give A kunai 5")).isEqualTo(new GmCommand.Give("A", "kunai", 5));
        assertThat(ok("/gm gold A 1000000")).isEqualTo(new GmCommand.Gold("A", 1_000_000L));
        assertThat(ok("/gm announce Bảo trì lúc 22h")).isEqualTo(new GmCommand.Announce("Bảo trì lúc 22h"));
        assertThat(ok("/gm heal A")).isEqualTo(new GmCommand.Heal("A"));
        assertThat(ok("/gm goto A")).isEqualTo(new GmCommand.Goto("A"));
        assertThat(ok("/gm inspect A")).isEqualTo(new GmCommand.Inspect("A"));
        assertThat(ok("/gm SUMMON A")).isEqualTo(new GmCommand.Summon("A"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/gm mute A 0",
                "/gm mute A 10081",
                "/gm mute A x",
                "/gm give A kunai 100",
                "/gm give A kunai 0",
                "/gm give A Kunai 1",
                "/gm gold A 0",
                "/gm gold A 1000001",
                "/gm gold A -5",
                "/gm kick",
                "/gm kick A B",
                "/gm summon",
                "/gm goto A B",
                "/gm teleport A",
                "/gm setlevel A 99",
                "/gm announce",
                "/gm event",
                "/gm event gold 2 60",
                "/gm event exp 1 60",
                "/gm event exp 6 60",
                "/gm event exp 2 0",
                "/gm event exp 2",
                "/gm event drop on"
            })
    void shouldRejectOutOfDomainOrUnknown(String line) {
        assertThat(GmCommandParser.parse(line)).isInstanceOf(GmCommandParser.Parsed.Invalid.class);
    }

    @Test
    void adminShouldOutrankGm() {
        assertThat(GmRole.ADMIN.atLeast(GmRole.GM)).isTrue();
        assertThat(GmRole.GM.atLeast(GmRole.ADMIN)).isFalse();
        assertThat(new GmCommand.Gold("A", 1).requiredRole()).isEqualTo(GmRole.ADMIN);
    }

    @Test
    void eventShouldParseStartAndStop_andNeedAdmin() {
        assertThat(ok("/gm event EXP 2 60")).isEqualTo(new GmCommand.Event(GameEventKind.EXP, 2, 60));
        GmCommand stop = ok("/gm event drop off");
        assertThat(stop).isEqualTo(new GmCommand.Event(GameEventKind.DROP, 0, 0));
        assertThat(((GmCommand.Event) stop).stops()).isTrue();
        assertThat(stop.requiredRole()).isEqualTo(GmRole.ADMIN);
    }

    private static GmCommand ok(String line) {
        GmCommandParser.Parsed parsed = GmCommandParser.parse(line);
        assertThat(parsed).isInstanceOf(GmCommandParser.Parsed.Ok.class);
        return ((GmCommandParser.Parsed.Ok) parsed).command();
    }
}

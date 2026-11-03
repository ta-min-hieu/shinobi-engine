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

    /** Lô 22 — bàn trực (ADR-103/104/105). */
    @Test
    void shouldParseTheServiceDeskCommands() {
        assertThat(ok("/gm motd Bảo trì   lúc 22h")).isEqualTo(new GmCommand.Motd("Bảo trì lúc 22h"));
        assertThat(ok("/gm motd OFF")).isEqualTo(new GmCommand.Motd(""));
        assertThat(ok("/gm code tet2027 5000 spirit_herb 3 100 7"))
                .isEqualTo(new GmCommand.CreateGiftCode("TET2027", 5000L, "spirit_herb", 3, 100, 7));
        assertThat(ok("/gm code VANG 1000 - 0 1 1"))
                .isEqualTo(new GmCommand.CreateGiftCode("VANG", 1000L, "", 0, 1, 1));
        assertThat(ok("/gm reports")).isEqualTo(new GmCommand.Reports());
        assertThat(ok("/gm report 12")).isEqualTo(new GmCommand.ShowReport(12L));
        assertThat(ok("/gm resolve 12")).isEqualTo(new GmCommand.ResolveReport(12L));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/gm code AB 100 - 0 1 1", // mã quá ngắn
                "/gm code GOOD 0 - 0 1 1", // không có quà
                "/gm code GOOD 100 - 0 0 1", // 0 lượt
                "/gm code GOOD 100 - 0 1 366", // quá 365 ngày
                "/gm code GOOD 100 Bad-Item 1 1 1",
                "/gm report abc",
                "/gm resolve 0",
                "/gm motd"
            })
    void shouldRejectMalformedServiceDeskCommands(String line) {
        assertThat(GmCommandParser.parse(line)).isInstanceOf(GmCommandParser.Parsed.Invalid.class);
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

    @Test
    void compensateShouldParseGoldAndItemRewards_andNeedAdmin() {
        assertThat(ok("/gm compensate maint01 500 - 0 1"))
                .isEqualTo(new GmCommand.Compensate("MAINT01", 500L, "", 0, 1));
        GmCommand withItem = ok("/gm compensate MAINT02 0 minor_healing_pill 5 10");
        assertThat(withItem).isEqualTo(new GmCommand.Compensate("MAINT02", 0L, "minor_healing_pill", 5, 10));
        assertThat(withItem.requiredRole()).isEqualTo(GmRole.ADMIN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/gm compensate MAINT 0 - 0 1",
                "/gm compensate MAINT 500 - 0 0",
                "/gm compensate MAINT 500 - 0 101",
                "/gm compensate MAINT 500 - 0",
                "/gm compensate MAINT 9999999 - 0 1",
                "/gm compensate MAINT 0 minor_healing_pill 100 1"
            })
    void compensateShouldRejectEmptyOrOutOfRangeRewards(String line) {
        assertThat(GmCommandParser.parse(line)).isInstanceOf(GmCommandParser.Parsed.Invalid.class);
    }

    private static GmCommand ok(String line) {
        GmCommandParser.Parsed parsed = GmCommandParser.parse(line);
        assertThat(parsed).isInstanceOf(GmCommandParser.Parsed.Ok.class);
        return ((GmCommandParser.Parsed.Ok) parsed).command();
    }
}

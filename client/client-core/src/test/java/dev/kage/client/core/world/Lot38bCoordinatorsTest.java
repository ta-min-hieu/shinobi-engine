package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ContactPrivacySetting;

/** Lô 38 (nửa sau) — đọc lệnh thư toàn bang (ADR-235), công tắc riêng tư (ADR-236/237), giờ đăng nhập (ADR-238). */
class Lot38bCoordinatorsTest {

    @Test
    void shouldSplitGuildMailOnBar() {
        assertThat(GuildMailCoordinator.parse(" Họp bang | Tối nay 8 giờ "))
                .contains(new GuildMailCoordinator.Draft("Họp bang", "Tối nay 8 giờ"));
        assertThat(GuildMailCoordinator.parse("không có gạch")).isEmpty();
        assertThat(GuildMailCoordinator.parse(" | chỉ nội dung")).isEmpty();
        assertThat(GuildMailCoordinator.parse("chỉ tiêu đề | ")).isEmpty();
    }

    @Test
    void shouldMapCommandsAndSwitches() {
        assertThat(ContactPrivacyCoordinator.settingOf("/DND"))
                .contains(ContactPrivacySetting.CONTACT_PRIVACY_SETTING_DO_NOT_DISTURB);
        assertThat(ContactPrivacyCoordinator.settingOf("/chanloimoi"))
                .contains(ContactPrivacySetting.CONTACT_PRIVACY_SETTING_BLOCK_STRANGER_INVITES);
        assertThat(ContactPrivacyCoordinator.settingOf("/chanthu")).isEmpty();
        assertThat(ContactPrivacyCoordinator.parseSwitch(" bat ")).contains(true);
        assertThat(ContactPrivacyCoordinator.parseSwitch("off")).contains(false);
        assertThat(ContactPrivacyCoordinator.parseSwitch("maybe")).isEmpty();
    }

    @Test
    void shouldFormatLoginTimeInGivenZone() {
        assertThat(LoginHistoryCoordinator.formatTime(0L, ZoneOffset.ofHours(7)))
                .isEqualTo("01/01 07:00");
    }
}

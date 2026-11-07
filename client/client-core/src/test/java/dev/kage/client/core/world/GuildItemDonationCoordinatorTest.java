package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-207 — đọc số lượng của {@code /gopdo}. */
class GuildItemDonationCoordinatorTest {

    @Test
    void shouldDefaultToOne_andRejectNonPositiveOrHugeNumbers() {
        assertThat(GuildItemDonationCoordinator.parseQuantity("")).contains(1);
        assertThat(GuildItemDonationCoordinator.parseQuantity(" 25 ")).contains(25);
        assertThat(GuildItemDonationCoordinator.parseQuantity("0")).isEmpty();
        assertThat(GuildItemDonationCoordinator.parseQuantity("-3")).isEmpty();
        assertThat(GuildItemDonationCoordinator.parseQuantity("12345")).isEmpty();
        assertThat(GuildItemDonationCoordinator.parseQuantity("abc")).isEmpty();
    }
}

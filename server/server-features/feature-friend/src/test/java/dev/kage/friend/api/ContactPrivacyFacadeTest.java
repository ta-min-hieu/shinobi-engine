package dev.kage.friend.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.friend.api.ContactPrivacyFacade.Contact;
import dev.kage.friend.api.ContactPrivacyFacade.Refusal;
import dev.kage.friend.api.ContactPrivacyFacade.Relation;
import dev.kage.friend.domain.ContactPrivacyPort.Settings;

/** ADR-236/237 — luật riêng tư liên lạc. */
class ContactPrivacyFacadeTest {

    private static final Relation STRANGER = new Relation(false, false);
    private static final Relation GUILDMATE = new Relation(false, true);
    private static final Relation FRIEND = new Relation(true, false);

    @Test
    void friendsAlwaysPass() {
        Settings both = new Settings(true, true);
        for (Contact contact : Contact.values()) {
            assertThat(ContactPrivacyFacade.refusal(both, contact, FRIEND)).isEqualTo(Refusal.NONE);
        }
    }

    @Test
    void doNotDisturbBlocksEveryContactFromNonFriendsIncludingGuildmates() {
        Settings dnd = new Settings(true, false);
        for (Contact contact : Contact.values()) {
            assertThat(ContactPrivacyFacade.refusal(dnd, contact, GUILDMATE)).isEqualTo(Refusal.DO_NOT_DISTURB);
        }
    }

    @Test
    void strangerInviteBlockOnlyCoversPartyAndTradeFromStrangers() {
        Settings strangers = new Settings(false, true);
        assertThat(ContactPrivacyFacade.refusal(strangers, Contact.PARTY, STRANGER))
                .isEqualTo(Refusal.STRANGER_INVITES);
        assertThat(ContactPrivacyFacade.refusal(strangers, Contact.TRADE, STRANGER))
                .isEqualTo(Refusal.STRANGER_INVITES);
        assertThat(ContactPrivacyFacade.refusal(strangers, Contact.PARTY, GUILDMATE))
                .isEqualTo(Refusal.NONE);
        assertThat(ContactPrivacyFacade.refusal(strangers, Contact.WHISPER, STRANGER))
                .isEqualTo(Refusal.NONE);
        assertThat(ContactPrivacyFacade.refusal(strangers, Contact.FRIEND, STRANGER))
                .isEqualTo(Refusal.NONE);
    }

    @Test
    void nothingOnMeansNothingRefused() {
        assertThat(ContactPrivacyFacade.refusal(Settings.NONE, Contact.TRADE, STRANGER))
                .isEqualTo(Refusal.NONE);
    }
}

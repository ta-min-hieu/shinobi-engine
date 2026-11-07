package dev.kage.mail.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.mail.domain.StrangerMailPort;

/** ADR-212 — chỉ từ chối khi người nhận bật chặn VÀ người gửi không phải bạn; tự gửi cho mình luôn được. */
class StrangerMailFacadeTest {

    private final Set<Long> blocking = new HashSet<>();
    private final StrangerMailFacade facade = new StrangerMailFacade(new StrangerMailPort() {
        @Override
        public boolean blocksStrangers(long characterId) {
            return blocking.contains(characterId);
        }

        @Override
        public void setBlocksStrangers(long characterId, boolean enabled) {
            if (enabled) {
                blocking.add(characterId);
            } else {
                blocking.remove(characterId);
            }
        }
    });

    @Test
    void shouldRefuseOnlyStrangers_whileTheRecipientBlocks() {
        long recipient = 1L;
        Set<Long> friendsOfRecipient = Set.of(2L);

        assertThat(facade.refuses(recipient, 3L, friendsOfRecipient::contains)).isFalse();
        facade.setBlocksStrangers(recipient, true);
        assertThat(facade.refuses(recipient, 3L, friendsOfRecipient::contains)).isTrue();
        assertThat(facade.refuses(recipient, 2L, friendsOfRecipient::contains)).isFalse();
        assertThat(facade.refuses(recipient, recipient, friendsOfRecipient::contains))
                .isFalse();
    }
}

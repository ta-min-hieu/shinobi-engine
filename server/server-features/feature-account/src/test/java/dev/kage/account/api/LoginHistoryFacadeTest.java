package dev.kage.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

/** ADR-238 — che IP trước khi lưu. */
class LoginHistoryFacadeTest {

    @Test
    void masksIpv4AndIpv6() throws UnknownHostException {
        assertThat(LoginHistoryFacade.mask(
                        new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 203, 113, 7, 9}), 7777)))
                .isEqualTo("203.113.x.x");
        assertThat(LoginHistoryFacade.mask(new InetSocketAddress(InetAddress.getByName("2001:db8:1:2::5"), 7777)))
                .isEqualTo("2001:db8:x:x");
    }

    @Test
    void unknownAddressIsQuestionMark() {
        assertThat(LoginHistoryFacade.mask(InetSocketAddress.createUnresolved("nowhere", 1)))
                .isEqualTo("?");
    }
}

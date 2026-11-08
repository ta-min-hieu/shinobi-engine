package dev.kage.account.api;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.List;

import dev.kage.account.domain.LoginHistoryPort;

/**
 * Lịch sử đăng nhập (ADR-238): ghi mỗi lần đăng nhập thành công với IP ĐÃ CHE (IPv4 giữ 2 khối đầu, IPv6 giữ 2 nhóm
 * đầu), người chơi xem {@value #SHOWN} lần gần nhất để tự phát hiện ai đó dùng tài khoản mình. Chạm DB — chỉ gọi trên
 * Virtual Thread.
 */
public final class LoginHistoryFacade {

    /** Số dòng trả cho người chơi. */
    public static final int SHOWN = 10;

    /** Số dòng giữ lại mỗi tài khoản (dòng cũ hơn bị xoá lúc ghi). */
    public static final int KEPT = 50;

    /** Không ghi gì (composition test). */
    public static final LoginHistoryFacade DISABLED = new LoginHistoryFacade(new LoginHistoryPort() {
        @Override
        public void record(long accountId, String maskedIp, Instant at, int keep) {
            // không lưu
        }

        @Override
        public List<Entry> recent(long accountId, int limit) {
            return List.of();
        }
    });

    private static final int IPV6_GROUP_BYTES = 2;
    private static final int BYTE_MASK = 0xFF;
    private static final int BYTE_BITS = 8;

    private final LoginHistoryPort port;

    /** Dựng ở composition root. */
    public LoginHistoryFacade(LoginHistoryPort port) {
        this.port = port;
    }

    /** IP đã che của một địa chỉ socket; không phải IP ⇒ {@code "?"}. Tĩnh để test. */
    public static String mask(SocketAddress address) {
        if (!(address instanceof InetSocketAddress socket) || socket.getAddress() == null) {
            return "?";
        }
        InetAddress ip = socket.getAddress();
        byte[] bytes = ip.getAddress();
        if (ip instanceof Inet4Address) {
            return (bytes[0] & BYTE_MASK) + "." + (bytes[1] & BYTE_MASK) + ".x.x";
        }
        if (ip instanceof Inet6Address) {
            return Integer.toHexString(group(bytes, 0)) + ":" + Integer.toHexString(group(bytes, 1)) + ":x:x";
        }
        return "?";
    }

    private static int group(byte[] bytes, int index) {
        int offset = index * IPV6_GROUP_BYTES;
        return ((bytes[offset] & BYTE_MASK) << BYTE_BITS) | (bytes[offset + 1] & BYTE_MASK);
    }

    /** Ghi một lần đăng nhập thành công. */
    public void record(long accountId, SocketAddress address, Instant at) {
        port.record(accountId, mask(address), at, KEPT);
    }

    /** {@value #SHOWN} lần gần nhất, mới trước. */
    public List<LoginHistoryPort.Entry> recent(long accountId) {
        return port.recent(accountId, SHOWN);
    }
}

package dev.kage.server.gateway;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hai chốt cấu hình của lệnh GM khi CHƯA có đăng nhập bằng mật khẩu (ADR-052 D3, Q1 theo khuyến nghị):
 * {@code KAGE_GM_ENABLED} (mặc định {@code false}; giá trị lạ ⇒ server từ chối khởi động, cùng khuôn
 * {@code KAGE_TLS_ENABLED}) và {@code KAGE_GM_ALLOWED_CIDRS} (mặc định chỉ loopback). Role trong DB là
 * điều kiện CẦN, chưa đủ: kẻ đoán được username GM nhưng không ngồi ở mạng của chủ dự án vẫn không ra
 * lệnh được.
 *
 * <p>⚠️ Chạy trong Docker có NAT thì địa chỉ nguồn có thể là IP gateway của Docker — kiểm lúc triển khai.
 */
final class GmConfig {

    static final String ENABLED_ENV = "KAGE_GM_ENABLED";
    static final String CIDRS_ENV = "KAGE_GM_ALLOWED_CIDRS";
    static final String DEFAULT_CIDRS = "127.0.0.1/32,::1/128";
    private static final int BITS_PER_BYTE = 8;

    /** Đọc MỘT lần lúc nạp lớp (lần đầu một lệnh GM tới hoặc lúc khởi động — xem {@link #current()}). */
    private static final GmConfig CURRENT = fromEnvironment();

    private final boolean enabled;
    private final List<Cidr> allowed;

    GmConfig(boolean enabled, List<Cidr> allowed) {
        this.enabled = enabled;
        this.allowed = List.copyOf(allowed);
    }

    static GmConfig current() {
        return CURRENT;
    }

    static GmConfig fromEnvironment() {
        return new GmConfig(parseEnabled(System.getenv(ENABLED_ENV)), parseCidrs(System.getenv(CIDRS_ENV)));
    }

    /** Thiếu/rỗng ⇒ {@code false}; chỉ nhận {@code true}/{@code false} — giá trị lạ là lỗi cấu hình to tiếng. */
    static boolean parseEnabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException(ENABLED_ENV + " phải là true/false, nhận: " + raw);
        };
    }

    static List<Cidr> parseCidrs(String raw) {
        String source = raw == null || raw.isBlank() ? DEFAULT_CIDRS : raw;
        List<Cidr> out = new ArrayList<>();
        for (String part : source.split(",", -1)) {
            String entry = part.strip();
            if (!entry.isEmpty()) {
                out.add(Cidr.parse(entry));
            }
        }
        return out;
    }

    boolean enabled() {
        return enabled;
    }

    /** Địa chỉ nguồn của kết nối có nằm trong danh sách cho phép không. */
    boolean allows(SocketAddress remote) {
        if (!(remote instanceof InetSocketAddress inet) || inet.getAddress() == null) {
            return false;
        }
        byte[] address = inet.getAddress().getAddress();
        return allowed.stream().anyMatch(cidr -> cidr.contains(address));
    }

    /** Một dải {@code địa_chỉ/độ_dài_tiền_tố} (IPv4 hoặc IPv6, chỉ chữ số — không tra DNS). */
    static final class Cidr {
        private final byte[] network;
        private final int prefixLength;

        private Cidr(byte[] network, int prefixLength) {
            this.network = network.clone();
            this.prefixLength = prefixLength;
        }

        static Cidr parse(String text) {
            int slash = text.indexOf('/');
            String host = slash < 0 ? text : text.substring(0, slash);
            byte[] network = literalAddress(host, text);
            int bits = slash < 0 ? network.length * BITS_PER_BYTE : Integer.parseInt(text.substring(slash + 1));
            if (bits < 0 || bits > network.length * BITS_PER_BYTE) {
                throw new IllegalStateException(CIDRS_ENV + ": độ dài tiền tố sai: " + text);
            }
            return new Cidr(network, bits);
        }

        /** Chỉ nhận CHỮ SỐ IP — {@code InetAddress.ofLiteral} không bao giờ tra DNS. */
        private static byte[] literalAddress(String host, String text) {
            try {
                return InetAddress.ofLiteral(host).getAddress();
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(CIDRS_ENV + ": không phải địa chỉ IP: " + text, e);
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / BITS_PER_BYTE;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remaining = prefixLength % BITS_PER_BYTE;
            if (remaining == 0) {
                return true;
            }
            int mask = 0xFF << (BITS_PER_BYTE - remaining) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}

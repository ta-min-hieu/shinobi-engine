package dev.kage.client.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;

/**
 * Cổng build phía client của "mọi opcode có người nhận" — nửa còn lại của
 * {@code OpcodeDispatchCoverageTest} (server-gateway).
 *
 * <p>Client định tuyến gói bằng chuỗi so sánh {@code opcode == Opcode.OPCODE_X_VALUE} rải trong các
 * coordinator, và gói không ai nhận thì bị bỏ IM LẶNG ({@code WorldScreen#dispatch}: "Opcode khác ...
 * bỏ qua"). Thêm một event server→client mà quên nhánh nhận thì server gửi đều đặn, client vứt đều
 * đặn, và không có dòng log nào.
 *
 * <p>Đọc mã nguồn chứ không chạy thử: hằng {@code *_VALUE} là hằng biên dịch, javac chép thẳng giá
 * trị vào bytecode nên không quét lớp nào thấy được; còn dựng {@code WorldScreen} thật cần cả libGDX.
 */
class ClientOpcodeCoverageTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /**
     * Một NHÁNH NHẬN: so sánh opcode của khung vừa tới với một hằng, hoặc chờ câu trả lời qua
     * {@code PendingReply.poll} (màn đăng nhập/chọn nhân vật khớp opcode bên trong lời gọi đó).
     */
    private static final Pattern RECEIVE_BRANCH =
            Pattern.compile("(?:[!=]=\\s*|PendingReply\\.poll\\([^;]*?)Opcode\\.OPCODE_([A-Z0-9_]+)_VALUE");

    /** Bất kỳ lần nhắc tới nào — đủ cho chiều GỬI (opcode là đối số của lời gọi gửi). */
    private static final Pattern ANY_REFERENCE = Pattern.compile("Opcode\\.OPCODE_([A-Z0-9_]+)_VALUE");

    @Test
    void everyServerToClientOpcodeShouldHaveAReceivingBranch() throws IOException {
        Set<String> received = scan(RECEIVE_BRANCH);
        Set<String> dropped = new TreeSet<>();
        for (Opcode opcode : Opcode.values()) {
            if (isServerToClient(opcode) && !received.contains(baseName(opcode))) {
                dropped.add(opcode.name());
            }
        }
        assertThat(dropped)
                .as("gói server→client mà client không có nhánh nhận — sẽ bị bỏ im lặng")
                .isEmpty();
    }

    @Test
    void everyClientToServerOpcodeShouldBeSentSomewhere() throws IOException {
        Set<String> referenced = scan(ANY_REFERENCE);
        Set<String> neverSent = new TreeSet<>();
        for (Opcode opcode : Opcode.values()) {
            if (isClientToServer(opcode) && !referenced.contains(baseName(opcode))) {
                neverSent.add(opcode.name());
            }
        }
        assertThat(neverSent)
                .as("lệnh server có handler mà client không bao giờ gửi")
                .isEmpty();
    }

    private static Set<String> scan(Pattern pattern) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        assertThat(names)
                .as("không đọc được mã nguồn ở " + MAIN_SOURCES.toAbsolutePath())
                .isNotEmpty();
        return names;
    }

    private static String baseName(Opcode opcode) {
        return opcode.name().substring("OPCODE_".length());
    }

    private static boolean isWireOpcode(Opcode opcode) {
        return opcode != Opcode.UNRECOGNIZED && opcode != Opcode.OPCODE_UNSPECIFIED;
    }

    /** Cùng quy ước hướng với {@code OpcodeRegistryTest} ở shared-protocol (hậu tố tên). */
    private static boolean isClientToServer(Opcode opcode) {
        String name = opcode.name();
        return isWireOpcode(opcode)
                && (opcode == Opcode.OPCODE_PING || name.endsWith("_COMMAND") || name.endsWith("_REQUEST"));
    }

    private static boolean isServerToClient(Opcode opcode) {
        return isWireOpcode(opcode) && !isClientToServer(opcode);
    }
}

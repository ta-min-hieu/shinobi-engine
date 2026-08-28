package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Giữ cho lời hứa "MỘT điểm phát duy nhất" của ADR-035 Decision-3 là thứ CHẠY ĐƯỢC, không phải một
 * câu javadoc.
 *
 * <p>ADR nêu rủi ro lớn nhất của {@code GoldChangedEvent} là bỏ sót một đường đổi vàng — và cách bỏ
 * sót dễ nhất không phải quên gọi hàm, mà là ai đó viết thêm một câu {@code UPDATE character_stat
 * SET gold = ...} ở adapter mới, hoàn toàn hợp lệ về mặt kiến trúc, chạy đúng, chỉ là không ai được
 * báo. Test này quét mã nguồn để chuyện đó gãy build ngay thay vì lộ ra khi người chơi thấy số cũ.
 *
 * <p>Quét bằng văn bản (không phải ArchUnit) vì thứ cần chặn là một chuỗi SQL, không phải một lời
 * gọi phương thức — ArchUnit không nhìn thấy nội dung {@code String}. Chỉ quét
 * {@code server-database/src/main/java}: mọi SQL trong dự án đều bắt buộc nằm ở module này
 * (server/CLAUDE.md), nên đây là toàn bộ bề mặt rủi ro. Test tự bỏ qua nếu không tìm thấy thư mục
 * nguồn (chạy từ thư mục làm việc lạ) thay vì báo xanh giả.
 */
class GoldLedgerSourceGuardTest {

    /** Câu lệnh DUY NHẤT ghi được vào {@code character_stat.gold}. */
    private static final String GOLD_WRITE_MARKER = "SET gold";

    private static final String ALLOWED_FILE = "GoldLedger.java";

    @Test
    void onlyGoldLedgerMayContainSqlThatWritesGold() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        assertThat(Files.isDirectory(sourceRoot))
                .as(
                        "Không tìm thấy %s — test này vô nghĩa nếu không quét được gì (cwd=%s)",
                        sourceRoot, Path.of("").toAbsolutePath())
                .isTrue();

        List<String> offenders;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            // Dùng toString() thay vì getFileName(): Path#getFileName trả null cho path rỗng nên
            // SpotBugs bắt NP ở đó, mà ở đây so hậu tố là đủ và không có nhánh null nào.
            offenders = files.map(Path::toString)
                    .filter(path -> path.endsWith(".java"))
                    .filter(path -> !path.endsWith(ALLOWED_FILE))
                    .filter(GoldLedgerSourceGuardTest::writesGold)
                    .sorted()
                    .toList();
        }

        assertThat(offenders)
                .as(
                        "Chỉ %s được phép ghi character_stat.gold (ADR-035 Decision-3 — một điểm phát duy"
                                + " nhất cho GoldChangedEvent). File dưới đây đang tự viết SQL đổi gold, nghĩa"
                                + " là đường đó sẽ KHÔNG báo cho client: %s",
                        ALLOWED_FILE, offenders)
                .isEmpty();
    }

    /** Chốt ngược: nếu {@link #GOLD_WRITE_MARKER} không còn khớp gì, test trên xanh một cách vô nghĩa. */
    @Test
    void goldLedgerItselfStillMatchesTheMarker() throws IOException {
        Path goldLedger = Path.of("src", "main", "java", "dev", "kage", "server", "database", ALLOWED_FILE);

        assertThat(Files.readString(goldLedger, StandardCharsets.UTF_8))
                .as(
                        "Marker '%s' không còn khớp GoldLedger — đổi cách viết SQL thì phải đổi cả marker,"
                                + " nếu không guard ở test kia thành vô dụng",
                        GOLD_WRITE_MARKER)
                .contains(GOLD_WRITE_MARKER);
    }

    private static boolean writesGold(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8).contains(GOLD_WRITE_MARKER);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file nguồn: " + path, e);
        }
    }
}

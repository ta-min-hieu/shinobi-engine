package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gác các ràng buộc THẬT của {@link ScrollingList#MAX_BUILT_ROWS}: trần số dòng được DỰNG phải nằm
 * TRÊN mọi sức chứa mà server có, nếu không phần vượt trần biến mất khỏi giao diện trong im lặng —
 * không dựng thì không cuộn tới, không bấm được.
 *
 * <p>KHÔNG ghim con số trần (đổi 60 thành 80 là chuyện bình thường). Cái được ghim là QUAN HỆ giữa
 * trần và dữ liệu server, và dữ liệu đó (danh mục cửa hàng, số nhiệm vụ) đọc thẳng từ migration chứ
 * không chép số — thêm món hàng thứ 61 thì ô này đỏ ngay, không phải đợi một buổi playtest.
 *
 * <p>Chiều cao khung ở trạng thái tệ nhất (thứ trần cũ từng hứa giữ) KHÔNG gác được ở đây vì cần GL
 * context thật — xem {@code HudLayoutBudgetTool}.
 */
class ScrollingListTest {

    /**
     * {@code PostgresInventoryAdapter.DEFAULT_CAPACITY} — module server, chép có chủ đích (client-core
     * không được phụ thuộc server). Nó là trần thật của túi đồ VÀ của mỗi lời chào giao dịch hợp lệ:
     * mỗi món chào là một dòng {@code inventory_item} của chính người chào.
     */
    private static final int SERVER_BAG_CAPACITY = 20;

    private static final Path MIGRATIONS = Path.of("server/server-database/src/main/resources/db/migration");

    private static final Path CLIENT_CORE_SOURCES = Path.of("client/client-core/src/main/java");

    /** Mọi seed {@code shop_item} hiện có là MỘT dòng mỗi câu lệnh ({@code INSERT … SELECT … WHERE code = …}). */
    private static final Pattern SHOP_ITEM_INSERT = Pattern.compile("(?i)\\bINSERT\\s+INTO\\s+shop_item\\b");

    /** Cùng quy ước một dòng mỗi câu lệnh — V11/V28/V30/V33/V37. */
    private static final Pattern QUEST_TEMPLATE_INSERT = Pattern.compile("(?i)\\bINSERT\\s+INTO\\s+quest_template\\b");

    /** Đúng hình dạng của bảy bản chép cũ ({@code MAX_VISIBLE_ROWS}/{@code _ITEMS}/{@code _SLOTS}). */
    private static final Pattern PER_WINDOW_CAP = Pattern.compile("static\\s+final\\s+int\\s+MAX_VISIBLE_\\w+\\s*=");

    /**
     * LỖI THẬT, bắt bằng CLIENT THẬT 2026-09-09: túi 14 món hiện "còn 4 vật phẩm nữa chưa hiện" và
     * không có cách nào chạm tới bốn món đó — món thứ 14 là {@code minor_healing_pill}, món DÙNG ĐƯỢC
     * duy nhất của game (ADR-046). Trần khi đó là 10 với lý do "chưa có ScrollPane" đã chết.
     */
    @Test
    @DisplayName("Trần > sức chứa túi của server — túi đồ và cả hai lời chào giao dịch")
    void exceedsServerBagCapacity() {
        assertThat(ScrollingList.MAX_BUILT_ROWS)
                .as("trần dựng dòng phải > sức chứa túi, nếu không món cuối túi KHÔNG bấm tới được")
                .isGreaterThan(SERVER_BAG_CAPACITY);
    }

    /**
     * LỖI THẬT tìm thấy 2026-09-11: trần cửa hàng là 8 trong khi server bán 19 món, sắp theo
     * {@code item_template_id} — 11 món cuối (có cả {@code minor_healing_pill}, seed ở V36) không bao
     * giờ được dựng, không mua được. Ô này ĐỎ với trần cũ.
     */
    @Test
    @DisplayName("Trần > số món cửa hàng server seed — món cuối danh mục phải mua được")
    void exceedsSeededShopCatalog() throws IOException {
        int catalogSize = countInMigrations(SHOP_ITEM_INSERT);

        assertThat(catalogSize)
                .as("không đếm được dòng shop_item nào — đường dẫn migration đổi?")
                .isPositive();
        assertThat(ScrollingList.MAX_BUILT_ROWS)
                .as("trần dựng dòng phải > %d món cửa hàng, nếu không món cuối KHÔNG mua được", catalogSize)
                .isGreaterThan(catalogSize);
    }

    @Test
    @DisplayName("Trần > số nhiệm vụ server seed — nhật ký phải liệt kê được mọi nhiệm vụ")
    void exceedsSeededQuestTemplates() throws IOException {
        int questCount = countInMigrations(QUEST_TEMPLATE_INSERT);

        assertThat(questCount)
                .as("không đếm được quest_template nào — đường dẫn migration đổi?")
                .isPositive();
        assertThat(ScrollingList.MAX_BUILT_ROWS)
                .as("trần dựng dòng phải > %d nhiệm vụ, nếu không nhiệm vụ cuối biến khỏi nhật ký", questCount)
                .isGreaterThan(questCount);
    }

    /**
     * Không cửa sổ nào tự đặt lại một trần riêng. Đó chính là cách lỗi này sinh sôi: BẢY cửa sổ tự đặt
     * trần 6–10 dòng, sáu cái chép cùng một lý do đã chết, và sửa một cái không làm những cái kia hết
     * sai. Hòm thư, bang hội, bạn bè KHÔNG có trần phía server để so — với chúng, ràng buộc thật duy
     * nhất là "dùng trần chung, chiều cao do vùng cuộn chặn", và ô này gác nửa đầu.
     *
     * <p>Chỉ bắt đúng hình dạng của các bản chép cũ, không bắt mọi biến thể — đủ để lần sau ai chép
     * lại khuôn cũ thì build đỏ và dẫn họ tới {@link ScrollingList}.
     */
    @Test
    @DisplayName("Không cửa sổ nào giữ trần MAX_VISIBLE_* riêng — mọi danh sách dùng ScrollingList.MAX_BUILT_ROWS")
    void noWindowKeepsItsOwnRowCap() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(repoRoot().resolve(CLIENT_CORE_SOURCES))) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (PER_WINDOW_CAP
                        .matcher(Files.readString(file, StandardCharsets.UTF_8))
                        .find()) {
                    // Đường dẫn tương đối, không getFileName(): Path.getFileName() khai trả null
                    // (thư mục gốc không có tên) và tên file trần thì không nói file nằm ở đâu.
                    offenders.add(repoRoot().relativize(file).toString());
                }
            }
        }

        assertThat(offenders)
                .as("trần riêng từng cửa sổ — dùng ScrollingList.MAX_BUILT_ROWS + vùng cuộn chặn chiều cao")
                .isEmpty();
    }

    private static int countInMigrations(Pattern statement) throws IOException {
        Path directory = repoRoot().resolve(MIGRATIONS);
        assertThat(directory).as("thư mục migration phải tồn tại").isDirectory();
        int count = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                Matcher matcher = statement.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Cùng cách {@code FontFactoryVietnameseGlyphTest}: đi ngược lên tới {@code settings.gradle.kts}. */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (dir.resolve("settings.gradle.kts").toFile().exists()) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Không tìm thấy settings.gradle.kts từ " + Path.of("").toAbsolutePath());
    }
}

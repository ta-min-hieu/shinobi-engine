package dev.kage.server.database.quest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Cổng nội dung: mọi {@code give_item("mã", n)} trong script của MỌI dòng {@code quest_template} phải
 * trỏ tới một mã có thật trong {@code item_template} (2026-10-02).
 *
 * <p><b>Vì sao cần.</b> Từ khi {@code ClaimQuestRewardUseCase} coi một lần trao hụt là lỗi cứng
 * (rollback + {@code INVENTORY_FULL}, sửa mất món khi túi gần đầy), một mã gõ sai trong script không
 * còn "trao thiếu im lặng" nữa mà làm quest KHÔNG BAO GIỜ lĩnh được — người chơi chỉ thấy "túi đầy"
 * dù túi trống. Trước test này không cổng nào soi mã vật phẩm trong script quest
 * ({@code verifyContentKeys} chỉ soi khoá i18n).
 *
 * <p>Đọc danh sách quest từ DB sau Flyway (không liệt kê thư mục classpath) để quest nào thêm sau
 * cũng tự được soi, kể cả quest có script nằm ở module khác.
 */
@Testcontainers
class QuestRewardItemCodesIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Pattern GIVE_ITEM_CODE = Pattern.compile("give_item\\(\\s*\"([^\"]+)\"");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);
    }

    @Test
    void shouldOnlyGiveItemCodesThatExistInTheItemCatalog() {
        Map<String, String> scriptByQuest = readScriptRefs();
        Set<String> itemCodes = readItemCodes();
        List<String> unknown = new ArrayList<>();
        int grantsSeen = 0;

        for (Map.Entry<String, String> quest : scriptByQuest.entrySet()) {
            Matcher matcher = GIVE_ITEM_CODE.matcher(loadScript(quest.getValue()));
            while (matcher.find()) {
                grantsSeen++;
                if (!itemCodes.contains(matcher.group(1))) {
                    unknown.add(quest.getKey() + " → " + matcher.group(1));
                }
            }
        }

        // Không có lời give_item nào = regex hoặc nguồn đọc đã hỏng, không phải "mọi thứ đều đúng".
        assertThat(grantsSeen).as("số lời give_item tìm thấy").isPositive();
        assertThat(unknown)
                .as("give_item trỏ tới mã không có trong item_template")
                .isEmpty();
    }

    private static String loadScript(String scriptRef) {
        try (InputStream in = QuestRewardItemCodesIntegrationTest.class.getResourceAsStream(scriptRef)) {
            assertThat(in).as("script %s phải có trên classpath", scriptRef).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> readScriptRefs() {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT code, script_ref FROM quest_template ORDER BY code");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        assertThat(result).as("quest_template sau Flyway").isNotEmpty();
        return result;
    }

    private static Set<String> readItemCodes() {
        Set<String> result = new HashSet<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT code FROM item_template");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }
}

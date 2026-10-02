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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Cổng nội dung cho ĐỒ THỊ quest sau Flyway (2026-10-02) — anh em của
 * {@link QuestRewardItemCodesIntegrationTest}, soi những tham chiếu còn lại mà không cổng nào soi:
 *
 * <ul>
 *   <li>chuỗi {@code prerequisite_quest_id} không có vòng (một vòng làm cả chuỗi KHÔNG BAO GIỜ nhận
 *       được — FK chỉ bảo đảm id tồn tại, không chặn vòng);
 *   <li>mọi quest đều được MỘT hội thoại NPC chào mời ({@code accept_quest:<code>}) — không có
 *       {@code AcceptQuestCommand} riêng, nên quest không ai mời là quest chết;
 *   <li>mọi {@code event.monster_code === "x"} trong script trỏ tới loài có thật VÀ có mặt ngoài thế
 *       giới chính (một điểm {@code monster_spawn} world {@code main}) — gõ sai mã thì quest đứng yên
 *       vĩnh viễn, script không báo lỗi gì vì so sánh chuỗi chỉ đơn giản là sai. Boss hầm ngục
 *       ({@code sample_boss}) KHÔNG tính: mã của nó nằm cứng trong {@code DungeonInstanceRegistry},
 *       ngoài tầm DB; quest đầu tiên nhắm boss hầm ngục phải nới cổng này có chủ đích;
 *   <li>mọi {@code event.npc_code === "x"} trỏ tới NPC có thật VÀ có đứng ở đâu đó
 *       ({@code npc_spawn});
 *   <li>mọi {@code take_item("x", n)} trỏ tới mã có thật trong {@code item_template}.
 * </ul>
 *
 * <p>Mỗi phép kiểm khẳng định số tham chiếu đã soi là DƯƠNG: regex hỏng hay nguồn đọc rỗng thì test
 * đỏ, không xanh vì "chẳng có gì để soi".
 */
@Testcontainers
class QuestContentGraphIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Pattern MONSTER_CODE = Pattern.compile("event\\.monster_code\\s*===\\s*\"([^\"]+)\"");
    private static final Pattern NPC_CODE = Pattern.compile("event\\.npc_code\\s*===\\s*\"([^\"]+)\"");
    private static final Pattern TAKE_ITEM_CODE = Pattern.compile("take_item\\(\\s*\"([^\"]+)\"");
    private static final Pattern ACCEPT_QUEST = Pattern.compile("action:\\s*\"accept_quest:([^\"]+)\"");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
    void shouldHaveAcyclicPrerequisiteChains() {
        Map<String, String> prerequisiteByQuest = readPairs("""
                SELECT q.code, p.code FROM quest_template q
                  JOIN quest_template p ON p.id = q.prerequisite_quest_id
                """);
        List<String> cycles = new ArrayList<>();
        for (String start : prerequisiteByQuest.keySet()) {
            Set<String> visited = new HashSet<>();
            String current = start;
            while (current != null && visited.add(current)) {
                current = prerequisiteByQuest.get(current);
            }
            if (current != null) {
                cycles.add(start + " → … → " + current);
            }
        }

        assertThat(prerequisiteByQuest).as("cạnh tiên quyết sau Flyway").isNotEmpty();
        assertThat(cycles)
                .as("chuỗi tiên quyết có vòng — không quest nào trong vòng nhận được")
                .isEmpty();
    }

    @Test
    void shouldOfferEveryQuestFromSomeNpcDialogue() {
        Set<String> offered = new HashSet<>();
        for (String scriptRef : readColumn("SELECT dialogue_script_ref FROM npc_template")) {
            Matcher matcher = ACCEPT_QUEST.matcher(loadScript(scriptRef));
            while (matcher.find()) {
                offered.add(matcher.group(1));
            }
        }
        Set<String> questCodes = readColumn("SELECT code FROM quest_template");
        Set<String> neverOffered = new HashSet<>(questCodes);
        neverOffered.removeAll(offered);
        Set<String> offeredButUnknown = new HashSet<>(offered);
        offeredButUnknown.removeAll(questCodes);

        assertThat(offered).as("lời mời accept_quest trong hội thoại NPC").isNotEmpty();
        assertThat(neverOffered)
                .as("quest không NPC nào mời — không ai nhận được")
                .isEmpty();
        assertThat(offeredButUnknown)
                .as("hội thoại mời quest không có trong quest_template")
                .isEmpty();
    }

    @Test
    void shouldOnlyTrackMonstersThatExistAndAppearInTheWorld() {
        Set<String> reachable = readColumn("""
                SELECT m.code FROM monster_template m
                 WHERE EXISTS (SELECT 1 FROM monster_spawn s
                                WHERE s.monster_template_id = m.id AND s.world_code = 'main')
                """);
        assertReferencesKnown(MONSTER_CODE, reachable, "monster_code không có loài nào đang xuất hiện");
    }

    @Test
    void shouldOnlyTrackNpcsThatExistAndStandSomewhere() {
        Set<String> standing = readColumn("""
                SELECT n.code FROM npc_template n
                 WHERE EXISTS (SELECT 1 FROM npc_spawn s WHERE s.npc_template_id = n.id)
                """);
        assertReferencesKnown(NPC_CODE, standing, "npc_code không có NPC nào đang đứng ngoài thế giới");
    }

    @Test
    void shouldOnlyTakeItemCodesThatExistInTheItemCatalog() {
        assertReferencesKnown(
                TAKE_ITEM_CODE, readColumn("SELECT code FROM item_template"), "take_item trỏ tới mã không có");
    }

    private static void assertReferencesKnown(Pattern pattern, Set<String> known, String description) {
        List<String> unknown = new ArrayList<>();
        int seen = 0;
        for (Map.Entry<String, String> quest :
                readPairs("SELECT code, script_ref FROM quest_template").entrySet()) {
            Matcher matcher = pattern.matcher(loadScript(quest.getValue()));
            while (matcher.find()) {
                seen++;
                if (!known.contains(matcher.group(1))) {
                    unknown.add(quest.getKey() + " → " + matcher.group(1));
                }
            }
        }
        // Không tìm thấy tham chiếu nào = regex hoặc nguồn đọc đã hỏng, không phải "mọi thứ đều đúng".
        assertThat(seen).as("số tham chiếu %s tìm thấy", pattern.pattern()).isPositive();
        assertThat(known).as("tập mã đối chiếu").isNotEmpty();
        assertThat(unknown).as(description).isEmpty();
    }

    private static String loadScript(String scriptRef) {
        try (InputStream in = QuestContentGraphIntegrationTest.class.getResourceAsStream(scriptRef)) {
            assertThat(in).as("script %s phải có trên classpath", scriptRef).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> readPairs(String sql) {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private static Set<String> readColumn(String sql) {
        Set<String> result = new HashSet<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
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

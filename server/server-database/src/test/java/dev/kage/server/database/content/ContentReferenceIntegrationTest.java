package dev.kage.server.database.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.dialogue.PostgresNpcTemplateAdapter;

/**
 * Cổng TOÀN VẸN THAM CHIẾU của dữ liệu nội dung sau Flyway (2026-10-03) — anh em của
 * {@code QuestContentGraphIntegrationTest} (đồ thị quest) và {@code SafeZoneIntegrationTest}/
 * {@code SpawnGeometryIntegrationTest} (hình học spawn). Ba cổng đó không soi những tham chiếu dưới
 * đây, và FK của DB cũng không: mỗi tham chiếu ở đây đi qua một CHUỖI MÃ (so bằng chuỗi lúc chạy)
 * hoặc ra khỏi DB (file asset), nên sai thì không lệnh SQL nào báo lỗi.
 *
 * <ul>
 *   <li><b>Bảng rơi tra bằng MÃ LOÀI.</b> Đường chạy thật ({@code ConnectedCharacterRegistry
 *       .onMonsterKilled} → {@code InventoryFacade.grantLoot(characterId, mã quái)}) tra
 *       {@code drop_table.code} bằng mã loài, KHÔNG qua khoá ngoại {@code monster_template
 *       .drop_table_id}. Loài không có bảng cùng mã rơi vào nhánh WARN "chắc gõ sai mã" của
 *       {@code PostgresDropTableAdapter} — quy ước V33: loài cố ý không rơi gì vẫn phải có một bảng
 *       RỖNG mang mã của nó.
 *   <li><b>Dòng bảng rơi</b> nằm trong biên: tỉ lệ 1–100, số lượng 1 ≤ min ≤ max ≤ {@code max_stack}.
 *   <li><b>Cửa hàng không in vàng:</b> món cửa hàng BÁN RA ({@code buy_price > 0}) có
 *       {@code sell_price < buy_price} (mua rồi bán lại phải LỖ — V21 ghi bất biến này bằng chữ, đây
 *       là chỗ máy giữ nó). Món chỉ-thu-mua ({@code buy_price = 0}, V55) không có đường mua vào nên
 *       không có vòng nào để lặp — nhưng phải bán được thật ({@code sell_price > 0}), không thì dòng
 *       đó vô nghĩa.
 *   <li><b>Mọi {@code take_item} đòi một món KIẾM ĐƯỢC</b> (rơi từ loài có mặt ở thế giới chính, bán
 *       ở cửa hàng, hoặc là thưởng {@code give_item} của một quest) — nếu không quest đứng yên mãi.
 *   <li><b>Vùng an toàn</b> có diện tích thật (min &lt; max) và không chồng nhau trong cùng world.
 *   <li><b>Mọi cây hội thoại trong {@code npc_template}</b> (đọc từ DB, không phải danh sách gõ tay
 *       như {@code ProductionDialogueTreesTest}) — mọi action điều hướng trỏ tới node có thật, mọi
 *       node có lối ra {@code end}. NPC thêm sau tự được soi.
 *   <li><b>Asset nguồn:</b> mọi {@code monster_template.model_key} và mọi NPC có file {@code .blend}
 *       đúng quy ước {@code BlendPathResolver}; mọi vật phẩm có icon nguồn, trừ đúng danh sách nợ art
 *       đã biết {@link #ITEMS_KNOWN_TO_LACK_AN_ICON}.
 * </ul>
 *
 * <p>Mỗi phép kiểm khẳng định tập đã soi KHÔNG rỗng — truy vấn hay regex hỏng thì đỏ, không xanh vì
 * "chẳng có gì để soi".
 */
@Testcontainers
class ContentReferenceIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Pattern TAKE_ITEM_CODE = Pattern.compile("take_item\\(\\s*\"([^\"]+)\"");
    private static final Pattern GIVE_ITEM_CODE = Pattern.compile("give_item\\(\\s*\"([^\"]+)\"");

    /**
     * Vật phẩm ĐÃ BIẾT là chưa có icon nguồn ({@code assets/raw/blender/props/<code>.blend}, sinh từ
     * {@code tools/blender/build_item_icon.py}) — trong túi đồ chúng hiện ô TRỐNG. Đây là nợ art, không
     * phải lỗi dữ liệu (2026-10-03). Danh sách được GHIM hai chiều: thêm vật phẩm mới thiếu icon thì
     * đỏ, mà vẽ xong icon cho một món ở đây cũng đỏ — để danh sách không bao giờ nói dối.
     */
    private static final Set<String> ITEMS_KNOWN_TO_LACK_AN_ICON = Set.of("outer_sect_tally", "blood_sand_talisman");

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
    void everyMonsterHasALootTableUnderItsOwnCode() {
        assertThat(readColumn("SELECT code FROM monster_template"))
                .as("monster_template sau Flyway")
                .isNotEmpty();
        assertThat(readColumn("""
                SELECT m.code FROM monster_template m
                 WHERE NOT EXISTS (SELECT 1 FROM drop_table d WHERE d.code = m.code)
                """))
                .as("loài không có drop_table cùng mã — grantLoot tra theo MÃ LOÀI nên rơi vào nhánh WARN"
                        + " 'gõ sai mã'; loài cố ý không rơi gì phải có bảng RỖNG (quy ước V33)")
                .isEmpty();
        assertThat(readColumn("""
                SELECT m.code || ' → ' || d.code FROM monster_template m
                  JOIN drop_table d ON d.id = m.drop_table_id
                 WHERE d.code <> m.code
                """))
                .as("drop_table_id trỏ sang bảng của loài KHÁC — hai đường đọc nói hai câu khác nhau")
                .isEmpty();
        assertThat(readColumn("SELECT code FROM monster_template WHERE drop_table_id IS NULL"))
                .as("drop_table_id NULL trong khi bảng cùng mã có thật — cột nói 'không có bảng rơi' (V53)")
                .isEmpty();
    }

    @Test
    void lootEntriesStayWithinChanceAndStackBounds() {
        assertThat(readColumn("SELECT id::text FROM drop_table_entry"))
                .as("drop_table_entry sau Flyway")
                .isNotEmpty();
        assertThat(readColumn("""
                SELECT d.code || ' → ' || i.code FROM drop_table_entry e
                  JOIN drop_table d ON d.id = e.drop_table_id
                  JOIN item_template i ON i.id = e.item_template_id
                 WHERE e.drop_chance_percent NOT BETWEEN 1 AND 100
                    OR e.min_quantity < 1
                    OR e.max_quantity < e.min_quantity
                    OR e.max_quantity > i.max_stack
                """))
                .as("dòng bảng rơi ngoài biên (tỉ lệ 1–100, 1 ≤ min ≤ max ≤ max_stack)")
                .isEmpty();
    }

    @Test
    void shopNeverBuysBackAtOrAboveItsOwnPrice() {
        assertThat(readColumn("SELECT item_template_id::text FROM shop_item"))
                .as("shop_item sau Flyway")
                .isNotEmpty();
        assertThat(readColumn("""
                SELECT i.code FROM shop_item s JOIN item_template i ON i.id = s.item_template_id
                 WHERE (s.buy_price > 0 AND s.sell_price >= s.buy_price)
                    OR (s.buy_price = 0 AND s.sell_price <= 0)
                """))
                .as("giá bán lại ≥ giá mua (in vàng vô hạn) hoặc dòng chỉ-thu-mua mà bán ra 0")
                .isEmpty();
    }

    @Test
    void everyItemAQuestTakesCanBeObtained() {
        Set<String> obtainable = readColumn("""
                SELECT i.code FROM item_template i
                 WHERE EXISTS (SELECT 1 FROM shop_item s WHERE s.item_template_id = i.id AND s.buy_price > 0)
                    OR EXISTS (SELECT 1 FROM drop_table_entry e
                                 JOIN drop_table d ON d.id = e.drop_table_id
                                 JOIN monster_template m ON m.code = d.code
                                 JOIN monster_spawn sp ON sp.monster_template_id = m.id AND sp.world_code = 'main'
                                WHERE e.item_template_id = i.id)
                """);
        Set<String> taken = new TreeSet<>();
        for (String scriptRef : readColumn("SELECT script_ref FROM quest_template")) {
            String script = loadScript(scriptRef);
            obtainable.addAll(matches(GIVE_ITEM_CODE, script));
            taken.addAll(matches(TAKE_ITEM_CODE, script));
        }
        Set<String> unobtainable = new TreeSet<>(taken);
        unobtainable.removeAll(obtainable);

        assertThat(taken).as("lời take_item trong script quest").isNotEmpty();
        assertThat(unobtainable)
                .as("quest đòi giao món không nguồn nào cấp (không rơi, không bán, không quest nào thưởng)")
                .isEmpty();
    }

    @Test
    void safeZonesHaveAreaAndNeverOverlap() {
        assertThat(readColumn("SELECT code FROM safe_zone"))
                .as("safe_zone sau Flyway")
                .isNotEmpty();
        assertThat(readColumn("SELECT code FROM safe_zone WHERE min_x >= max_x OR min_y >= max_y"))
                .as("vùng an toàn diện tích 0 — không ai đứng vào được")
                .isEmpty();
        assertThat(readColumn("""
                SELECT a.code || ' ∩ ' || b.code FROM safe_zone a
                  JOIN safe_zone b ON a.id < b.id AND a.world_code = b.world_code
                 WHERE a.min_x < b.max_x AND b.min_x < a.max_x
                   AND a.min_y < b.max_y AND b.min_y < a.max_y
                """))
                .as("hai vùng an toàn chồng nhau — luật hồi máu/chặn aggro không còn một nghĩa")
                .isEmpty();
    }

    @Test
    void everyNpcDialogueTreeInTheDatabaseResolvesAndLetsThePlayerLeave() {
        GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(new PostgresNpcTemplateAdapter(dataSource));
        Set<String> npcCodes = readColumn("SELECT code FROM npc_template");
        List<String> broken = new ArrayList<>();
        for (String npcCode : npcCodes) {
            Optional<DialogueNode> start = useCase.execute(npcCode, DialogueNode.START_NODE_ID);
            if (start.isEmpty()) {
                broken.add(npcCode + ": không đọc được node start");
                continue;
            }
            walkTree(useCase, npcCode, start.get(), broken);
        }

        assertThat(npcCodes).as("npc_template sau Flyway").isNotEmpty();
        assertThat(broken).as("cây hội thoại hỏng").isEmpty();
    }

    @Test
    void everyMonsterAndNpcHasASourceModel() {
        Path blenderDir = findRepoRoot().resolve("assets/raw/blender");
        Set<String> missing = new TreeSet<>();
        for (String modelKey : readColumn("SELECT model_key FROM monster_template")) {
            missingBlend(blenderDir.resolve("monsters"), modelKey, "mob_").ifPresent(missing::add);
        }
        for (String npcCode : readColumn("SELECT code FROM npc_template")) {
            // Cùng phép đổi với NpcModelKey (client-core): town_elder → char_town-elder.
            String modelKey = "char_" + npcCode.toLowerCase(Locale.ROOT).replace('_', '-');
            missingBlend(blenderDir.resolve("characters"), modelKey, "char_").ifPresent(missing::add);
        }
        assertThat(missing)
                .as("model_key không có file .blend nguồn — client vẽ hộp thay cho model")
                .isEmpty();
    }

    @Test
    void everyItemHasASourceIconExceptTheKnownArtDebt() {
        Path propsDir = findRepoRoot().resolve("assets/raw/blender/props");
        Set<String> itemCodes = readColumn("SELECT code FROM item_template");
        Set<String> withoutIcon = new TreeSet<>();
        for (String code : itemCodes) {
            if (!Files.isRegularFile(propsDir.resolve(code + ".blend"))) {
                withoutIcon.add(code);
            }
        }

        assertThat(itemCodes).as("item_template sau Flyway").isNotEmpty();
        assertThat(withoutIcon)
                .as("vật phẩm thiếu icon nguồn (ô trống trong túi đồ) — khác danh sách nợ art đã ghim")
                .isEqualTo(new TreeSet<>(ITEMS_KNOWN_TO_LACK_AN_ICON));
    }

    private static void walkTree(
            GetDialogueNodeUseCase useCase, String npcCode, DialogueNode start, List<String> broken) {
        Set<String> seen = new HashSet<>();
        Deque<DialogueNode> queue = new ArrayDeque<>();
        seen.add(start.nodeId());
        queue.add(start);
        while (!queue.isEmpty()) {
            DialogueNode node = queue.removeFirst();
            boolean canLeave = false;
            for (DialogueOption option : node.options()) {
                canLeave |= DialogueOption.ACTION_END.equals(option.action());
                if (!isNavigation(option) || !seen.add(option.action())) {
                    continue;
                }
                Optional<DialogueNode> next = useCase.execute(npcCode, option.action());
                if (next.isPresent()) {
                    queue.addLast(next.get());
                } else {
                    broken.add(npcCode + ": node \"" + node.nodeId() + "\" trỏ tới \"" + option.action()
                            + "\" không tồn tại");
                }
            }
            if (!canLeave) {
                broken.add(npcCode + ": node \"" + node.nodeId() + "\" không có lựa chọn kết thúc");
            }
        }
    }

    private static boolean isNavigation(DialogueOption option) {
        return !DialogueOption.ACTION_END.equals(option.action())
                && !DialogueOption.ACTION_OPEN_SHOP.equals(option.action())
                && !DialogueOption.ACTION_OPEN_STORAGE.equals(option.action())
                && !option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX);
    }

    /** Quy ước thư mục của {@code BlendPathResolver} (tools/asset-pipeline): mob_x → monsters/x.blend. */
    private static Optional<String> missingBlend(Path folder, String modelKey, String prefix) {
        if (!modelKey.startsWith(prefix)) {
            return Optional.of(modelKey + " (thiếu tiền tố " + prefix + ")");
        }
        Path blend = folder.resolve(modelKey.substring(prefix.length()) + ".blend");
        return Files.isRegularFile(blend) ? Optional.empty() : Optional.of(modelKey);
    }

    /** Cùng cách {@code FontFactoryVietnameseGlyphTest}: đi ngược lên tới {@code settings.gradle.kts}. */
    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Không tìm thấy settings.gradle.kts từ " + Path.of("").toAbsolutePath());
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static String loadScript(String scriptRef) {
        try (InputStream in = ContentReferenceIntegrationTest.class.getResourceAsStream(scriptRef)) {
            assertThat(in).as("script %s phải có trên classpath", scriptRef).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> readColumn(String sql) {
        Set<String> result = new TreeSet<>();
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

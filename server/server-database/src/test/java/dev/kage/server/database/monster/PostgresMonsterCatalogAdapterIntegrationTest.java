package dev.kage.server.database.monster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.monster.domain.MonsterAiParams;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterTemplate;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * PostgreSQL THẬT qua Testcontainers — chứng minh migration V23 (ADR-037) và V25 (ADR-039) ÁP ĐƯỢC,
 * không chỉ phân tích cú pháp được, và adapter đọc đúng cái chúng seed.
 */
@Testcontainers
class PostgresMonsterCatalogAdapterIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    /**
     * Bản sao của {@code CombatConstants.ATTACK_RANGE_METERS} (server-world). Chép số chứ không
     * import: {@code server-database} KHÔNG được phụ thuộc {@code server-world} (ADR-037) — ràng
     * buộc đó chi phối mọi đường dữ liệu world.
     */
    private static final float MELEE_ATTACK_RANGE_METERS = 2f;

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
    void shouldKeepTrainingDummyNumbersExactlyAsBeforeAdr037() {
        // Hàng ĐỐI CHỨNG của cả migration: nếu con số này trôi thì mọi test combat đang dựa vào nó
        // đổi nghĩa trong im lặng.
        MonsterTemplate dummy = findByCode("training_dummy");

        assertThat(dummy.stats().maxHp()).isEqualTo(50);
        assertThat(dummy.stats().atk()).isEqualTo(8);
        assertThat(dummy.stats().def()).isEqualTo(5);
        assertThat(dummy.stats().res()).isEqualTo(3);
        assertThat(dummy.stats().hitPercent()).isEqualTo(90);
        assertThat(dummy.expReward()).isEqualTo(20L);
        assertThat(dummy.dropTableCode()).isEqualTo("training_dummy");
    }

    @Test
    void shouldSeedStrongerSpeciesSoDefenceBecomesMeasurable() {
        // Lý do ADR-037 tồn tại: quái duy nhất có atk 8 nên DEF của V22 không đo được.
        MonsterTemplate ogre = findByCode("stone_ogre");

        assertThat(ogre.stats().atk()).isEqualTo(40);
        assertThat(ogre.stats().maxHp()).isEqualTo(200);
        assertThat(ogre.expReward()).isEqualTo(90L);
    }

    @Test
    void shouldSeedTheArchiveWardenExactlyAsTheBalanceTableSays() {
        // V30 — loài của quest 3 (`clear_old_archive`). Bốn số này KHÔNG tuỳ ý: chúng được chọn từ
        // phép tính ở `OldArchiveDuelBalanceTest` (server-world), lớp duy nhất trả lời "quest 3 có
        // qua nổi bằng đường chơi thường không". Trôi một số ở đây là phá phép tính bên đó trong im
        // lặng, vì lớp kia đọc hằng số của chính nó chứ không đọc DB.
        MonsterTemplate warden = findByCode("archive_warden");

        assertThat(warden.stats().maxHp()).isEqualTo(120);
        assertThat(warden.stats().atk()).isEqualTo(26);
        assertThat(warden.stats().def()).isEqualTo(14);
        assertThat(warden.stats().res()).isEqualTo(10);
        assertThat(warden.expReward()).isEqualTo(55L);
        assertThat(warden.dropTableCode()).isEqualTo("archive_warden");
    }

    @Test
    void shouldPointEverySpeciesAtAModelKey() {
        // Không loài nào được thiếu model_key: rỗng nghĩa là client rơi về suy từ code và ra hộp.
        assertThat(new PostgresMonsterCatalogAdapter(dataSource).listTemplates())
                .isNotEmpty()
                .allSatisfy(template -> assertThat(template.modelKey()).isNotBlank());
    }

    @Test
    void shouldGiveEachSpeciesItsOwnModel() {
        // V23 cho cả ba loài dùng CHUNG `mob_training-dummy` (nợ art, ghi rõ trong migration đó);
        // V24 trả hai loài mới về model của chính chúng khi art đã vẽ xong (2026-08-30).
        //
        // Ô test này gác đúng khoảng trống đã suýt lọt: art nằm trên đĩa, `MonsterModelKey` phía
        // client đúng, mọi test khác xanh — mà trong game vẫn là ba cái cọc gỗ giống hệt nhau, vì
        // server luôn GỬI cột này và client chỉ tự suy khi nó rỗng.
        assertThat(findByCode("wandering_bandit").modelKey()).isEqualTo("mob_wandering-bandit");
        assertThat(findByCode("stone_ogre").modelKey()).isEqualTo("mob_stone-ogre");
        // Hình nộm là hàng đối chứng của V23 — không đợt nào được lặng lẽ đổi nó.
        assertThat(findByCode("training_dummy").modelKey()).isEqualTo("mob_training-dummy");
        // V30 mở LẠI món nợ đó một lần nữa, có chủ đích và ghi rõ: `archive_warden` mượn model của
        // quỷ đá (cùng dòng máy móc Linh Triều nên còn hợp lý), nên trong game hai loài trông y hệt
        // nhau. Khẳng định nó ở đây để nợ này nằm trong test chứ không nằm trong trí nhớ ai đó.
        assertThat(findByCode("archive_warden").modelKey()).isEqualTo("mob_stone-ogre");
    }

    @Test
    void shouldListMainWorldSpawnPointsOnThePlayLine() {
        List<MonsterSpawnPoint> spawns =
                new PostgresMonsterCatalogAdapter(dataSource).listSpawnPoints(MonsterSpawnPoint.MAIN_WORLD_CODE);

        assertThat(spawns).hasSize(7);
        // ADR-033 — quái lệch khỏi y=0 là quái người chơi không bao giờ chạm tới. DB có CHECK,
        // đây là hàng rào thứ hai và cũng là tài liệu sống cho ràng buộc đó.
        assertThat(spawns).allSatisfy(spawn -> assertThat(spawn.posY()).isZero());
        assertThat(spawns)
                .allSatisfy(spawn -> assertThat(spawn.respawnSeconds()).isPositive());
        // V29 giãn ba điểm CÓ AGGRO ra xa nhau (21 / 34 / 47) — xem
        // shouldKeepAggroSpawnsApartEnoughToPullOneAtATime. Hai cọc gỗ (aggro 0) giữ nguyên chỗ cũ.
        // V30 thêm hai thạch vệ của quest 3; V32 dời chúng sang PHÍA TÂY (-33 / -20) sau khi
        // playtest đo được đường đi phía đông tốn 65 máu — xem
        // shouldLeaveEveryAggroSpawnAQuietPlaceToFightIn và ROADMAP mục 62/63.
        // KHÔNG khớp theo thứ tự: adapter trả về theo id (thứ tự chèn của các migration), không
        // theo trục x — V32 dời hai thạch vệ sang âm nên hai thứ tự đó nay khác hẳn nhau.
        assertThat(spawns)
                .extracting(MonsterSpawnPoint::posX)
                .containsExactlyInAnyOrder(-33f, -20f, 8f, 14f, 21f, 34f, 47f);
    }

    /**
     * V29 (ROADMAP mục 57) — hàng rào HÌNH HỌC, không phải hàng rào chỉ số.
     *
     * <p>Một điểm {@code p} kéo được CẢ HAI con {@code A} và {@code B} khi {@code |p-A| <= aggroA}
     * và {@code |p-B| <= aggroB}, tức khi {@code |A-B| <= aggroA + aggroB}. Trước V29,
     * {@code wandering_bandit} nằm ở 21 và 27 (cách 6, tổng aggro 12) nên MỌI vị trí ở giữa kéo cả
     * hai và sát thương nhận vào gấp đôi — quest {@code night_watch_bandit} lại dẫn người chơi vào
     * đúng đoạn đó. {@code stone_ogre} ở 34 cũng chồng lên con thứ hai y như vậy.
     *
     * <p>Ô test này gác luật chung thay vì gác ba con số, nên thêm loài mới hay dời điểm spawn đều
     * bị soi lại. Quái {@code isPassive} (mộc nhân, aggro 0) không tham gia: chúng không tự đánh ai.
     */
    /**
     * ROADMAP mục 62 — hàng rào thứ HAI về hình học, sinh ra từ một buổi playtest đo được.
     *
     * <p>{@link #shouldKeepAggroSpawnsApartEnoughToPullOneAtATime} chỉ bảo đảm người chơi ĐI NGANG
     * không kéo hai con cùng lúc. Nhưng lúc họ ĐỨNG LẠI ĐÁNH tại một điểm spawn thì thứ quyết định
     * là <b>tầm đuổi</b> ({@code leashRadiusMeters}) của con bên cạnh, không phải tầm aggro: quái A
     * rời spawn của nó xa nhất {@code leashA}, rồi còn với thêm {@code ATTACK_RANGE_METERS}.
     *
     * <p>Đã trả giá thật: V30 đặt thạch vệ ở x=60 trong khi quỷ đá spawn ở x=47 có leash 10 ⇒ nó
     * đuổi tới x=57 và <b>ra đòn kết liễu</b> trong lúc người chơi đánh thạch vệ. Luật cũ không
     * thấy gì vì 13m &gt; 12m tổng aggro.
     */
    @Test
    void shouldLeaveEveryAggroSpawnAQuietPlaceToFightIn() {
        PostgresMonsterCatalogAdapter adapter = new PostgresMonsterCatalogAdapter(dataSource);
        Map<String, Float> leashByCode = new HashMap<>();
        Map<String, Float> aggroByCode = new HashMap<>();
        for (MonsterTemplate template : adapter.listTemplates()) {
            leashByCode.put(template.code(), template.aiParams().leashRadiusMeters());
            aggroByCode.put(template.code(), template.aiParams().aggroRadiusMeters());
        }

        List<MonsterSpawnPoint> aggressive = adapter.listSpawnPoints(MonsterSpawnPoint.MAIN_WORLD_CODE).stream()
                .filter(spawn -> aggroByCode.getOrDefault(spawn.monsterCode(), 0f) > 0f)
                .toList();

        for (MonsterSpawnPoint standing : aggressive) {
            for (MonsterSpawnPoint neighbour : aggressive) {
                if (standing == neighbour) {
                    continue;
                }
                float reach = leashByCode.get(neighbour.monsterCode()) + MELEE_ATTACK_RANGE_METERS;
                assertThat(Math.abs(standing.posX() - neighbour.posX()))
                        .as(
                                "đánh nhau ở %s@%.0f thì %s@%.0f đuổi tới nơi được (leash %.0f + tầm đánh %.0f)",
                                standing.monsterCode(),
                                standing.posX(),
                                neighbour.monsterCode(),
                                neighbour.posX(),
                                leashByCode.get(neighbour.monsterCode()),
                                MELEE_ATTACK_RANGE_METERS)
                        .isGreaterThan(reach);
            }
        }
    }

    @Test
    void shouldKeepAggroSpawnsApartEnoughToPullOneAtATime() {
        PostgresMonsterCatalogAdapter adapter = new PostgresMonsterCatalogAdapter(dataSource);
        Map<String, Float> aggroByCode = new HashMap<>();
        for (MonsterTemplate template : adapter.listTemplates()) {
            aggroByCode.put(template.code(), template.aiParams().aggroRadiusMeters());
        }

        List<MonsterSpawnPoint> aggressive = adapter.listSpawnPoints(MonsterSpawnPoint.MAIN_WORLD_CODE).stream()
                .filter(spawn -> aggroByCode.getOrDefault(spawn.monsterCode(), 0f) > 0f)
                .toList();

        assertThat(aggressive).hasSize(5); // 2 tán tu + 1 quỷ đá + 2 thạch vệ (V30)
        for (int i = 0; i < aggressive.size(); i++) {
            for (int j = i + 1; j < aggressive.size(); j++) {
                MonsterSpawnPoint first = aggressive.get(i);
                MonsterSpawnPoint second = aggressive.get(j);
                float reachOfBoth = aggroByCode.get(first.monsterCode()) + aggroByCode.get(second.monsterCode());
                assertThat(Math.abs(first.posX() - second.posX()))
                        .as(
                                "%s@%.0f và %s@%.0f phải cách nhau hơn %.0fm, nếu không đoạn giữa kéo cả hai",
                                first.monsterCode(), first.posX(), second.monsterCode(), second.posX(), reachOfBoth)
                        .isGreaterThan(reachOfBoth);
            }
        }
    }

    @Test
    void shouldKeepAiParamsUnchangedForEverySpeciesButTheTrainingDummy() {
        // ADR-039 D2 — ràng buộc CỨNG của V25: mỗi DEFAULT bằng ĐÚNG hằng số đang chạy trong
        // AiConstants, nên chạy migration xong game hành xử y hệt. Ô test này là thứ bắt được nếu
        // một đợt sau lỡ đổi DEFAULT mà quên đổi AiConstants (hoặc ngược lại).
        // `archive_warden` (V30) nằm trong danh sách này vì migration đó CỐ Ý không cho nó tính
        // cách AI riêng — cùng lý do V25 §4 đã ghi cho hai loài trước: đặt số chưa playtest cùng
        // lúc với việc thêm nội dung là trộn hai thứ phải kiểm riêng.
        for (String code : List.of("wandering_bandit", "stone_ogre", "archive_warden")) {
            MonsterAiParams ai = findByCode(code).aiParams();
            assertThat(ai.aggroRadiusMeters()).isEqualTo(6f);
            assertThat(ai.leashRadiusMeters()).isEqualTo(10f);
            assertThat(ai.chaseSpeedMps()).isEqualTo(3f);
            assertThat(ai.patrolSpeedMps()).isEqualTo(1.5f);
            assertThat(ai.patrolRadiusMeters()).isEqualTo(3f);
            assertThat(ai.idleDurationSeconds()).isEqualTo(3f);
            assertThat(ai.isPassive()).isFalse();
        }
    }

    @Test
    void shouldMakeTrainingDummyPassive() {
        // ADR-039 D4 — ngoại lệ có chủ đích DUY NHẤT của V25: cây "Mộc nhân luyện công" thôi rời
        // cọc đuổi theo người chơi 3 m/s. Gỡ được bằng một câu UPDATE nếu chủ dự án đổi ý.
        MonsterAiParams ai = findByCode("training_dummy").aiParams();

        assertThat(ai.isPassive()).isTrue();
        assertThat(ai.aggroRadiusMeters()).isZero();
        assertThat(ai.patrolRadiusMeters()).isZero();
        // Phần còn lại giữ mặc định: nó vẫn quay về được nếu vì lý do gì bị đẩy khỏi chỗ.
        assertThat(ai.chaseSpeedMps()).isEqualTo(3f);
        assertThat(ai.leashRadiusMeters()).isEqualTo(10f);
    }

    @Test
    void shouldRejectLeashSmallerThanAggro() throws SQLException {
        // ADR-039 D3 — bất biến mà một cặp HẰNG SỐ CHUNG không bao giờ vi phạm được, nhưng dữ liệu
        // thì chỉ cách một lần gõ sai: leash < aggro ⇒ quái dao động Chase/Return vô hạn.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                            "UPDATE monster_template SET leash_radius_meters = 1 WHERE code = 'stone_ogre'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("monster_template_ai_leash_covers_aggro");
        }
    }

    @Test
    void shouldReturnEmptyForWorldWithoutSpawnPoints() {
        // Dungeon/arena chưa khai điểm spawn nào — rỗng là hợp lệ, không phải lỗi.
        assertThat(new PostgresMonsterCatalogAdapter(dataSource).listSpawnPoints("dungeon"))
                .isEmpty();
    }

    private static MonsterTemplate findByCode(String code) {
        return new PostgresMonsterCatalogAdapter(dataSource)
                .listTemplates().stream()
                        .filter(template -> template.code().equals(code))
                        .findFirst()
                        .orElseThrow();
    }
}

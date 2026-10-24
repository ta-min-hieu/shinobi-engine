package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V36 (hai cột {@code use_effect}/
 * {@code use_value} + hàng {@code minor_healing_pill}) và cả HAI đường đọc của
 * {@link PostgresItemTemplateAdapter}: {@code findById} (đường gameplay) và {@code listCatalog}
 * (đường gửi cho client). ADR-046 D5.
 *
 * <p>Hai đường phải cùng nói một điều: trước ADR-046, {@code findById} cố ý chỉ đọc phần "mặc đồ"
 * còn {@code listCatalog} đọc đủ cột — nên một cột mới rất dễ chỉ được nối vào MỘT trong hai, và
 * hậu quả là nút "Dùng" hiện ra nhưng server bảo "không dùng được", hoặc ngược lại.
 */
@Testcontainers
class HealingPillIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final String PILL_CODE = "minor_healing_pill";
    private static final int HEAL_HP = 50;

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

    private static long pillTemplateId() {
        return new PostgresItemTemplateAdapter(dataSource)
                .findIdByCode(PILL_CODE)
                .orElseThrow();
    }

    @Test
    void shouldSeedThePill_asAStackableConsumableThatHealsFifty() {
        Optional<ItemTemplate> pill = new PostgresItemTemplateAdapter(dataSource).findById(pillTemplateId());

        assertThat(pill).isPresent();
        assertThat(pill.get().isUsable()).isTrue();
        assertThat(pill.get().useEffect()).isEqualTo(ItemUseEffect.HEAL_HP);
        assertThat(pill.get().useValue()).isEqualTo(HEAL_HP);
        assertThat(pill.get().maxStack()).isEqualTo(20);
        assertThat(pill.get().isEquippable()).isFalse();
    }

    @Test
    void shouldCarryTheUseEffectThroughTheCatalogPathToo() {
        List<ItemCatalogEntry> catalog = new PostgresItemTemplateAdapter(dataSource).listCatalog();

        assertThat(catalog)
                .filteredOn(entry -> PILL_CODE.equals(entry.code()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.useEffect()).isEqualTo(ItemUseEffect.HEAL_HP);
                    assertThat(entry.useValue()).isEqualTo(HEAL_HP);
                });
    }

    @Test
    void shouldLeaveEveryOtherItemUnusable() {
        // Cột mới KHÔNG được backfill giá trị giả cho các hàng cũ: trang bị và nguyên liệu phải
        // giữ nguyên "không dùng được", nếu không thì mọi món trong túi đều mọc nút "Dùng".
        List<ItemCatalogEntry> catalog = new PostgresItemTemplateAdapter(dataSource).listCatalog();

        // Linh Thú Đan (ADR-078) là món dùng được thứ hai — có hiệu ứng riêng, không phải backfill.
        assertThat(catalog)
                .filteredOn(entry -> !PILL_CODE.equals(entry.code())
                        && !"spirit_beast_pill".equals(entry.code())
                        && !"bag_expansion_pouch".equals(entry.code()))
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.useEffect()).isNull());
    }

    @Test
    void shouldSellTheHealingPillAtThePavilion() {
        // Bất biến của V21 §4 mà tool load-test dựa vào: món nào vào túi được thì bán được.
        assertThat(countRows("SELECT count(*) FROM shop_item si JOIN item_template it ON it.id = si.item_template_id"
                        + " WHERE it.code = '" + PILL_CODE + "'"))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectAnItemThatIsBothEquippableAndUsable() {
        // Hàng rào DB song song với compact constructor của ItemTemplate — dùng xong thì món biến
        // mất, mà nó đang nằm trên người thì đó là một ô trang bị rỗng không ai giải thích được.
        assertThatThrownBy(() -> execute("INSERT INTO item_template (code, name_key, max_stack, equip_slot,"
                        + " use_effect, use_value) VALUES ('bad_ring', 'x', 1, 'ACCESSORY_1', 'HEAL_HP', 10)"))
                .hasMessageContaining("item_template_usable_is_not_equippable");
    }

    @Test
    void shouldRejectAUsableItemWithNoValue() {
        assertThatThrownBy(() -> execute("INSERT INTO item_template (code, name_key, max_stack, use_effect,"
                        + " use_value) VALUES ('bad_pill', 'x', 1, 'HEAL_HP', 0)"))
                .hasMessageContaining("item_template_usable_needs_value");
    }

    private static void execute(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static int countRows(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}

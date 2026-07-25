package dev.kage.server.database.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.dialogue.application.GetDialogueNodeUseCase;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V10 ({@code npc_template}) +
 * {@link GetDialogueNodeUseCase} chạy ĐÚNG script sản phẩm thật ({@code town_elder.js}, seed sẵn
 * trong V10), không phải script test giả (khác {@code GetDialogueNodeUseCaseTest}).
 */
@Testcontainers
class DialogueIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

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

    private static GetDialogueNodeUseCase newUseCase() {
        return new GetDialogueNodeUseCase(new PostgresNpcTemplateAdapter(dataSource));
    }

    @Test
    void shouldReturnStartNode_forSeededTownElder() {
        Optional<DialogueNode> result = newUseCase().execute("town_elder", DialogueNode.START_NODE_ID);

        assertThat(result).isPresent();
        DialogueNode node = result.get();
        assertThat(node.textKey()).isEqualTo("dialogue.town_elder.greeting");
        assertThat(node.options()).extracting("action")
                .containsExactly("about_town", "accept_quest:kill_training_dummies", "open_shop", "end");
    }

    @Test
    void shouldReturnAboutTownNode_whenNavigating() {
        Optional<DialogueNode> result = newUseCase().execute("town_elder", "about_town");

        assertThat(result).isPresent();
        assertThat(result.get().textKey()).isEqualTo("dialogue.town_elder.about_town");
    }

    @Test
    void shouldReturnEmpty_forUnknownNpcCode() {
        Optional<DialogueNode> result = newUseCase().execute("no_such_npc", DialogueNode.START_NODE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldListDialogueScriptRef_forStartupValidation() {
        // SCRIPTING.md §8 — validate lúc khởi động cần liệt kê ĐÚNG dialogue_script_ref đang tham
        // chiếu thật trong DB (V10 seed), không phải danh sách tự khai trong code.
        List<String> scriptRefs = new PostgresNpcTemplateAdapter(dataSource).listAllDialogueScriptRefs();

        assertThat(scriptRefs).contains("/scripts/dialogue/town_elder.js");
    }
}

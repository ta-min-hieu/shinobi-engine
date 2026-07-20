package dev.kage.server.database;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginOrCreateAccountUseCase;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.inventory.JdbcInventoryTransactionRunner;
import dev.kage.server.database.inventory.PostgresDropTableAdapter;
import dev.kage.server.database.inventory.PostgresInventoryAdapter;
import dev.kage.server.database.session.RedisClientFactory;
import dev.kage.server.database.session.RedisConfig;
import dev.kage.server.database.session.RedisSessionAdapter;
import javax.sql.DataSource;
import redis.clients.jedis.JedisPool;

/**
 * Composition root — nơi DUY NHẤT lắp ráp Facade thật (Adapter + UseCase + TransactionRunner)
 * từ cấu hình môi trường. {@code server-gateway} chỉ gọi {@link #wireAccountFacade()}/{@link
 * #wireCharacterFacade()}, không bao giờ thấy {@link DataSource}/{@link JedisPool} trực tiếp
 * (Điều 23 — server-gateway không biết DB; gọi qua đây không vi phạm vì server-gateway không tự
 * import java.sql/Jedis).
 */
public final class ServerDatabaseBootstrap {

    private final DataSource dataSource;
    private final JedisPool jedisPool;

    public ServerDatabaseBootstrap() {
        this.dataSource = PostgresDataSourceFactory.create(DatabaseConfig.fromEnvironment());
        FlywayMigrator.migrate(dataSource);
        this.jedisPool = RedisClientFactory.create(RedisConfig.fromEnvironment());
    }

    public AccountFacade wireAccountFacade() {
        var accountPort = new PostgresAccountAdapter();
        var sessionPort = new RedisSessionAdapter(jedisPool);
        var transactionManager = new JdbcAccountTransactionRunner(dataSource);
        var useCase = new LoginOrCreateAccountUseCase(accountPort, sessionPort, transactionManager);
        return new AccountFacade(useCase);
    }

    public CharacterFacade wireCharacterFacade() {
        var characterPort = new PostgresCharacterAdapter(dataSource);
        var transactionRunner = new JdbcCharacterTransactionRunner(dataSource);
        var createUseCase = new CreateCharacterUseCase(characterPort, transactionRunner);
        var listUseCase = new ListCharactersUseCase(characterPort);
        var selectUseCase = new SelectCharacterUseCase(characterPort);
        return new CharacterFacade(createUseCase, listUseCase, selectUseCase, characterPort);
    }

    public InventoryFacade wireInventoryFacade() {
        var inventoryPort = new PostgresInventoryAdapter(dataSource);
        var dropTablePort = new PostgresDropTableAdapter(dataSource);
        var transactionRunner = new JdbcInventoryTransactionRunner(dataSource);
        var addItemUseCase = new AddItemUseCase(inventoryPort, transactionRunner);
        var grantLootUseCase = new GrantLootUseCase(dropTablePort, inventoryPort, transactionRunner);
        return new InventoryFacade(addItemUseCase, grantLootUseCase);
    }

    /** Đóng DB/Redis (SERVER.md §10 graceful shutdown, bước 5) — gọi sau khi tick/flush xong. */
    public void shutdown() {
        jedisPool.close();
        PostgresDataSourceFactory.close(dataSource);
    }
}

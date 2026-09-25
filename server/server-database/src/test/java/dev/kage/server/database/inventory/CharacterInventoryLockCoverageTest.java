package dev.kage.server.database.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import dev.kage.server.database.mail.PostgresMailInventoryGatewayAdapter;
import dev.kage.server.database.quest.PostgresQuestInventoryGrantAdapter;
import dev.kage.server.database.quest.PostgresQuestItemTakeAdapter;
import dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter;
import dev.kage.server.database.trade.PostgresTradeInventoryAdapter;

/**
 * Mọi adapter Postgres ghi túi đồ PHẢI tự ghi đè hàm khoá túi đồ của port (DATABASE.md §5.1).
 *
 * <p>Các port khai hàm khoá là {@code default} KHÔNG làm gì — chỉ để hàng chục fake ở test khỏi phải
 * sửa. Cái giá: adapter thật quên ghi đè vẫn biên dịch, và túi đồ lại vượt 20 ô trong im lặng. Test
 * này biến "quên" thành đỏ: {@code getDeclaredMethod} chỉ thấy hàm khai NGAY trên lớp đó, không thấy
 * bản {@code default} thừa kế từ interface.
 */
class CharacterInventoryLockCoverageTest {

    @Test
    void everyPostgresInventoryWriterShouldOverrideItsLockMethod() throws NoSuchMethodException {
        assertDeclaredOn(PostgresShopInventoryGatewayAdapter.class.getDeclaredMethod(
                "lockInventory", dev.kage.shop.domain.Transaction.class, long.class));
        assertDeclaredOn(PostgresMailInventoryGatewayAdapter.class.getDeclaredMethod(
                "lockInventory", dev.kage.mail.domain.Transaction.class, long.class));
        assertDeclaredOn(PostgresQuestInventoryGrantAdapter.class.getDeclaredMethod(
                "lockInventory", dev.kage.quest.domain.Transaction.class, long.class));
        assertDeclaredOn(PostgresQuestItemTakeAdapter.class.getDeclaredMethod(
                "lockInventory", dev.kage.quest.domain.Transaction.class, long.class));
        assertDeclaredOn(PostgresTradeInventoryAdapter.class.getDeclaredMethod(
                "lockInventories", dev.kage.trade.domain.Transaction.class, long.class, long.class));
        assertDeclaredOn(PostgresInventoryAdapter.class.getDeclaredMethod(
                "findByCharacterForUpdate", dev.kage.inventory.domain.Transaction.class, long.class));
    }

    /** Ghi đè thật là một hàm CỤ THỂ trên lớp adapter, không phải bản {@code default} của interface. */
    private static void assertDeclaredOn(Method method) {
        assertThat(method.getDeclaringClass().isInterface())
                .as(method.toString())
                .isFalse();
        assertThat(method.isDefault()).as(method.toString()).isFalse();
    }
}

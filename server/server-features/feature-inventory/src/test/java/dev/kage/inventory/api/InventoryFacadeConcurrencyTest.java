package dev.kage.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EnhancePorts;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GetItemCatalogUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.ListInventoryUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * ⚠️ Race THẬT tìm được 2026-09-20 — xem javadoc lớp {@link InventoryFacade} cho mô tả đầy đủ. Túi
 * tóm tắt: {@link GrantLootUseCase} đọc TOÀN BỘ {@code Inventory}, cộng đồ trong bộ nhớ, rồi lưu
 * lại CẢ CHỒNG; adapter thật ({@code PostgresInventoryAdapter}) coi dòng nào KHÔNG có trong chồng
 * vừa lưu là "đã gỡ" rồi XOÁ. Hai lần gọi {@code grantLoot} cho CÙNG nhân vật đọc trúng CÙNG một
 * bản chụp cũ (VD giết 2 quái gần nhau) → lần lưu SAU xoá mất phần lần lưu TRƯỚC vừa thêm.
 *
 * <p>{@link RacyReplaceOnSaveInventoryPort} mô phỏng ĐÚNG ngữ nghĩa "lưu = THAY THẾ toàn bộ chồng
 * cũ" của adapter thật bằng một cách đơn giản nhất có thể chứng minh được: {@code Map.put} thay
 * nguyên đối tượng {@code Inventory} — hai chồng khác nhau (đồ khác nhau) mà cùng ghi vào 1 khoá
 * thì cái ghi SAU thắng tuyệt đối, cái ghi TRƯỚC biến mất không dấu vết, đúng hệt {@code DELETE}
 * của adapter thật khi dòng đó không có mặt trong chồng mới.
 */
class InventoryFacadeConcurrencyTest {

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);
    private static final long CHARACTER_ID = 1L;

    /** Thay THẲNG bằng {@code Inventory} vừa lưu — không merge, không diff — đúng ngữ nghĩa thật. */
    static final class RacyReplaceOnSaveInventoryPort implements InventoryPort {
        private final ConcurrentHashMap<Long, Inventory> store = new ConcurrentHashMap<>();

        RacyReplaceOnSaveInventoryPort(Inventory initial) {
            store.put(initial.characterId(), initial);
        }

        // ⚠️ Trả BẢN SAO MỚI mỗi lần, không phải tham chiếu đang lưu — Inventory là CLASS MUTABLE
        // (add() sửa list nội bộ tại chỗ), và adapter Postgres thật LUÔN dựng Inventory MỚI từ
        // ResultSet mỗi lần đọc. Trả thẳng tham chiếu cũ ở đây sẽ khiến 2 luồng vô tình sửa CHUNG
        // một object (cả hai add() đều thắng, không món nào mất) — che mất race hoàn toàn, đúng
        // bẫy tự vấp lần đầu viết ô test này (2026-09-20): tưởng đã tái hiện được race mà thực ra
        // chỉ đang chia sẻ nhầm 1 instance.
        @Override
        public Optional<Inventory> findByCharacter(long characterId) {
            Inventory stored = store.get(characterId);
            return stored == null
                    ? Optional.empty()
                    : Optional.of(new Inventory(stored.characterId(), stored.capacity(), stored.items()));
        }

        @Override
        public Inventory save(Transaction tx, Inventory inventory) {
            store.put(inventory.characterId(), inventory);
            return inventory;
        }

        List<ItemStack> currentItems() {
            return store.get(CHARACTER_ID).items();
        }
    }

    private static ItemTemplatePort templatePortWithMaxStack(int maxStack) {
        return new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                return Optional.of(new ItemTemplate(itemTemplateId, null, maxStack, 0, 0, 0, 0, 1, null, 0));
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return Optional.empty();
            }
        };
    }

    private static DropTablePort alwaysDrops(long itemTemplateId) {
        List<DropTableEntry> entries = List.of(new DropTableEntry(itemTemplateId, 100, 1, 1));
        return dropTableCode -> entries;
    }

    /**
     * ⚠️ Đo trực tiếp {@link GrantLootUseCase}, KHÔNG qua {@link InventoryFacade} — đây là phép đo
     * cho CHÍNH LỖ HỔNG, không phải cho bản vá. Hai luồng cùng đợi ở {@link CyclicBarrier} SAU khi
     * đã đọc xong bản chụp gốc (rỗng) nhưng TRƯỚC khi lưu — buộc cả hai đọc TRÚNG CÙNG một trạng
     * thái, mô phỏng chính xác "giết 2 quái gần nhau, cả hai lootExecutor task đọc cùng lúc".
     */
    @Test
    @Timeout(10)
    void grantLootUseCaseAloneLosesOneDrop_whenTwoCallsReadTheSameSnapshot() throws Exception {
        RacyReplaceOnSaveInventoryPort port =
                new RacyReplaceOnSaveInventoryPort(new Inventory(CHARACTER_ID, 20, List.of()));
        CyclicBarrier bothHaveRolledLoot = new CyclicBarrier(2);
        GrantLootUseCase useCaseX = new GrantLootUseCase(
                alwaysDrops(100L),
                new BarrierGatedInventoryPort(port, bothHaveRolledLoot),
                templatePortWithMaxStack(1),
                DIRECT_TX);
        GrantLootUseCase useCaseY = new GrantLootUseCase(
                alwaysDrops(200L),
                new BarrierGatedInventoryPort(port, bothHaveRolledLoot),
                templatePortWithMaxStack(1),
                DIRECT_TX);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<ItemStack>> resultX = pool.submit(() -> useCaseX.execute(CHARACTER_ID, "dropX"));
            Future<List<ItemStack>> resultY = pool.submit(() -> useCaseY.execute(CHARACTER_ID, "dropY"));
            assertThat(resultX.get()).isNotEmpty();
            assertThat(resultY.get()).isNotEmpty();
        } finally {
            pool.shutdown();
        }

        // MỘT trong hai món đã BIẾN MẤT — đây chính là bug, không phải giả định. Cả hai use case
        // BÁO đã cấp đồ thành công (mỗi cái tự thấy granted của riêng nó), nhưng túi cuối cùng chỉ
        // còn giữ được ĐÚNG MỘT trong hai — cái lưu SAU xoá mất cái lưu TRƯỚC.
        assertThat(port.currentItems()).hasSize(1);
    }

    /**
     * {@code InventoryPort} bọc thêm 1 hàng rào ngay TRƯỚC khi gọi {@code save()} — 2 luồng gặp
     * nhau ở đây trước khi luồng nào được phép ghi, tái hiện đúng cửa sổ hở giữa đọc và ghi.
     */
    private record BarrierGatedInventoryPort(InventoryPort delegate, CyclicBarrier beforeSave)
            implements InventoryPort {
        @Override
        public Optional<Inventory> findByCharacter(long characterId) {
            return delegate.findByCharacter(characterId);
        }

        @Override
        public Inventory save(Transaction tx, Inventory inventory) {
            try {
                beforeSave.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Hàng rào test bị ngắt giữa chừng", e);
            } catch (BrokenBarrierException e) {
                throw new IllegalStateException("Hàng rào test vỡ — luồng kia lỗi trước khi tới đây", e);
            }
            return delegate.save(tx, inventory);
        }
    }

    /**
     * Cùng kịch bản ĐUA hệt bài trên, nhưng đi qua {@link InventoryFacade#grantLoot} thật — khoá
     * theo nhân vật ({@code withCharacterLock}) phải buộc lệnh thứ hai CHỜ lệnh thứ nhất lưu xong
     * rồi mới ĐỌC (không phải chỉ chờ tới lúc ghi — nếu chỉ khoá phần ghi thì vẫn đọc trúng bản
     * chụp cũ y hệt bug), nên KHÔNG còn cách nào để rơi vào cùng một bản chụp gốc nữa — CẢ HAI món
     * phải còn nguyên.
     */
    @Test
    @Timeout(10)
    void inventoryFacadeGrantLootKeepsBothDrops_whenTwoKillsRaceForTheSameCharacter() throws Exception {
        RacyReplaceOnSaveInventoryPort port =
                new RacyReplaceOnSaveInventoryPort(new Inventory(CHARACTER_ID, 20, List.of()));
        InventoryFacade facade = fullFacadeWith(port);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<ItemStack>> resultX = pool.submit(() -> facade.grantLoot(CHARACTER_ID, "dropX"));
            Future<List<ItemStack>> resultY = pool.submit(() -> facade.grantLoot(CHARACTER_ID, "dropY"));
            assertThat(resultX.get()).isNotEmpty();
            assertThat(resultY.get()).isNotEmpty();
        } finally {
            pool.shutdown();
        }

        // Khoá theo nhân vật buộc 2 lời gọi chạy TUẦN TỰ — không còn cửa sổ đọc-chung-bản-chụp-cũ,
        // nên CẢ HAI món phải còn nguyên trong túi, không món nào biến mất.
        assertThat(port.currentItems()).hasSize(2);
    }

    /**
     * Dựng {@link InventoryFacade} ĐẦY ĐỦ (8 use case) chỉ để test {@code grantLoot} — 7 use case
     * còn lại không bị gọi trong test này nên chỉ cần dựng ĐÚNG KIỂU, không cần ý nghĩa nghiệp vụ.
     * Vẫn dùng CHÍNH {@code port} truyền vào cho những use case có đọc {@code InventoryPort}, để
     * nếu sau này ai lỡ mở rộng test và gọi nhầm 1 trong 7 cái kia thì thấy lỗi RÕ thay vì im lặng
     * dùng dữ liệu khác.
     */
    private static InventoryFacade fullFacadeWith(InventoryPort port) {
        ItemTemplatePort templatePort = templatePortWithMaxStack(1);
        EquipmentPort equipmentPort = new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, java.util.Map.of()));
            }

            @Override
            public void save(Transaction tx, Equipment equipment) {
                throw new UnsupportedOperationException("không dùng ở test này");
            }
        };

        ItemAcquisitionUseCases acquisitionUseCases = new ItemAcquisitionUseCases(
                new AddItemUseCase(port, templatePort, DIRECT_TX),
                new GrantLootUseCase(alwaysDrops(999L), port, templatePort, DIRECT_TX));
        EquipmentUseCases equipmentUseCases = new EquipmentUseCases(
                new EquipItemUseCase(port, templatePort, equipmentPort, DIRECT_TX),
                new UnequipItemUseCase(equipmentPort, DIRECT_TX),
                new ComputeEquipmentBonusUseCase(equipmentPort, port, templatePort));
        ItemActionUseCases actionUseCases = new ItemActionUseCases(
                new EnhanceItemUseCase(
                        new EnhancePorts(
                                port,
                                (tx, characterId, amount) -> true,
                                (tx, characterId, action, detailJson) -> {},
                                templatePort,
                                (tx, idempotencyKey) -> {},
                                (tx, characterId, itemId) -> false),
                        DIRECT_TX),
                new UseItemUseCase(port, templatePort, (tx, characterId, action, detailJson) -> {}, DIRECT_TX));
        InventoryQueryUseCases queryUseCases = new InventoryQueryUseCases(
                new ListInventoryUseCase(port, equipmentPort), new GetItemCatalogUseCase(templatePort));

        return new InventoryFacade(acquisitionUseCases, equipmentUseCases, actionUseCases, queryUseCases);
    }
}

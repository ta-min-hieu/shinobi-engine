package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemCatalog;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;

/** ADR-035 Decision-2 (phương án A′) — catalog + {@code version} là hash NỘI DUNG do server tính. */
class GetItemCatalogUseCaseTest {

    private static final ItemCatalogEntry KUNAI = new ItemCatalogEntry(1L, "kunai", "item.kunai.name", 99, null, 0);
    private static final ItemCatalogEntry SWORD =
            new ItemCatalogEntry(2L, "wooden_sword", "item.wooden_sword.name", 1, EquipmentSlotType.WEAPON, 5);

    private static ItemTemplatePort fakePort(List<ItemCatalogEntry> entries, AtomicInteger readCount) {
        return new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                return Optional.empty();
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return Optional.empty();
            }

            @Override
            public List<ItemCatalogEntry> listCatalog() {
                readCount.incrementAndGet();
                return entries;
            }
        };
    }

    @Test
    void shouldReturnEveryEntryWithNonBlankVersion() {
        GetItemCatalogUseCase useCase = new GetItemCatalogUseCase(fakePort(List.of(SWORD, KUNAI), new AtomicInteger()));

        ItemCatalog catalog = useCase.execute();

        assertThat(catalog.version()).isNotBlank();
        // Sắp lại theo id, KHÔNG giữ thứ tự DB trả về — version không được đổi vì thứ tự đổi.
        assertThat(catalog.entries()).containsExactly(KUNAI, SWORD);
    }

    @Test
    void shouldReadDatabaseOnlyOnce_becauseItemTemplateIsStaticConfig() {
        AtomicInteger readCount = new AtomicInteger();
        GetItemCatalogUseCase useCase = new GetItemCatalogUseCase(fakePort(List.of(KUNAI), readCount));

        useCase.execute();
        useCase.execute();
        useCase.execute();

        assertThat(readCount.get()).isEqualTo(1);
    }

    @Test
    void shouldProduceSameVersion_forSameContentInDifferentOrder() {
        ItemCatalog ascending = ItemCatalog.of(List.of(KUNAI, SWORD));
        ItemCatalog descending = ItemCatalog.of(List.of(SWORD, KUNAI));

        // Thứ tự DB trả về không được ảnh hưởng version, nếu không client sẽ tải lại catalog vô cớ.
        assertThat(ascending.version()).isEqualTo(descending.version());
    }

    @Test
    void shouldChangeVersion_whenABalanceNumberChanges() {
        ItemCatalog before = ItemCatalog.of(List.of(SWORD));
        ItemCatalog after = ItemCatalog.of(List.of(
                new ItemCatalogEntry(2L, "wooden_sword", "item.wooden_sword.name", 1, EquipmentSlotType.WEAPON, 12)));

        // Đây chính là lý do chọn A′ thay vì đóng gói tĩnh: chỉnh cân bằng là version tự đổi, client
        // nhận số mới ở lần đăng nhập kế — không ai phải nhớ bump phiên bản bằng tay.
        assertThat(after.version()).isNotEqualTo(before.version());
    }

    @Test
    void shouldProduceDifferentVersion_whenFieldBoundaryShifts() {
        // Bẫy kinh điển của hash nối chuỗi: hai catalog khác nhau băm ra cùng chuỗi vì thiếu ký tự
        // phân tách. "ab"+"c" và "a"+"bc" phải ra hai version khác nhau.
        ItemCatalog first = ItemCatalog.of(List.of(new ItemCatalogEntry(1L, "ab", "c", 1, null, 0)));
        ItemCatalog second = ItemCatalog.of(List.of(new ItemCatalogEntry(1L, "a", "bc", 1, null, 0)));

        assertThat(first.version()).isNotEqualTo(second.version());
    }

    @Test
    void shouldHandleEmptyCatalog() {
        GetItemCatalogUseCase useCase = new GetItemCatalogUseCase(fakePort(List.of(), new AtomicInteger()));

        ItemCatalog catalog = useCase.execute();

        assertThat(catalog.entries()).isEmpty();
        assertThat(catalog.version()).isNotBlank(); // vẫn phải có version để client cache được "rỗng"
    }
}

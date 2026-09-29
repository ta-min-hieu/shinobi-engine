package dev.kage.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.application.EnhanceItemUseCase;

/** Bảng tra kết quả cường hoá của facade phải phủ HẾT enum của use case, cùng tên. */
class InventoryFacadeEnhanceResultTest {

    @Test
    void shouldMapEveryUseCaseResultToTheSameNamedFacadeResult() {
        for (EnhanceItemUseCase.EnhanceResult result : EnhanceItemUseCase.EnhanceResult.values()) {
            assertThat(InventoryFacade.toFacadeResult(result)).as(result.name()).isNotNull();
            assertThat(InventoryFacade.toFacadeResult(result).name()).isEqualTo(result.name());
        }
    }
}

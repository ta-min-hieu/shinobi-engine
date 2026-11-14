package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lô 54 phía client — tên người gửi bỏ tiền tố (ADR-364). */
class Lot54ClientTest {

    @Test
    void bareNameDropsTagAndTitle() {
        assertThat(ChatCoordinator.bareName("BaoDo01")).isEqualTo("BaoDo01");
        assertThat(ChatCoordinator.bareName("<KAGE> [Kiếm Tiên] BaoDo01")).isEqualTo("BaoDo01");
    }
}

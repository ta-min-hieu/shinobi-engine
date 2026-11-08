package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Lô 40 thuần client — lọc từ khoá (ADR-249), vị trí yêu thích (ADR-250), tìm trong túi (ADR-254), nhận mình (ADR-247).
 */
class Lot40bClientTest {

    @Test
    void keywordsAreFoldedDeduplicatedAndBounded() {
        assertThat(KeywordFilterCoordinator.parse("Bán Vàng\nban vang\nx\n  rác  "))
                .containsExactly("ban vang", "rac");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            many.append("tu").append(i).append('\n');
        }
        assertThat(KeywordFilterCoordinator.parse(many.toString())).hasSize(KeywordFilterCoordinator.MAX_WORDS);
    }

    @Test
    void bookmarksSkipBrokenLines() {
        Map<String, Float> parsed = LocationBookmarkCoordinator.parse("Lò rèn=-11.5\nhỏng\nTrạm=a\nCổng=83\n");
        assertThat(parsed).containsOnlyKeys("Lò rèn", "Cổng");
        assertThat(parsed.get("Lò rèn")).isEqualTo(-11.5f);
    }

    @Test
    void bagSearchMatchesWithoutAccents() {
        List<BagSearchCoordinator.Described> bag = List.of(
                new BagSearchCoordinator.Described(0, 15, "Tiểu Hồi Nguyên Đan"),
                new BagSearchCoordinator.Described(4, 1, "Kiếm Gỗ +2"),
                new BagSearchCoordinator.Described(7, 20, "Phi Đao"));
        assertThat(BagSearchCoordinator.search(bag, "hoi nguyen"))
                .extracting(BagSearchCoordinator.Described::slotIndex)
                .containsExactly(0);
        assertThat(BagSearchCoordinator.search(bag, "KIẾM")).hasSize(1);
        assertThat(BagSearchCoordinator.search(bag, " ")).isEmpty();
    }

    @Test
    void recognisesOwnNameEvenWithTitle() {
        MentionWatch watch = new MentionWatch();
        assertThat(watch.isSelf("ĐạoHữu01")).isFalse();
        watch.useOwnName("ĐạoHữu01");
        assertThat(watch.isSelf("[Kiếm Tiên] ĐạoHữu01")).isTrue();
        assertThat(watch.isSelf("BaoDo01")).isFalse();
    }
}

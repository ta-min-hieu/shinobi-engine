package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GuildRecruitmentView;
import dev.kage.protocol.PostGuildRecruitmentCommand;
import dev.kage.protocol.PostGuildRecruitmentOutcome;
import dev.kage.protocol.PostGuildRecruitmentResponse;

/** ADR-185 — cú pháp {@code /chieumo}, một dòng bảng, câu báo treo/gỡ. */
final class GuildRecruitmentCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void parsePostShouldReadPostAndClose() {
        PostGuildRecruitmentCommand post = GuildRecruitmentCoordinator.parsePost("dang 15 Tuyển đệ tử chăm chỉ")
                .orElseThrow();
        assertThat(post.getOpen()).isTrue();
        assertThat(post.getMinLevel()).isEqualTo(15);
        assertThat(post.getMessage()).isEqualTo("Tuyển đệ tử chăm chỉ");
        assertThat(GuildRecruitmentCoordinator.parsePost("go").orElseThrow().getOpen())
                .isFalse();
        assertThat(GuildRecruitmentCoordinator.parsePost("dang abc xyz")).isEmpty();
        assertThat(GuildRecruitmentCoordinator.parsePost("dang 15")).isEmpty();
    }

    @Test
    void rowAndOutcomesAreTranslated() {
        GuildRecruitmentView view = GuildRecruitmentView.newBuilder()
                .setGuildName("LinhKiemMon")
                .setGuildLevel(2)
                .setMembers(3)
                .setMaxMembers(50)
                .setMinLevel(15)
                .setMessage("Tuyển")
                .build();
        assertThat(GuildRecruitmentCoordinator.describe(KageI18n.load(), view))
                .contains("LinhKiemMon")
                .contains("3/50")
                .contains("15");
        for (PostGuildRecruitmentOutcome outcome : PostGuildRecruitmentOutcome.values()) {
            if (outcome == PostGuildRecruitmentOutcome.UNRECOGNIZED) {
                continue;
            }
            assertThat(GuildRecruitmentCoordinator.describe(
                            KageI18n.load(),
                            PostGuildRecruitmentResponse.newBuilder()
                                    .setOutcome(outcome)
                                    .build()))
                    .doesNotStartWith("guild_recruit.");
        }
    }
}

package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Chưa từng có test nào cho lớp này trước 2026-09-19 — lỗ hổng để lọt đúng con đường mà javadoc
 * lớp đã cảnh báo hai lần: NPC mới không được thêm vào {@code NPC_ASSET_KEYS} thì rơi thẳng vào
 * {@code character-player} mà không có gì đỏ lên. Test này khoá lại BA khoá NPC đã biết (đối chiếu
 * bảng {@code npc_template} thật 2026-09-19: {@code town_elder}, {@code senior_sister},
 * {@code waypost_attendant}) — không khoá được việc quên thêm khoá thứ tư, javadoc lớp đã nói rõ
 * giới hạn đó.
 */
class AssetCategoryResolverTest {

    @Test
    void classifiesAllThreeKnownNpcsAsNpcNotPlayer() {
        assertThat(AssetCategoryResolver.leafCategoryCode("char_town-elder")).isEqualTo("character-npc");
        assertThat(AssetCategoryResolver.leafCategoryCode("char_senior-sister")).isEqualTo("character-npc");
        // Bug thật 2026-09-19: khoá này bị bỏ sót từ lúc NPC thêm vào (V49), art_asset thật trong DB
        // mang category_id của character-player cho tới khi sửa ở cùng commit với test này.
        assertThat(AssetCategoryResolver.leafCategoryCode("char_waypost-attendant"))
                .isEqualTo("character-npc");
    }

    @Test
    void classifiesAnyOtherCharKeyAsPlayer() {
        assertThat(AssetCategoryResolver.leafCategoryCode("char_hoa-b1")).isEqualTo("character-player");
        assertThat(AssetCategoryResolver.leafCategoryCode("char_thuy-pilot")).isEqualTo("character-player");
    }

    @Test
    void classifiesByPrefixForNonCharacterAssets() {
        assertThat(AssetCategoryResolver.leafCategoryCode("mob_raider-chieftain"))
                .isEqualTo("monster-normal");
        assertThat(AssetCategoryResolver.leafCategoryCode("vfx_fireball")).isEqualTo("vfx-skill");
        assertThat(AssetCategoryResolver.leafCategoryCode("icon_kunai")).isEqualTo("icon-item");
    }

    @Test
    void rejectsAnUnknownPrefixInsteadOfGuessing() {
        assertThatThrownBy(() -> AssetCategoryResolver.leafCategoryCode("unknown_thing"))
                .isInstanceOf(AssetPipelineException.class);
    }

    @Test
    void derivesElementFromSuffixWhenPresent() {
        assertThat(AssetCategoryResolver.element("char_hoa-b1")).contains("HOA");
        assertThat(AssetCategoryResolver.element("char_thuy-pilot")).contains("THUY");
        assertThat(AssetCategoryResolver.element("vfx_loi_dien_giat")).contains("LOI");
    }

    @Test
    void hasNoElementWhenAssetKeyDoesNotCarryOne() {
        // NPC và icon/mob thường không mang tên hệ phái trong asset_key.
        assertThat(AssetCategoryResolver.element("char_town-elder")).isEmpty();
        assertThat(AssetCategoryResolver.element("mob_training-dummy")).isEmpty();
        assertThat(AssetCategoryResolver.element("icon_kunai")).isEmpty();
    }
}

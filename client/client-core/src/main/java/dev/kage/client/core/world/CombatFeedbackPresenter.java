package dev.kage.client.core.world;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.SkillResultEvent;

/**
 * Phản hồi TẠI CHỖ khi một đòn đánh/skill kết thúc: số damage bay lên
 * ({@link FloatingCombatText}) và hiệu ứng skill ({@link SkillVfxRenderer}), cả hai neo vào đúng vị
 * trí mục tiêu. Song sinh với {@link CombatAnimationTrigger} (phản hồi trên MODEL nhân vật) — tách
 * bạch cùng một kiểu: HUD, animation, và hiệu ứng tại chỗ là ba mối quan tâm khác nhau đọc chung
 * một packet.
 *
 * <p>Gộp hai thứ này lại vì chúng dùng CHUNG một phép tra khó: "entity id này đang ở đâu trong thế
 * giới" — mình (id dương khớp {@code localCharacterId}), quái (id âm, tra
 * {@link CombatHudController}), nhân vật đang là MỤC TIÊU (đối thủ đấu trường — vị trí lấy từ
 * {@link CombatHudController#targetPoint()}), hay một nhân vật khác (bỏ qua, không đoán). Trước khi
 * tách ra, phép tra đó nằm lặp hai bản trong {@code WorldScreen}.
 *
 * <p>KHÔNG sở hữu {@link BitmapFont} — font thuộc về {@code CombatHud}, ở đây chỉ mượn để vẽ.
 */
final class CombatFeedbackPresenter implements Disposable {

    // MEDIUM tường minh — bản cài đóng gói đúng 1 tier mặc định theo ADR-028, giống lý do
    // WorldModelRenderer của WorldScreen nhận tier tường minh chứ không có default ngầm.
    private static final GraphicsTier VFX_TIER = GraphicsTier.MEDIUM;

    private final long localCharacterId;
    private final PositionComponent localPosition;
    private final CombatHudController combat;
    private final SkillVfxRenderer skillVfx = new SkillVfxRenderer(VFX_TIER);
    private final FloatingCombatText floatingText;
    private BooleanSupplier damageNumbers = () -> true;
    private BooleanSupplier hideOthers = () -> false; // ADR-285
    private java.util.function.LongPredicate partyMember = id -> false; // ADR-285
    private final ComboCounter combo = new ComboCounter(); // ADR-293
    private BooleanSupplier comboOn = () -> false; // ADR-293
    private BooleanSupplier colorblind = () -> false; // ADR-307
    private BooleanSupplier compactNumbers = () -> false; // ADR-387

    /** ADR-307 — màu số sát thương PHẢI CHỊU ở chế độ mù màu: xanh dương, tách hẳn vàng của sát thương gây ra. */
    private static final Color DAMAGE_TAKEN_COLORBLIND = Color.valueOf("4AA3FF");
    // Bundle RIÊNG đúng quy ước KageI18n (mỗi nơi tự nạp, không dùng chung instance) — cần cho nhãn
    // "Né!" của số damage bay lên.
    private final I18NBundle i18n = KageI18n.load();

    /** ADR-136 — mã VFX đột phá cảnh giới (vùng atlas {@code vfx_dot_pha_impact}). */
    private static final String BREAKTHROUGH_VFX = "dot_pha";
    // Biến tạm TÁI DÙNG cho điểm neo — FloatingCombatText/SkillVfxRenderer đọc giá trị ngay trong
    // lời gọi nên an toàn (Điều 31).
    private final Vector3 anchor = new Vector3();

    /** ADR-358 — công tắc "giảm hiệu ứng người khác"; mặc định tắt (vẽ hết). */
    private java.util.function.BooleanSupplier hideOthersVfx = () -> false;

    /** ADR-350 — đòn đang hiện số là chí mạng (đặt ngay trước {@link #showNumber}, tránh thêm tham số thứ năm). */
    private boolean nextIsCrit;

    /**
     * @param localPosition {@link PositionComponent} của CHÍNH người chơi — giữ tham chiếu thẳng
     *     (không copy) là đúng: entity người chơi tạo một lần và không bao giờ đổi component, nên
     *     tham chiếu này luôn là vị trí hiện tại.
     */
    CombatFeedbackPresenter(
            long localCharacterId, PositionComponent localPosition, CombatHudController combat, BitmapFont font) {
        this.localCharacterId = localCharacterId;
        this.localPosition = localPosition;
        this.combat = combat;
        this.floatingText = new FloatingCombatText(font);
    }

    /** ADR-358 — nối công tắc giảm hiệu ứng kỹ năng của người khác. */
    void useOthersVfxFilter(java.util.function.BooleanSupplier hide) {
        this.hideOthersVfx = hide;
    }

    private boolean involvesMe(long attackerEntityId, long targetEntityId) {
        return attackerEntityId == localCharacterId || targetEntityId == localCharacterId;
    }

    /** Đòn đánh thường — chỉ có số damage, không có hiệu ứng riêng. */
    void onAttackResult(AttackResultEvent event) {
        if (resolveAnchor(event.getTargetEntityId())) {
            nextIsCrit = event.getIsCrit(); // ADR-350
            showNumber(event.getAttackerEntityId(), event.getTargetEntityId(), event.getDamage(), event.getIsMiss());
        }
    }

    /** Skill — hiệu ứng phát tại mục tiêu, kèm số damage như đòn thường. */
    void onSkillResult(SkillResultEvent event) {
        if (resolveAnchor(event.getTargetEntityId())) {
            if (!hideOthersVfx.getAsBoolean() || involvesMe(event.getAttackerEntityId(), event.getTargetEntityId())) {
                skillVfx.play(event.getSkillCode(), anchor.x, anchor.y, anchor.z); // ADR-358
            }
            nextIsCrit = event.getIsCrit(); // ADR-350
            showNumber(event.getAttackerEntityId(), event.getTargetEntityId(), event.getDamage(), event.getIsMiss());
        }
    }

    /**
     * Ghi vị trí neo của {@code targetEntityId} vào {@link #anchor}.
     *
     * @return {@code false} nếu không biết entity này (nhân vật khác ngoài tầm theo dõi) — bỏ qua
     *     cả số lẫn hiệu ứng, không đoán vị trí. Số của người khác cũng không phải thứ người chơi
     *     cần thấy.
     */
    private boolean resolveAnchor(long targetEntityId) {
        if (targetEntityId == localCharacterId) {
            anchor.set(localPosition.x, localPosition.y, localPosition.z);
            return true;
        }
        CombatHudController.MonsterState monster = combat.monsters().get(targetEntityId);
        if (monster != null) {
            anchor.set(monster.x(), monster.y(), 0f);
            return true;
        }
        // Nhân vật khác: chỉ khi đó là MỤC TIÊU đang nhắm (đối thủ đấu trường, 2026-10-04) — số của
        // những người khác vẫn không phải thứ người chơi cần thấy.
        if (targetEntityId <= 0L || targetEntityId != combat.currentTargetId()) {
            return false;
        }
        Optional<PickablePoint> character = combat.targetPoint();
        if (character.isEmpty()) {
            return false;
        }
        anchor.set(character.get().x(), character.get().y(), 0f);
        return true;
    }

    /**
     * Màu phân biệt NGAY không cần đọc số: vàng = mình gây ra, đỏ = mình phải chịu, xám = né.
     * {@link #anchor} phải vừa được {@link #resolveAnchor} ghi.
     */
    private void showNumber(long attackerEntityId, long targetEntityId, int damage, boolean miss) {
        if (hidesOthers(attackerEntityId, targetEntityId)) {
            return; // ADR-285 — số của người ngoài nhóm
        }
        if (miss) {
            floatingText.show(i18n.get("hud.combat.miss"), FloatingCombatText.MISS, anchor);
            return;
        }
        Color color = targetEntityId == localCharacterId ? takenColor() : FloatingCombatText.DAMAGE_DEALT;
        String amount = compactNumbers.getAsBoolean() ? CompactNumber.format(damage) : Integer.toString(damage);
        if (nextIsCrit) { // ADR-350 — chí mạng: to hơn, cam, có dấu "!"
            floatingText.show(
                    "-" + amount + "!",
                    targetEntityId == localCharacterId ? color : FloatingCombatText.CRIT,
                    anchor,
                    FloatingCombatText.CRIT_SCALE);
        } else {
            floatingText.show("-" + amount, color, anchor); // ADR-387 — có thể rút gọn "1.2k"
        }
        if (attackerEntityId == localCharacterId && targetEntityId != localCharacterId) {
            countCombo();
        }
    }

    /** ADR-293 — một đòn trúng của mình; từ đòn thứ {@link ComboCounter#SHOW_FROM} hiện chữ trên đầu nhân vật. */
    private void countCombo() {
        int hits = combo.hit();
        if (hits >= ComboCounter.SHOW_FROM && comboOn.getAsBoolean()) {
            showSelfText(KageI18n.formatOrKey(i18n, "combo.label", hits), FloatingCombatText.LEVEL_UP);
        }
    }

    private Color takenColor() {
        return colorblind.getAsBoolean() ? DAMAGE_TAKEN_COLORBLIND : FloatingCombatText.DAMAGE_TAKEN;
    }

    /** ADR-387 — công tắc rút gọn số sát thương. */
    void useCompactNumbers(BooleanSupplier enabled) {
        this.compactNumbers = enabled;
    }

    /** ADR-307 — công tắc chế độ mù màu. */
    void useColorblind(BooleanSupplier enabled) {
        this.colorblind = enabled;
    }

    /** ADR-293 — công tắc chữ combo. */
    void useCombo(BooleanSupplier enabled) {
        this.comboOn = enabled;
    }

    /**
     * Chữ bay lên ngay trên đầu CHÍNH người chơi — dùng cho những thứ không phải damage: mừng lên
     * cấp, và lời nhắc "vì sao đòn vừa bấm không xảy ra" (2026-08-09).
     *
     * <p>Neo vào người chơi chứ không phải góc màn hình: mắt người chơi đang ở nhân vật giữa lúc
     * đánh nhau, thông báo nằm ở rìa màn hình thì đọc xong đã muộn.
     *
     * @param i18nKey khoá i18n, KHÔNG phải chuỗi hiển thị (client/CLAUDE.md — cấm hardcode chuỗi).
     */
    void showSelfNotice(String i18nKey, Color color) {
        anchor.set(localPosition.x, localPosition.y, localPosition.z);
        floatingText.show(i18n.get(i18nKey), color, anchor);
    }

    /**
     * Như {@link #showSelfNotice} nhưng nhận chuỗi ĐÃ dựng sẵn.
     *
     * <p>Không phá luật "cấm hardcode chuỗi hiển thị": dùng cho những dòng mà phần chữ đến từ DỮ
     * LIỆU server chứ không phải từ code — mã vật phẩm + số lượng của phần thưởng nhiệm vụ
     * ({@code kunai x5}). Không có khoá i18n nào dịch được một mã do server đặt ra lúc chạy; bảng
     * tên vật phẩm là việc của túi đồ, thứ client còn chưa có.
     */
    void showSelfText(String text, Color color) {
        anchor.set(localPosition.x, localPosition.y, localPosition.z);
        floatingText.show(text, color, anchor);
    }

    /** Như {@link #showSelfText} nhưng neo ở một điểm thế giới bất kỳ (biểu cảm của người khác — ADR-092). */
    void showTextAt(String text, Color color, float x, float y) {
        anchor.set(x, y, 0f);
        floatingText.show(text, color, anchor);
    }

    /**
     * Vừa lên cấp — {@code leveled_up} của {@code ExperienceChangedEvent}. ADR-136: cấp mới mở một CẢNH GIỚI mới
     * (Trúc Cơ, Tử Phủ…) thì đó là đột phá — phát VFX {@code vfx_dot_pha} trên chính mình và báo tên cảnh giới thay
     * chữ lên cấp thường. Cảnh giới suy bằng {@link CultivationRealm} (hiển thị thuần, không phải luật gameplay).
     */
    void showLevelUp(int level) {
        CultivationRealm reached = CultivationRealm.of(level);
        if (reached.nameKey().equals(CultivationRealm.of(level - 1).nameKey())) {
            showSelfNotice("hud.exp.level_up", FloatingCombatText.LEVEL_UP);
            return;
        }
        skillVfx.play(BREAKTHROUGH_VFX, localPosition.x, localPosition.y, localPosition.z);
        showSelfText(
                KageI18n.formatOrKey(i18n, "hud.exp.breakthrough", i18n.get(reached.nameKey())),
                FloatingCombatText.LEVEL_UP);
    }

    void resize(int width, int height) {
        floatingText.resize(width, height);
    }

    /**
     * @param projection ma trận của nhánh render đang hoạt động — hiệu ứng vẽ TRONG không gian thế
     *     giới.
     * @param projector cùng camera đó nhưng để chiếu ra màn hình — số damage vẽ ở không gian màn
     *     hình cho chữ sắc nét (xem {@link FloatingCombatText}).
     */
    void render(float deltaTime, Matrix4 projection, WorldToScreen projector) {
        combo.tick(deltaTime); // ADR-293
        skillVfx.render(deltaTime, projection);
        if (damageNumbers.getAsBoolean()) {
            floatingText.render(deltaTime, projector);
        }
    }

    /** ADR-143 — công tắc "chữ damage"; tắt thì số bay lên không vẽ (hiệu ứng skill vẫn vẽ). */
    void useSwitch(BooleanSupplier damageNumbersOn) {
        this.damageNumbers = damageNumbersOn;
    }

    /**
     * ADR-285 — ẩn số sát thương của người ngoài nhóm: công tắc + "người này là phe mình" (mình đã tính sẵn; thành viên
     * nhóm do bảng sát thương nhóm ADR-251 biết).
     */
    void useOthersFilter(BooleanSupplier hideOthersOn, java.util.function.LongPredicate partyMember) {
        this.hideOthers = hideOthersOn;
        this.partyMember = partyMember;
    }

    /** Đòn do người ngoài nhóm đánh lên thứ không phải mình, và công tắc đang bật. */
    private boolean hidesOthers(long attackerEntityId, long targetEntityId) {
        return hideOthers.getAsBoolean()
                && attackerEntityId != localCharacterId
                && targetEntityId != localCharacterId
                && !partyMember.test(attackerEntityId);
    }

    @Override
    public void dispose() {
        skillVfx.dispose();
        floatingText.dispose(); // KHÔNG dispose font — thuộc về CombatHud, xem javadoc lớp.
    }
}

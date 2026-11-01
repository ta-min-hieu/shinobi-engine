package dev.kage.client.core.world;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListZoneMapResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.ZoneRange;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Biển vùng PvP (ADR-141, phần D4 treo của ADR-137) — hỏi bản đồ vùng một lần khi vào world (338/339), rồi mỗi khung so
 * vị trí của chính mình với các dải: trong dải PvP mà ngoài mọi vùng an toàn ⇒ biển "Vùng PvP" thường trực trên HUD và
 * một dòng hệ thống lúc bước vào/ra.
 *
 * <p>Chỉ HIỂN THỊ: server vẫn phán quyết mọi đòn bằng luật ADR-137. Lệch vài chục cm ở mép dải (vị trí client so với
 * vị trí server) chỉ làm biển bật sớm/muộn một chút. Không cấp phát mỗi khung — dải giữ trong mảng float.
 */
final class ZoneMapCoordinator implements WindowFlow {

    /** Trạng thái vùng của chính mình. */
    enum ZoneState {
        /** Ngoài mọi dải PvP (hoặc không ở world chính). */
        OPEN,
        /** Trong dải PvP nhưng đứng trong một vùng an toàn — không ai đánh được. */
        SAFE,
        /** Trong dải PvP, ngoài vùng an toàn — người khác đánh được mình. */
        PVP
    }

    /** Đỏ son — màu cảnh báo; chữ đã nói đủ nên màu không phải thông tin duy nhất. */
    private static final Color PVP_COLOR = Color.valueOf("FF6B5BFF");

    private static final Color SAFE_COLOR = Color.valueOf("8FD9C0FF");

    private static final float[] NONE = new float[0];

    /** Trần số dải mỗi loại — bản đồ thật có vài chục; gói lớn hơn bị cắt (luật gói repeated mới, fuzz 100k). */
    private static final int MAX_RANGES = 64;

    private final Runnable requestMap;
    private final Consumer<String> notice;
    private final UnaryOperator<String> text;
    private BiConsumer<String, Color> banner = (text, color) -> {};
    private float[] pvpRanges = NONE;
    private float[] safeRanges = NONE;
    private boolean inMainWorld = true;
    private ZoneState state = ZoneState.OPEN;

    /**
     * @param requestMap gửi {@code ListZoneMapRequest} ({@code WorldCommandSender#sendReadRequest})
     * @param notice in một dòng hệ thống lúc bước vào/ra dải ({@code ChatCoordinator#showSystemNotice})
     */
    ZoneMapCoordinator(Runnable requestMap, Consumer<String> notice) {
        this(requestMap, notice, i18nText(KageI18n.load()));
    }

    /** Cho test: {@code text} dịch khoá i18n (test không có libGDX để nạp bundle). */
    ZoneMapCoordinator(Runnable requestMap, Consumer<String> notice, UnaryOperator<String> text) {
        this.requestMap = requestMap;
        this.notice = notice;
        this.text = text;
    }

    private static UnaryOperator<String> i18nText(I18NBundle i18n) {
        return key -> KageI18n.textOrKey(i18n, key);
    }

    /**
     * Hỏi bản đồ vùng — {@code WorldScreen.show()} gọi, cùng chỗ và cùng lý do với {@code QuestCoordinator#requestLog}:
     * client không bao giờ tự gửi lệnh từ một khung vẽ ({@code InboundPacketFuzzTest}).
     */
    void request() {
        requestMap.run();
    }

    /** Nơi vẽ biển ({@code CombatHud#setZoneBanner}) — chỉ gọi khi trạng thái đổi. */
    void useBanner(BiConsumer<String, Color> sink) {
        this.banner = sink;
    }

    /** Gộp với một điều kiện nhắm người khác (bang chiến): đúng khi một trong hai đúng — cho {@code DuelTargeting}. */
    BooleanSupplier orInPvpZone(BooleanSupplier other) {
        return () -> other.getAsBoolean() || state == ZoneState.PVP;
    }

    ZoneState state() {
        return state;
    }

    /** Mỗi khung với hoành độ của chính mình. */
    void update(float localX) {
        ZoneState now = stateAt(localX);
        if (now == state) {
            return;
        }
        ZoneState before = state;
        state = now;
        showBanner();
        announce(before, now);
    }

    private ZoneState stateAt(float x) {
        if (!inMainWorld || !inside(pvpRanges, x)) {
            return ZoneState.OPEN;
        }
        return inside(safeRanges, x) ? ZoneState.SAFE : ZoneState.PVP;
    }

    private void showBanner() {
        switch (state) {
            case PVP -> banner.accept(text.apply("zone.hud.pvp"), PVP_COLOR);
            case SAFE -> banner.accept(text.apply("zone.hud.safe"), SAFE_COLOR);
            default -> banner.accept("", Color.WHITE);
        }
    }

    private void announce(ZoneState before, ZoneState now) {
        if (now == ZoneState.PVP) {
            notice.accept(text.apply("zone.notice.enter_pvp"));
        } else if (before == ZoneState.PVP) {
            notice.accept(text.apply("zone.notice.leave_pvp"));
        }
    }

    private static boolean inside(float[] ranges, float x) {
        for (int i = 0; i < ranges.length; i += 2) {
            if (x >= ranges[i] && x <= ranges[i + 1]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_LIST_ZONE_MAP_RESPONSE_VALUE) {
            ListZoneMapResponse map = ListZoneMapResponse.parseFrom(frame.payload());
            pvpRanges = flatten(map.getPvpRangesList());
            safeRanges = flatten(map.getSafeRangesList());
            return true;
        }
        if (opcode == Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE) {
            // Không nhận gói — luồng khác vẫn xử lý nó (PanelHub hỏi mọi luồng).
            inMainWorld = WorldChangedEvent.parseFrom(frame.payload()).getWorldKind() == WorldKind.WORLD_KIND_MAIN;
        }
        return false;
    }

    /** Dải → mảng phẳng [min0, max0, min1, max1…]; dải ngược (min > max) bỏ qua — gói hỏng không được làm sai biển. */
    private static float[] flatten(List<ZoneRange> ranges) {
        int count = Math.min(ranges.size(), MAX_RANGES);
        float[] flat = new float[count * 2];
        int n = 0;
        for (int i = 0; i < count; i++) {
            ZoneRange range = ranges.get(i);
            if (range.getMinX() <= range.getMaxX()) {
                flat[n++] = range.getMinX();
                flat[n++] = range.getMaxX();
            }
        }
        return n == flat.length ? flat : Arrays.copyOf(flat, n);
    }

    @Override
    public void reset() {
        pvpRanges = NONE;
        safeRanges = NONE;
        inMainWorld = true;
        state = ZoneState.OPEN;
        banner.accept("", Color.WHITE);
    }
}

package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ListZoneMapResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.ZoneRange;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/** ADR-141 — biển vùng PvP: hỏi bản đồ một lần, đổi trạng thái theo vị trí, vùng an toàn thắng dải PvP. */
class ZoneMapCoordinatorTest {

    private final List<String> notices = new ArrayList<>();
    private final List<String> banners = new ArrayList<>();
    private int requests;
    private ZoneMapCoordinator zones;

    @BeforeEach
    void setUp() throws InvalidProtocolBufferException {
        zones = new ZoneMapCoordinator(() -> requests++, notices::add, key -> key);
        zones.useBanner((text, color) -> banners.add(text));
        zones.dispatch(frame(
                Opcode.OPCODE_LIST_ZONE_MAP_RESPONSE_VALUE,
                ListZoneMapResponse.newBuilder()
                        .addPvpRanges(range(307f, 417f))
                        .addSafeRanges(range(365.5f, 370.5f))
                        .build()));
    }

    @Test
    void shouldRequestTheMapOnlyWhenAsked_neverFromAFrame() {
        zones.update(0f);
        zones.update(320f);
        zones.request();

        assertThat(requests).isEqualTo(1);
    }

    @Test
    void shouldWarnOnEnteringAndLeavingTheOpenStrip() {
        zones.update(300f);
        zones.update(320f);
        zones.update(320.5f);
        zones.update(300f);

        assertThat(notices).containsExactly("zone.notice.enter_pvp", "zone.notice.leave_pvp");
        assertThat(banners).hasSize(2);
        assertThat(banners.get(1)).isEmpty();
        assertThat(zones.state()).isEqualTo(ZoneMapCoordinator.ZoneState.OPEN);
    }

    @Test
    void shouldTreatASafePostInsideTheStripAsSafe() {
        zones.update(368f);

        assertThat(zones.state()).isEqualTo(ZoneMapCoordinator.ZoneState.SAFE);
        assertThat(zones.orInPvpZone(() -> false).getAsBoolean()).isFalse();
    }

    @Test
    void shouldAllowClickTargetingOnlyInsideTheOpenStrip() {
        zones.update(320f);

        assertThat(zones.orInPvpZone(() -> false).getAsBoolean()).isTrue();
    }

    @Test
    void shouldIgnoreTheStripOutsideTheMainWorld() throws InvalidProtocolBufferException {
        zones.dispatch(frame(
                Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE,
                WorldChangedEvent.newBuilder()
                        .setWorldKind(WorldKind.WORLD_KIND_DUNGEON)
                        .build()));
        zones.update(320f);

        assertThat(zones.state()).isEqualTo(ZoneMapCoordinator.ZoneState.OPEN);
    }

    @Test
    void shouldDropInvertedRanges() throws InvalidProtocolBufferException {
        zones.dispatch(frame(
                Opcode.OPCODE_LIST_ZONE_MAP_RESPONSE_VALUE,
                ListZoneMapResponse.newBuilder().addPvpRanges(range(50f, 10f)).build()));
        zones.update(30f);

        assertThat(zones.state()).isEqualTo(ZoneMapCoordinator.ZoneState.OPEN);
    }

    private static ZoneRange range(float min, float max) {
        return ZoneRange.newBuilder().setCode("z").setMinX(min).setMaxX(max).build();
    }

    private static PacketCodec.Frame frame(int opcode, MessageLite message) {
        return new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 0), message.toByteArray());
    }
}

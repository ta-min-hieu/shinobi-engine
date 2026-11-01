package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.WeatherEvent;
import dev.kage.protocol.WeatherKind;
import dev.kage.protocol.WeatherZone;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thời tiết theo vùng (ADR-144) — nhận gói 340 (server gửi lặp mỗi 30 giây: vùng nào đang có thời tiết), mỗi khung so
 * vị trí của chính mình với các vùng rồi bảo {@link WeatherOverlay} vẽ. Không cửa sổ, không gửi lệnh nào. Ngoài world
 * chính (hầm ngục/đấu trường sau gói 308) thì trời quang.
 */
final class WeatherCoordinator implements WindowFlow {

    /** Trần số vùng giữ lại — bản đồ thật có vài vùng; gói lớn hơn bị cắt (luật gói repeated mới). */
    private static final int MAX_ZONES = 32;

    private final WeatherOverlay overlay = new WeatherOverlay();
    private final float[] minX = new float[MAX_ZONES];
    private final float[] maxX = new float[MAX_ZONES];
    private final WeatherKind[] kinds = new WeatherKind[MAX_ZONES];
    private int zoneCount;
    private boolean inMainWorld = true;

    WeatherOverlay overlay() {
        return overlay;
    }

    /** Mỗi khung: chọn thời tiết nơi {@code localX}, cập nhật và vẽ lớp thời tiết. Không cấp phát. */
    void render(ShapeRenderer shapeRenderer, float localX, float deltaTime) {
        overlay.setTarget(kindAt(localX));
        overlay.update(deltaTime);
        overlay.render(shapeRenderer, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    WeatherKind kindAt(float x) {
        if (!inMainWorld) {
            return WeatherKind.WEATHER_KIND_UNSPECIFIED;
        }
        for (int i = 0; i < zoneCount; i++) {
            if (x >= minX[i] && x <= maxX[i]) {
                return kinds[i];
            }
        }
        return WeatherKind.WEATHER_KIND_UNSPECIFIED;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_WEATHER_EVENT_VALUE) {
            store(WeatherEvent.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE) {
            // Không nhận gói — luồng khác vẫn xử lý nó (PanelHub hỏi mọi luồng).
            inMainWorld = WorldChangedEvent.parseFrom(frame.payload()).getWorldKind() == WorldKind.WORLD_KIND_MAIN;
        }
        return false;
    }

    /** Vùng ngược (min > max) hoặc loại lạ bị bỏ — gói hỏng không được vẽ sai trời. */
    private void store(WeatherEvent event) {
        int n = 0;
        for (WeatherZone zone : event.getZonesList()) {
            if (n == MAX_ZONES) {
                break;
            }
            boolean known = zone.getKind() == WeatherKind.WEATHER_KIND_SPIRIT_RAIN
                    || zone.getKind() == WeatherKind.WEATHER_KIND_SANDSTORM;
            if (known && zone.getMinX() <= zone.getMaxX()) {
                minX[n] = zone.getMinX();
                maxX[n] = zone.getMaxX();
                kinds[n] = zone.getKind();
                n++;
            }
        }
        zoneCount = n;
    }

    @Override
    public void reset() {
        zoneCount = 0;
        inMainWorld = true;
        overlay.setTarget(WeatherKind.WEATHER_KIND_UNSPECIFIED);
    }
}

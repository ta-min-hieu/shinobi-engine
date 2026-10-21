package dev.kage.server.gateway;

import java.util.List;

import dev.kage.gm.domain.GameEvent;
import dev.kage.protocol.GameEventType;
import dev.kage.protocol.GameEventView;
import dev.kage.protocol.GameEventsChangedEvent;

/** Dịch sự kiện nhân đôi (ADR-067) sang gói {@code GameEventsChangedEvent}. */
final class GameEventNotices {

    private GameEventNotices() {}

    static GameEventsChangedEvent of(List<GameEvent> events) {
        GameEventsChangedEvent.Builder builder = GameEventsChangedEvent.newBuilder();
        for (GameEvent event : events) {
            builder.addEvents(GameEventView.newBuilder()
                    .setType(GameEventType.valueOf(
                            "GAME_EVENT_TYPE_" + event.kind().name()))
                    .setMultiplier(event.multiplier())
                    .setEndsAtEpochSeconds(event.endsAt().getEpochSecond())
                    .build());
        }
        return builder.build();
    }
}

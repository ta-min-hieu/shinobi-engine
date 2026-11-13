package dev.kage.client.core.world;

import java.util.Map;

/**
 * ADR-092 — chữ biểu cảm trên đầu chính mình hoặc người đang thấy; người ngoài tầm nhìn thì bỏ qua. Tách khỏi
 * {@code WorldScreen} vì lớp đó đang ở trần độ phức tạp PMD.
 */
final class EmoteBubbles implements EmoteCoordinator.EmoteDisplay {

    private final long localCharacterId;
    private final CombatFeedbackPresenter feedback;
    private final Map<Long, ? extends PickablePoint> others;

    /** @param others vị trí người khác đang thấy — bản đồ SỐNG của {@code RemoteCharacterPresenter}, không chép */
    EmoteBubbles(long localCharacterId, CombatFeedbackPresenter feedback, Map<Long, ? extends PickablePoint> others) {
        this.localCharacterId = localCharacterId;
        this.feedback = feedback;
        this.others = others;
    }

    @Override
    public int visibleOthers() {
        return others.size();
    }

    @Override
    public void show(long characterId, String text) {
        if (characterId == localCharacterId) {
            feedback.showSelfText(text, FloatingCombatText.EMOTE);
            return;
        }
        PickablePoint point = others.get(characterId);
        if (point != null) {
            feedback.showTextAt(text, FloatingCombatText.EMOTE, point.x(), point.y());
        }
    }
}

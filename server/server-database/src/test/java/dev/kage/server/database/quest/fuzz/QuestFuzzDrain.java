package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.quest.domain.QuestStatus;

/**
 * Bất biến SỐNG: không có trạng thái kẹt. Dự án không có lệnh bỏ quest, nên mọi quest đang làm PHẢI
 * đi tới được lĩnh thưởng bằng đúng việc mục tiêu của nó đòi — dù chuỗi lệnh ngẫu nhiên trước đó
 * (song song, túi đầy, đăng nhập lại, bán đồ) đã để nó ở trạng thái nào — và lĩnh xong thì quest kế
 * tiếp của chuỗi phải nhận được.
 */
final class QuestFuzzDrain {

    private final QuestFuzzRun run;

    QuestFuzzDrain(QuestFuzzRun run) {
        this.run = run;
    }

    void finishEverything() {
        run.emptyJunk();
        List<String> open = new ArrayList<>();
        for (Map.Entry<String, QuestFuzzModel.QuestState> entry :
                run.model().quests().entrySet()) {
            QuestStatus status = entry.getValue().status();
            if (status == QuestStatus.IN_PROGRESS || status == QuestStatus.COMPLETED) {
                open.add(entry.getKey());
            }
        }
        for (String code : open) {
            finish(code);
        }
        Optional<String> next = run.model().nextAcceptable();
        next.ifPresent(code -> {
            run.sequential(new QuestFuzzOp.Accept(code));
            if (!run.statusOf(code).equals(Optional.of(QuestStatus.IN_PROGRESS))) {
                run.flag("hết quest dở mà quest kế tiếp " + code + " vẫn không nhận được");
            }
        });
    }

    private void finish(String code) {
        if (isInProgress(code)) {
            doObjectives(code, run.content().spec(code));
        }
        if (!run.statusOf(code).equals(Optional.of(QuestStatus.COMPLETED))) {
            run.flag(code + " KẸT: đã làm đủ mục tiêu mà vẫn " + run.statusOf(code));
            return;
        }
        if (run.checker().bagRows() + QuestFuzzRun.MAX_SLOTS_PER_CLAIM > QuestFuzzChecker.BAG_CAPACITY) {
            run.clearWholeBag(); // người chơi bán bớt đồ — thứ duy nhất họ làm được khi túi đầy
        }
        run.sequential(new QuestFuzzOp.Claim(code, QuestFuzzGenerator.freshKey()));
        if (!run.statusOf(code).equals(Optional.of(QuestStatus.CLAIMED))) {
            run.flag(code + " KẸT: COMPLETED, túi trống, khoá mới mà vẫn không lĩnh được");
        }
    }

    /** Làm đúng và đủ thứ mục tiêu đòi: mang đồ, hỏi trước (nếu có), hạ đủ số, rồi báo cáo. */
    private void doObjectives(String code, QuestFuzzSpec spec) {
        if (run.checker().bagRows() + spec.deliveries().size() > QuestFuzzChecker.BAG_CAPACITY) {
            run.clearWholeBag();
        }
        for (QuestFuzzSpec.ItemTake take : spec.deliveries()) {
            run.giveItem(take.itemCode(), take.quantity());
        }
        if (spec.hasAskStep()) {
            run.sequential(new QuestFuzzOp.Talk(spec.askNpc()));
        }
        for (int i = 0; i < spec.kills() && isInProgress(code); i++) {
            run.sequential(new QuestFuzzOp.Kill(spec.monster()));
        }
        if (!spec.completesOnKill() && isInProgress(code)) {
            run.sequential(new QuestFuzzOp.Talk(spec.reportNpc()));
        }
    }

    private boolean isInProgress(String code) {
        return run.statusOf(code).equals(Optional.of(QuestStatus.IN_PROGRESS));
    }
}

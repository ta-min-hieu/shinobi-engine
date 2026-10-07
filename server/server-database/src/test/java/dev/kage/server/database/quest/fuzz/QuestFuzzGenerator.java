package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import dev.kage.quest.domain.QuestStatus;

/**
 * Sinh lệnh ngẫu nhiên NGHIÊNG về quest đang làm — fuzz đều tay trên 20 loài quái và 3 NPC thì gần
 * như không quest nào đi tới được bước lĩnh thưởng. Khoảng một phần ba vẫn là lệnh "lạc": loài không
 * liên quan, NPC bịa, mã quest bịa, chỉ số lựa chọn ngoài khoảng, khoá idempotency đã đốt.
 */
final class QuestFuzzGenerator {

    private static final String BOGUS = "fuzz_no_such_code";
    private static final int PERCENT = 100;

    private final Random random;
    private final QuestFuzzContent content;
    private final QuestFuzzModel model;

    QuestFuzzGenerator(Random random, QuestFuzzContent content, QuestFuzzModel model) {
        this.random = random;
        this.content = content;
        this.model = model;
    }

    /** Một lệnh bất kỳ trong năm loại, theo tỉ lệ cố định. */
    QuestFuzzOp anyOp() {
        int roll = random.nextInt(PERCENT);
        if (roll < 30) {
            return kill();
        }
        if (roll < 55) {
            return talk();
        }
        if (roll < 70) {
            return dialogue();
        }
        if (roll < 78) {
            return accept();
        }
        return claim();
    }

    QuestFuzzOp kill() {
        List<String> active = model.activeCodes();
        if (!active.isEmpty() && chance(70)) {
            QuestFuzzSpec spec = content.spec(pick(active));
            if (!spec.monster().isEmpty()) {
                return new QuestFuzzOp.Kill(spec.monster());
            }
        }
        return new QuestFuzzOp.Kill(chance(5) ? BOGUS : pick(content.monsters()));
    }

    QuestFuzzOp talk() {
        return new QuestFuzzOp.Talk(npc());
    }

    private String npc() {
        List<String> active = model.activeCodes();
        if (!active.isEmpty() && chance(70)) {
            QuestFuzzSpec spec = content.spec(pick(active));
            if (spec.hasAskStep() && chance(50)) {
                return spec.askNpc();
            }
            if (!spec.reportNpc().isEmpty()) {
                return spec.reportNpc();
            }
        }
        return chance(5) ? BOGUS : pick(content.npcs());
    }

    /** Đi đúng đường tới lời mời quest kế tiếp của chuỗi, hoặc bấm bừa. */
    QuestFuzzOp dialogue() {
        Optional<QuestFuzzContent.DialoguePath> path = model.nextAcceptable().flatMap(content::acceptPath);
        if (path.isPresent() && chance(75)) {
            List<Integer> indexes = path.get().steps().stream()
                    .map(QuestFuzzContent.DialogueStep::optionIndex)
                    .toList();
            return new QuestFuzzOp.Dialogue(path.get().npcCode(), indexes);
        }
        List<Integer> indexes = new ArrayList<>();
        int depth = 1 + random.nextInt(4);
        for (int i = 0; i < depth; i++) {
            indexes.add(random.nextInt(8) - 1);
        }
        return new QuestFuzzOp.Dialogue(chance(5) ? BOGUS : pick(content.npcs()), indexes);
    }

    QuestFuzzOp accept() {
        int roll = random.nextInt(PERCENT);
        if (roll < 50) {
            return new QuestFuzzOp.Accept(model.nextAcceptable().orElse(QuestFuzzSpec.FIXTURE_DELIVERY.code()));
        }
        if (roll < 70) {
            return new QuestFuzzOp.Accept(QuestFuzzSpec.FIXTURE_DELIVERY.code());
        }
        return new QuestFuzzOp.Accept(roll < 90 ? pick(content.chain()) : BOGUS);
    }

    QuestFuzzOp claim() {
        return new QuestFuzzOp.Claim(claimTarget(), claimKey());
    }

    QuestFuzzOp claim(String questCode) {
        return new QuestFuzzOp.Claim(questCode, freshKey());
    }

    String claimTarget() {
        List<String> completed = new ArrayList<>();
        model.quests().forEach((code, state) -> {
            if (state.status() == QuestStatus.COMPLETED) {
                completed.add(code);
            }
        });
        if (!completed.isEmpty() && chance(65)) {
            return pick(completed);
        }
        List<String> known = new ArrayList<>(model.quests().keySet());
        if (!known.isEmpty() && chance(60)) {
            return pick(known);
        }
        return chance(50) ? pick(content.chain()) : BOGUS;
    }

    String claimKey() {
        List<String> burned = new ArrayList<>(model.burnedKeys());
        return !burned.isEmpty() && chance(20) ? pick(burned) : freshKey();
    }

    static String freshKey() {
        return UUID.randomUUID().toString();
    }

    boolean chance(int percent) {
        return random.nextInt(PERCENT) < percent;
    }

    int between(int lowInclusive, int highInclusive) {
        return lowInclusive + random.nextInt(highInclusive - lowInclusive + 1);
    }

    <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}

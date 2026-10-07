package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

import dev.kage.quest.domain.QuestStatus;

/**
 * Một seed: một nhân vật mới, đặt ở một điểm ngẫu nhiên trong chuỗi quest, chạy {@code steps} bước
 * ngẫu nhiên (lệnh tuần tự, loạt song song, đưa đồ vào túi, nhồi/dọn túi, đăng nhập lại), kiểm mọi
 * bất biến SAU MỖI BƯỚC, rồi đi nốt mọi quest đang dở tới lĩnh thưởng ({@link QuestFuzzDrain}).
 */
final class QuestFuzzRun {

    private static final int TRACE_TAIL = 40;
    /** Ô mới tối đa một lượt lĩnh có thể cần: 6 món trang bị + 1 chồng mới. */
    static final int MAX_SLOTS_PER_CLAIM = 7;

    private final long seed;
    private final QuestFuzzDb db;
    private final QuestFuzzContent content;
    private final Random random;
    private final QuestFuzzModel model;
    private final QuestFuzzGenerator generator;
    private final List<String> trace = new ArrayList<>();
    private final List<String> pending = new ArrayList<>();
    private final Map<String, Integer> coverage = new TreeMap<>();
    private long characterId;
    private QuestFuzzActions actions;
    private QuestFuzzChecker checker;
    private QuestFuzzBurst burst;

    QuestFuzzRun(long seed, QuestFuzzDb db, QuestFuzzContent content) {
        this.seed = seed;
        this.db = db;
        this.content = content;
        this.random = new Random(seed);
        this.model = new QuestFuzzModel(content);
        this.generator = new QuestFuzzGenerator(random, content, model);
    }

    /** @return mọi vi phạm của lần chạy (rỗng = đạt). Dừng ở bước vi phạm đầu tiên. */
    List<String> run(long accountId, int steps, ExecutorService executor) throws InterruptedException {
        setUp(accountId);
        burst = new QuestFuzzBurst(executor, actions);
        List<String> violations = verify();
        for (int step = 0; step < steps && violations.isEmpty(); step++) {
            step(step);
            violations = verify();
        }
        if (violations.isEmpty()) {
            trace.add("== đi nốt chuỗi ==");
            new QuestFuzzDrain(this).finishEverything();
            violations = verify();
        }
        return violations;
    }

    private void setUp(long accountId) {
        characterId = db.createCharacter(accountId, "QFuzz" + Long.toHexString(seed));
        actions = new QuestFuzzActions(content, characterId, QuestFuzzSystem.wire(db.dataSource()));
        checker = new QuestFuzzChecker(db, content, characterId);
        int start = random.nextInt(content.chain().size() + 1);
        for (String code : content.chain().subList(0, start)) {
            db.seedClaimed(characterId, code);
            model.seedClaimed(code);
        }
        trace.add("bắt đầu sau " + start + " quest đã lĩnh");
        if (generator.chance(30)) {
            giveItem("minor_healing_pill", generator.between(1, 4));
        }
    }

    private void step(int step) throws InterruptedException {
        int roll = random.nextInt(100);
        if (roll < 62) {
            sequential(generator.anyOp());
        } else if (roll < 74) {
            concurrent();
        } else if (roll < 82) {
            supplyDeliveryItems();
        } else if (roll < 87) {
            fillBag();
        } else if (roll < 92) {
            emptyJunk();
        } else if (roll < 96) {
            relog(step);
        } else {
            sellEverything();
        }
    }

    // ------------------------------------------------------------------ lệnh

    void sequential(QuestFuzzOp op) {
        boolean tight = checker.bagRows() + MAX_SLOTS_PER_CLAIM > QuestFuzzChecker.BAG_CAPACITY;
        QuestFuzzActions.Observed observed = actions.execute(op);
        trace.add(op + " -> " + observed);
        tally(op, observed);
        actions.applyToModel(model, op, observed, tight).ifPresent(pending::add);
    }

    private void concurrent() throws InterruptedException {
        List<QuestFuzzOp> ops = burstOps();
        long claims = ops.stream().filter(QuestFuzzOp.Claim.class::isInstance).count();
        boolean tight = checker.bagRows() + MAX_SLOTS_PER_CLAIM * claims > QuestFuzzChecker.BAG_CAPACITY;
        QuestFuzzBurst.Outcome outcome = burst.run(model, ops, tight);
        trace.add("song song " + ops + " -> " + outcome.observed());
        coverage.merge("burst" + ops.size() + (outcome.candidates().size() > 1 ? "-ambiguous" : ""), 1, Integer::sum);
        for (int i = 0; i < ops.size(); i++) {
            tally(ops.get(i), outcome.observed().get(i));
        }
        if (outcome.candidates().isEmpty()) {
            pending.add("không thứ tự tuần tự nào giải thích được loạt song song " + ops + " -> " + outcome.observed());
            return;
        }
        model.become(pickCandidate(outcome.candidates()));
    }

    /** Nhiều thứ tự hợp lệ ⇒ lấy thứ tự khớp với DB; không cái nào khớp thì lấy cái đầu để verify báo. */
    private QuestFuzzModel pickCandidate(List<QuestFuzzModel> candidates) {
        if (candidates.size() > 1) {
            for (QuestFuzzModel candidate : candidates) {
                if (checker.check(candidate, actions.system().quest().listQuests(characterId))
                        .isEmpty()) {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    private List<QuestFuzzOp> burstOps() {
        int size = generator.between(2, 4);
        List<QuestFuzzOp> ops = new ArrayList<>();
        if (generator.chance(55)) {
            // Cùng MỘT lệnh lặp lại — bấm đúp, client tự gửi lại, hay nhiều quái chết cùng nhịp.
            QuestFuzzOp op = generator.anyOp();
            boolean distinctKeys = op instanceof QuestFuzzOp.Claim && generator.chance(50);
            for (int i = 0; i < size; i++) {
                ops.add(distinctKeys ? generator.claim(((QuestFuzzOp.Claim) op).questCode()) : op);
            }
        } else {
            for (int i = 0; i < size; i++) {
                ops.add(generator.anyOp());
            }
        }
        return ops;
    }

    // ------------------------------------------------------------------ túi đồ

    private void supplyDeliveryItems() {
        List<QuestFuzzSpec.ItemTake> wanted = new ArrayList<>();
        for (String code : model.activeCodes()) {
            wanted.addAll(content.spec(code).deliveries());
        }
        if (wanted.isEmpty()) {
            wanted.addAll(QuestFuzzSpec.FIXTURE_DELIVERY.deliveries());
        }
        QuestFuzzSpec.ItemTake take = generator.pick(wanted);
        int quantity = Math.max(1, take.quantity() + generator.between(-1, 1));
        if (quantity > 1 && generator.chance(40)) {
            // Túi phân mảnh — lượt giao phải gom từ nhiều dòng.
            giveItem(take.itemCode(), 1);
            giveItem(take.itemCode(), quantity - 1);
        } else {
            giveItem(take.itemCode(), quantity);
        }
    }

    void giveItem(String itemCode, int quantity) {
        if (checker.bagRows() >= QuestFuzzChecker.BAG_CAPACITY) {
            return;
        }
        db.insertItem(characterId, itemCode, quantity);
        model.addItem(itemCode, quantity);
        trace.add("đưa " + quantity + " " + itemCode);
    }

    private void fillBag() {
        int target = QuestFuzzChecker.BAG_CAPACITY - generator.between(0, MAX_SLOTS_PER_CLAIM);
        int added = 0;
        while (checker.bagRows() < target) {
            db.insertItem(characterId, QuestFuzzContent.JUNK_ITEM, 1);
            added++;
        }
        trace.add("nhồi túi +" + added + " ô");
    }

    void emptyJunk() {
        db.deleteItemsOf(characterId, QuestFuzzContent.JUNK_ITEM);
        trace.add("dọn ô nhồi");
    }

    /** Người chơi bán sạch một loại vật phẩm — đường ra khỏi túi không đi qua quest. */
    void sellEverything() {
        List<String> held = new ArrayList<>(model.bag().keySet());
        if (held.isEmpty()) {
            return;
        }
        Collections.sort(held);
        String itemCode = generator.pick(held);
        db.deleteItemsOf(characterId, itemCode);
        model.removeAll(itemCode);
        trace.add("bán sạch " + itemCode);
    }

    private void relog(int step) {
        actions.relog(QuestFuzzSystem.wire(db.dataSource()));
        trace.add("đăng nhập lại (bước " + step + ")");
    }

    // ------------------------------------------------------------------ kiểm

    List<String> verify() {
        List<String> violations = new ArrayList<>(pending);
        pending.clear();
        violations.addAll(actions.drainViolations());
        violations.addAll(checker.check(model, actions.system().quest().listQuests(characterId)));
        return violations;
    }

    void flag(String violation) {
        pending.add(violation);
    }

    String failureReport(List<String> violations) {
        List<String> tail = trace.subList(Math.max(0, trace.size() - TRACE_TAIL), trace.size());
        return ("seed=%d (chạy lại: -Dkage.questFuzz.seed=%d -Dkage.questFuzz.seeds=1)%n"
                        + "vi phạm:%n  %s%nvết lệnh cuối:%n  %s")
                .formatted(seed, seed, String.join("\n  ", violations), String.join("\n  ", tail));
    }

    private void tally(QuestFuzzOp op, QuestFuzzActions.Observed observed) {
        coverage.merge(op.getClass().getSimpleName() + ":" + observed.result(), 1, Integer::sum);
        if (!observed.completed().isEmpty()) {
            coverage.merge("completed", observed.completed().size(), Integer::sum);
        }
    }

    /** Đếm theo loại lệnh + kết quả — để thấy lượt chạy thật sự đi tới lĩnh thưởng, túi đầy, v.v. */
    Map<String, Integer> coverage() {
        return Map.copyOf(coverage);
    }

    QuestFuzzModel model() {
        return model;
    }

    QuestFuzzContent content() {
        return content;
    }

    QuestFuzzChecker checker() {
        return checker;
    }

    void clearWholeBag() {
        db.execute("DELETE FROM inventory_item WHERE character_id = ?", characterId);
        for (String itemCode : new ArrayList<>(model.bag().keySet())) {
            model.removeAll(itemCode);
        }
        trace.add("dọn sạch túi");
    }

    Optional<QuestStatus> statusOf(String code) {
        return model.statusOf(code);
    }
}

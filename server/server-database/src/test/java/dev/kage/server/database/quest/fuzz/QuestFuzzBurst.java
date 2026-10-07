package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Một loạt lệnh chạy CÙNG LÚC (cùng cổng xuất phát) rồi kiểm "tuyến tính hoá được": phải có ít nhất
 * một thứ tự tuần tự mà mô hình, đi theo thứ tự đó, ra đúng từng kết quả đã quan sát. Mọi thứ tự hợp
 * lệ đều được giữ làm ứng viên — bộ chạy chọn ứng viên khớp với trạng thái đã lưu.
 *
 * <p>Đây là chỗ bắt các lỗi kiểu "hai lệnh lĩnh cùng trao thưởng", "hai lần mở lời cùng lấy đồ",
 * "hai con quái chết cùng nhịp mà chỉ đếm một".
 */
final class QuestFuzzBurst {

    private static final long JOIN_SECONDS = 60L;

    private final ExecutorService executor;
    private final QuestFuzzActions actions;

    QuestFuzzBurst(ExecutorService executor, QuestFuzzActions actions) {
        this.executor = executor;
        this.actions = actions;
    }

    /** Kết quả một loạt: những gì đã quan sát, và các mô hình ứng viên (rỗng = không giải thích được). */
    record Outcome(List<QuestFuzzActions.Observed> observed, List<QuestFuzzModel> candidates) {
        Outcome {
            observed = List.copyOf(observed);
            candidates = List.copyOf(candidates);
        }
    }

    Outcome run(QuestFuzzModel model, List<QuestFuzzOp> ops, boolean tightBag) throws InterruptedException {
        List<QuestFuzzActions.Observed> observed = executeTogether(ops);
        Attempt attempt = new Attempt(ops, observed, tightBag);
        List<QuestFuzzModel> candidates = new ArrayList<>();
        Set<QuestFuzzModel.Snapshot> seen = new HashSet<>();
        for (List<Integer> order : permutations(ops.size())) {
            QuestFuzzModel branch = model.copy();
            if (replay(branch, attempt, order) && seen.add(branch.snapshot())) {
                candidates.add(branch);
            }
        }
        return new Outcome(observed, candidates);
    }

    /** Lệnh đã chạy + thứ quan sát được, cùng chỉ số. */
    private record Attempt(List<QuestFuzzOp> ops, List<QuestFuzzActions.Observed> observed, boolean tightBag) {}

    private boolean replay(QuestFuzzModel branch, Attempt attempt, List<Integer> order) {
        for (int index : order) {
            Optional<String> problem = actions.applyToModel(
                    branch, attempt.ops().get(index), attempt.observed().get(index), attempt.tightBag());
            if (problem.isPresent()) {
                return false;
            }
        }
        return true;
    }

    private List<QuestFuzzActions.Observed> executeTogether(List<QuestFuzzOp> ops) throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<QuestFuzzActions.Observed>> futures = new ArrayList<>();
        for (QuestFuzzOp op : ops) {
            futures.add(executor.submit(() -> {
                gate.await();
                return actions.execute(op);
            }));
        }
        gate.countDown();
        List<QuestFuzzActions.Observed> observed = new ArrayList<>();
        for (Future<QuestFuzzActions.Observed> future : futures) {
            try {
                observed.add(future.get(JOIN_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                throw new AssertionError("Một lệnh trong loạt song song ném lỗi: " + e.getCause(), e);
            } catch (TimeoutException e) {
                throw new AssertionError("Loạt song song treo quá " + JOIN_SECONDS + "s — nghi deadlock", e);
            }
        }
        return observed;
    }

    /** Mọi hoán vị của {@code 0..size-1} — size ≤ 4 nên tối đa 24. */
    static List<List<Integer>> permutations(int size) {
        List<List<Integer>> result = new ArrayList<>();
        permute(new ArrayList<>(), new boolean[size], result);
        return result;
    }

    private static void permute(List<Integer> prefix, boolean[] used, List<List<Integer>> result) {
        if (prefix.size() == used.length) {
            result.add(List.copyOf(prefix));
            return;
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                used[i] = true;
                prefix.add(i);
                permute(prefix, used, result);
                prefix.removeLast();
                used[i] = false;
            }
        }
    }
}

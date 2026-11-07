package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * Chọn người gợi ý kết bạn (ADR-211): người đang online, cảnh giới gần mình (chênh tối đa {@value #LEVEL_RANGE} cấp),
 * chưa là bạn / chưa gửi lời mời cho mình, không ai chặn ai. Tối đa {@value #MAX_SUGGESTIONS} người; chỉ xét tối đa
 * {@value #MAX_SCANNED} người online đầu tiên để một server đông không biến một lần hỏi thành hàng trăm lần đọc DB.
 */
final class FriendSuggestions {

    /** Số gợi ý tối đa một lần. */
    static final int MAX_SUGGESTIONS = 10;

    /** Chênh cấp tối đa (9 cấp = một cảnh giới). */
    static final int LEVEL_RANGE = 10;

    /** Số người online tối đa được xét. */
    static final int MAX_SCANNED = 200;

    /** Một người được gợi ý. */
    record Candidate(long characterId, String name, int level) {}

    /**
     * Nguồn dữ liệu của một lần chọn.
     *
     * @param online người đang online, theo thứ tự xét
     * @param excluded không gợi ý (chính mình, bạn bè, người đã gửi lời mời cho mình)
     * @param blocked hai người có chặn nhau không (với mình)
     * @param load đọc tên + cấp một nhân vật
     */
    record Sources(
            List<Long> online, Set<Long> excluded, LongPredicate blocked, LongFunction<Optional<Candidate>> load) {}

    private FriendSuggestions() {}

    static List<Candidate> pick(int myLevel, Sources sources) {
        List<Candidate> picked = new ArrayList<>();
        int scanned = 0;
        for (long id : sources.online()) {
            if (picked.size() >= MAX_SUGGESTIONS || scanned >= MAX_SCANNED) {
                break;
            }
            if (sources.excluded().contains(id)) {
                continue;
            }
            scanned++;
            Optional<Candidate> candidate = sources.load().apply(id);
            if (candidate.isPresent()
                    && Math.abs(candidate.get().level() - myLevel) <= LEVEL_RANGE
                    && !sources.blocked().test(id)) {
                picked.add(candidate.get());
            }
        }
        return picked;
    }
}

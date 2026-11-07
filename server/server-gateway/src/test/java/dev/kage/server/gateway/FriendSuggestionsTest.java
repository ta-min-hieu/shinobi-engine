package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** ADR-211 — chọn người gợi ý: online, cảnh giới gần, chưa là bạn, không chặn nhau, tối đa 10. */
class FriendSuggestionsTest {

    private static final Map<Long, FriendSuggestions.Candidate> PEOPLE = Map.of(
            2L, new FriendSuggestions.Candidate(2L, "Gan", 22),
            3L, new FriendSuggestions.Candidate(3L, "Xa", 45),
            4L, new FriendSuggestions.Candidate(4L, "BanCu", 20),
            5L, new FriendSuggestions.Candidate(5L, "ChanNhau", 19),
            6L, new FriendSuggestions.Candidate(6L, "SatMep", 10));

    @Test
    void shouldKeepOnlyNearbyStrangersWhoDoNotBlock() {
        List<FriendSuggestions.Candidate> picked = FriendSuggestions.pick(
                20,
                new FriendSuggestions.Sources(
                        List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                        Set.of(1L, 4L),
                        id -> id == 5L,
                        id -> Optional.ofNullable(PEOPLE.get(id))));

        assertThat(picked).extracting(FriendSuggestions.Candidate::name).containsExactly("Gan", "SatMep");
    }

    @Test
    void shouldStopAtTheMaximum() {
        List<Long> online = new ArrayList<>();
        for (long id = 100; id < 130; id++) {
            online.add(id);
        }
        List<FriendSuggestions.Candidate> picked = FriendSuggestions.pick(
                20,
                new FriendSuggestions.Sources(
                        online,
                        Set.of(),
                        id -> false,
                        id -> Optional.of(new FriendSuggestions.Candidate(id, "P" + id, 20))));

        assertThat(picked).hasSize(FriendSuggestions.MAX_SUGGESTIONS);
    }
}

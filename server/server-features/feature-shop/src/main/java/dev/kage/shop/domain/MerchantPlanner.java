package dev.kage.shop.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Lên lịch MỘT ngày của thương nhân du hành (ADR-118) — hàm thuần, ngẫu nhiên do server cấp. 2–3 lần ghé, mỗi lần
 * {@link #VISIT_DURATION}, bắt đầu ở các giờ KHÁC NHAU trong khung {@link #FIRST_HOUR}h–{@link #LAST_START_HOUR}h (giờ
 * Việt Nam) nên không bao giờ chồng nhau; mỗi lần một trạm ngẫu nhiên và {@link #LINES_PER_VISIT} món rút theo
 * trọng số, không lặp trong cùng lần ghé.
 */
public final class MerchantPlanner {

    public static final Duration VISIT_DURATION = Duration.ofMinutes(30);
    public static final int MIN_VISITS = 2;
    public static final int MAX_VISITS = 3;
    public static final int FIRST_HOUR = 8;
    public static final int LAST_START_HOUR = 22;
    public static final int LINES_PER_VISIT = 3;

    private static final int MINUTES_PER_HOUR = 60;
    private static final int LATEST_MINUTE_IN_HOUR = 30;

    /** Một lần ghé đã lên lịch. */
    public record PlannedVisit(String waypointCode, Instant startsAt, List<MerchantStock> stock) {

        public PlannedVisit {
            stock = List.copyOf(stock);
        }

        public Instant endsAt() {
            return startsAt.plus(VISIT_DURATION);
        }
    }

    private MerchantPlanner() {}

    /**
     * Lịch của một ngày.
     *
     * @param dayStart nửa đêm giờ Việt Nam của ngày cần lên lịch
     * @param stations mã các trạm (điểm truyền tống) — rỗng thì không lên lịch
     */
    public static List<PlannedVisit> planDay(
            Instant dayStart, List<String> stations, List<MerchantOffer> offers, RandomGenerator random) {
        if (stations.isEmpty() || offers.isEmpty()) {
            return List.of();
        }
        int visits = random.nextInt(MIN_VISITS, MAX_VISITS + 1);
        List<Integer> hours = new ArrayList<>();
        for (int hour = FIRST_HOUR; hour <= LAST_START_HOUR; hour++) {
            hours.add(hour);
        }
        List<PlannedVisit> planned = new ArrayList<>();
        for (int i = 0; i < visits; i++) {
            // Rút một giờ chưa dùng: lấy ô ngẫu nhiên, lấp bằng ô cuối rồi bỏ ô cuối.
            int slot = random.nextInt(hours.size());
            int hour = hours.get(slot);
            hours.set(slot, hours.getLast());
            hours.removeLast();
            Instant startsAt = dayStart.plus(
                    Duration.ofMinutes((long) hour * MINUTES_PER_HOUR + random.nextInt(LATEST_MINUTE_IN_HOUR + 1)));
            String station = stations.get(random.nextInt(stations.size()));
            planned.add(new PlannedVisit(station, startsAt, stockFor(offers, random)));
        }
        planned.sort(java.util.Comparator.comparing(PlannedVisit::startsAt));
        return planned;
    }

    private static List<MerchantStock> stockFor(List<MerchantOffer> offers, RandomGenerator random) {
        List<MerchantOffer> pool = new ArrayList<>(offers);
        List<MerchantStock> stock = new ArrayList<>();
        while (stock.size() < LINES_PER_VISIT && !pool.isEmpty()) {
            MerchantOffer pick = pickWeighted(pool, random);
            pool.remove(pick);
            stock.add(new MerchantStock(pick.itemTemplateId(), pick.price(), pick.quantityPerVisit()));
        }
        return stock;
    }

    private static MerchantOffer pickWeighted(List<MerchantOffer> pool, RandomGenerator random) {
        int total = 0;
        for (MerchantOffer offer : pool) {
            total += Math.max(1, offer.weight());
        }
        int roll = random.nextInt(total);
        for (MerchantOffer offer : pool) {
            roll -= Math.max(1, offer.weight());
            if (roll < 0) {
                return offer;
            }
        }
        return pool.getLast();
    }
}

package dev.kage.tools.loadtest.probe;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import dev.kage.tools.loadtest.CommandLineArguments;

/**
 * Kịch bản 1 lần chạy probe — parse từ {@code --key=value} (xem {@link CommandLineArguments}).
 *
 * <p>Toàn bộ tham số đều có mặc định chạy được ngay trên môi trường dev (tài khoản playtest
 * {@code kagetest}), vì công cụ này sinh ra để trả lời NHANH một câu hỏi cụ thể chứ không phải để
 * cấu hình lâu dài.
 *
 * @param host địa chỉ gateway
 * @param port cổng gateway (mặc định 7777)
 * @param username tên account gửi trong {@code ConnectRequest}
 * @param characterId 0 = không chỉ định, chọn theo {@code characterName} hoặc nhân vật đầu tiên
 * @param characterName rỗng = không chỉ định
 * @param createCharacter {@code true} = account chưa có nhân vật tên {@code characterName} thì TẠO MỚI
 *     qua đúng {@code CreateCharacterRequest} thật trước khi vào world (kiểm sống chuỗi quest từ Cấp 1);
 *     mặc định {@code false} — probe không tự đẻ nhân vật rác
 * @param moveToX rỗng = đứng yên tại chỗ server cho spawn; có giá trị = đi bộ THẬT tới toạ độ đó
 * @param attackTarget {@code none} | {@code nearest} | id thực thể cụ thể (số, âm = quái)
 * @param attackCode rỗng = mọi loài; có giá trị = {@code nearest} chỉ chọn trong loài này
 * @param attackSkillCode rỗng = đánh thường; có giá trị = mỗi nhịp gửi {@code UseSkillCommand} mã này lên mục
 *     tiêu thay cho đòn thường (nhịp đầu tới SAU một {@code attackIntervalMillis} — quãng đó là đối chứng)
 * @param retargetOnKill {@code true} = mục tiêu chết thì {@code nearest} chọn con kế tiếp — xem {@link TargetSelector}
 * @param attackIntervalMillis nhịp gửi {@code AttackCommand} (cooldown thật do server giữ)
 * @param observeSeconds thời gian đứng đọc sự kiện sau khi đã tới chỗ
 * @param sellItems các dòng {@code code[:soLuong]} cần BÁN ở cửa hàng trước khi mặc đồ — xem {@link ShopScenario}
 * @param buyItems các dòng {@code code[:soLuong]} cần MUA (sau khi bán) — xem {@link ShopScenario}
 * @param equipItemCodes danh sách mã trang bị cần MẶC trước khi đi bộ (ô trang bị suy từ catalog)
 * @param quest kịch bản nhiệm vụ (nói chuyện NPC / lĩnh thưởng / in nhật ký) — xem {@link QuestPlan}
 * @param enterDungeon mã hầm ngục để tự lập nhóm 1 người rồi vào, sau khi đi bộ; rỗng = không vào —
 *     xem {@link DungeonScenario}
 * @param duel kịch bản Arena 1v1 (thách / chấp nhận / bỏ cuộc) — xem {@link DuelPlan}
 * @param party kịch bản lập nhóm hai người (mời / chấp nhận / in thành viên) — xem {@link PartyPlan}
 * @param trade kịch bản mở phiên giao dịch (mời / chấp nhận / in trạng thái) — xem {@link TradePlan}
 * @param edge các lệnh lẻ gửi từ trong world (gỡ đồ / chat / đăng nhập lại) — xem {@link EdgePlan}
 */
public record ProbeConfig(
        String host,
        int port,
        String username,
        long characterId,
        String characterName,
        boolean createCharacter,
        OptionalDouble moveToX,
        String attackTarget,
        String attackCode,
        String attackSkillCode,
        boolean retargetOnKill,
        int attackIntervalMillis,
        int observeSeconds,
        List<String> useItemCodes,
        OptionalDouble returnToX,
        int settleSeconds,
        List<String> sellItems,
        List<String> buyItems,
        List<String> equipItemCodes,
        QuestPlan quest,
        String enterDungeon,
        DuelPlan duel,
        PartyPlan party,
        TradePlan trade,
        EdgePlan edge,
        ProgressionPlan progression,
        EconomyPlan economy,
        InspectionPlan inspection,
        Lot19Plan lot19) {

    /**
     * Danh sách mã trang bị BẤT BIẾN — record không tự copy, mà lệnh gọi có thể truyền list sống
     * (đúng lý do {@link QuestPlan} đã làm với {@code dialogueChoices}). Thiếu chỗ này thì SpotBugs
     * bắt đúng hai lỗi: {@code EI_EXPOSE_REP2} (constructor giữ list của người ngoài) và
     * {@code EI_EXPOSE_REP} (getter trả thẳng list nội bộ ra).
     */
    public ProbeConfig {
        useItemCodes = List.copyOf(useItemCodes);
        sellItems = List.copyOf(sellItems);
        buyItems = List.copyOf(buyItems);
        equipItemCodes = List.copyOf(equipItemCodes);
    }

    /**
     * Phần kịch bản NHIỆM VỤ của một lần chạy probe — tách khỏi {@link ProbeConfig} để chữ ký
     * record không vượt ngưỡng {@code ParameterNumber} và để nhóm tham số đi cùng nhau nằm cùng
     * chỗ (cùng cách {@code GameConnection.GatewayEndpoint} gộp địa chỉ gateway).
     *
     * @param npcCode NPC để mở hội thoại ({@code TalkToNpcCommand}); rỗng = không nói chuyện
     * @param dialogueChoices đường đi trong cây hội thoại, mỗi phần tử là CHỈ SỐ lựa chọn (số) hoặc
     *     KHOÁ ({@code action} hay {@code label_key}) — xem {@link DialogueChoice}
     * @param claimQuestCode quest để gửi {@code ClaimQuestRewardCommand}; rỗng = không lĩnh thưởng
     * @param report {@code true} = in nhật ký nhiệm vụ + túi đồ ngay cả khi không làm gì khác
     */
    public record QuestPlan(String npcCode, List<String> dialogueChoices, String claimQuestCode, boolean report) {

        /** Danh sách bước bất biến — record không tự copy, mà lệnh gọi có thể truyền list sống. */
        public QuestPlan {
            dialogueChoices = List.copyOf(dialogueChoices);
        }

        /** {@code true} khi có ít nhất một việc phải làm trước khi vào vòng quan sát. */
        public boolean isRequested() {
            return report || !npcCode.isEmpty() || !claimQuestCode.isEmpty();
        }
    }

    /**
     * Kịch bản Arena 1v1 ({@link ArenaScenario}) — hai probe, một bên thách, một bên chấp nhận.
     *
     * @param challengeTargetName tên nhân vật để thách đấu; rỗng = không thách
     * @param acceptIncoming {@code true} = đợi lời thách đến rồi chấp nhận
     * @param waitSeconds hạn chót chờ lời thách / trận bắt đầu
     * @param acceptDelaySeconds số giây NGỒI YÊN sau khi nhận lời thách rồi mới chấp nhận — để bên thách
     *     kịp rớt mạng trước (README dòng 73a); 0 = chấp nhận ngay
     * @param forfeitAfterObserve {@code true} = gửi {@code ExitDuelCommand} (bỏ cuộc) sau vòng quan sát
     */
    public record DuelPlan(
            String challengeTargetName,
            boolean acceptIncoming,
            int waitSeconds,
            int acceptDelaySeconds,
            boolean forfeitAfterObserve) {

        /** {@code true} khi probe phải vào trận trước vòng quan sát. */
        public boolean isRequested() {
            return acceptIncoming || !challengeTargetName.isEmpty();
        }
    }

    /**
     * Kịch bản lập nhóm hai người ({@link PartyScenario}) — hai probe trên hai tài khoản, một bên mời,
     * một bên chấp nhận.
     *
     * @param inviteTargetName tên nhân vật để lập nhóm rồi mời; rỗng = không mời
     * @param acceptIncoming {@code true} = thăm dò lời mời đến rồi chấp nhận
     * @param waitSeconds hạn chót chờ lời mời
     * @param report {@code true} = in danh sách thành viên sau vòng quan sát
     */
    public record PartyPlan(String inviteTargetName, boolean acceptIncoming, int waitSeconds, boolean report) {}

    /**
     * Kịch bản mở phiên giao dịch ({@link TradeScenario}) — hai probe trên hai tài khoản.
     *
     * @param requestTargetName tên nhân vật để gửi lời mời giao dịch; rỗng = không mời
     * @param acceptIncoming {@code true} = đợi lời mời đến rồi chấp nhận
     * @param waitSeconds hạn chót chờ lời mời
     * @param report {@code true} = in trạng thái phiên giao dịch sau vòng quan sát
     * @param confirmTrade lô 18 (ADR-075), bên chấp nhận chạy ngay sau khi nhận lời: {@code stale} = tự đặt lại
     *     lời chào (tăng phiên bản) rồi xác nhận bằng phiên bản CŨ; {@code current} = xác nhận bằng phiên bản hiện
     *     tại. Mọi bước dùng CHUNG một khoá chống trùng.
     */
    public record TradePlan(
            String requestTargetName,
            boolean acceptIncoming,
            int waitSeconds,
            boolean report,
            List<String> confirmTrade) {

        /** Danh sách bất biến — record không tự copy. */
        public TradePlan {
            confirmTrade = List.copyOf(confirmTrade);
        }
    }

    /**
     * Các lệnh lẻ gửi từ trong world ({@link SessionEdgeScenario}).
     *
     * @param unequipSlots tên ô trang bị cần GỠ (vd {@code weapon}, {@code armor}) trước khi đi bộ
     * @param reloginInWorld {@code true} = gửi {@code ConnectRequest} lần hai trên kết nối đang chơi
     * @param reloginUsername tên tài khoản của lần đăng nhập thứ hai (mặc định = {@code username})
     * @param chatChannel kênh chat ({@code world} / {@code area} / {@code party} / {@code guild})
     * @param chatBody nội dung chat gửi sau khi đi bộ; chuỗi {@code \n} thành xuống dòng thật; rỗng = không gửi
     * @param selfBuffSkillCode chiêu TỰ BUFF (đích SELF) tung lại mỗi {@code selfBuffIntervalMillis} trong vòng
     *     quan sát, xen giữa các đòn — đo luật chồng buff lên chỉ số gốc (vd trần chí mạng); rỗng = không tung
     * @param selfBuffIntervalMillis nhịp tung lại — phải LỚN HƠN hồi chiêu server của chiêu đó
     */
    public record EdgePlan(
            List<String> unequipSlots,
            boolean reloginInWorld,
            String reloginUsername,
            String chatChannel,
            String chatBody,
            String selfBuffSkillCode,
            int selfBuffIntervalMillis) {

        /** Danh sách ô bất biến — record không tự copy, mà lệnh gọi có thể truyền list sống. */
        public EdgePlan {
            unequipSlots = List.copyOf(unequipSlots);
        }
    }

    /**
     * Kế hoạch đọc/kiểm của lô 15 — chạy SAU khi đi bộ (xem {@link InspectionScenario}).
     *
     * @param abandonQuest mã nhiệm vụ cần bỏ; rỗng = không bỏ
     * @param claimAllMail lĩnh mọi thư chưa lĩnh (mỗi thư gửi hai lần cùng khoá idempotency)
     * @param sendMail {@code nguoiNhan:soVang} — gửi một thư kèm vàng, HAI lần cùng khoá (ADR-058); rỗng = không
     * @param statSheet in bảng nhân vật (210/211)
     * @param leaderboards loại bảng xếp hạng cần in ({@code realm}, {@code arena}, {@code combat_power},
     *     {@code guild})
     * @param friendSteps lô 18 (ADR-074): {@code send:Ten} gửi lời mời kết bạn, {@code withdraw:Ten} rút lời mời
     */
    public record InspectionPlan(
            String abandonQuest,
            boolean claimAllMail,
            String sendMail,
            boolean statSheet,
            List<String> leaderboards,
            List<String> friendSteps) {

        /** Danh sách bất biến — record không tự copy, mà lệnh gọi có thể truyền list sống. */
        public InspectionPlan {
            leaderboards = List.copyOf(leaderboards);
            friendSteps = List.copyOf(friendSteps);
        }

        /** {@code true} khi có ít nhất một bước cần chạy. */
        public boolean isRequested() {
            return !abandonQuest.isEmpty()
                    || claimAllMail
                    || !sendMail.isEmpty()
                    || statSheet
                    || !leaderboards.isEmpty()
                    || !friendSteps.isEmpty();
        }
    }

    /**
     * Kế hoạch kinh tế của lô 16 — chạy SAU khi đi bộ (xem {@link EconomyScenario}).
     *
     * @param depositItems món gửi kho, {@code code} hoặc {@code code:soLuong}
     * @param withdrawItems món rút kho, {@code code} hoặc {@code code:soLuong}
     * @param socketGems lượt khảm, mỗi phần tử {@code maTrangBi:maNgoc}; tiền tố {@code ma:soLuong>} đặt lời chào
     *     giao dịch đó ngay trước lượt khảm (dòng 82)
     * @param setTitle mã thành tựu cần đeo làm danh hiệu; rỗng = không đổi
     * @param listOnMarket dòng treo phường thị, mỗi phần tử {@code ma:giaTong}
     * @param buyFromMarket mã món cần mua dòng rẻ nhất ở phường thị
     */
    public record EconomyPlan(
            List<String> depositItems,
            List<String> withdrawItems,
            List<String> socketGems,
            String setTitle,
            List<String> listOnMarket,
            List<String> buyFromMarket) {

        /** Danh sách bất biến — record không tự copy, mà lệnh gọi có thể truyền list sống. */
        public EconomyPlan {
            depositItems = List.copyOf(depositItems);
            withdrawItems = List.copyOf(withdrawItems);
            socketGems = List.copyOf(socketGems);
            listOnMarket = List.copyOf(listOnMarket);
            buyFromMarket = List.copyOf(buyFromMarket);
        }

        /** {@code true} khi có ít nhất một bước cần chạy. */
        public boolean isRequested() {
            return !depositItems.isEmpty()
                    || !withdrawItems.isEmpty()
                    || !socketGems.isEmpty()
                    || !setTitle.isEmpty()
                    || !listOnMarket.isEmpty()
                    || !buyFromMarket.isEmpty();
        }
    }

    /**
     * Kế hoạch lô 19 (xem {@link Lot19Scenario}).
     *
     * @param craftRecipes mã công thức lò đan cần luyện, mỗi phần tử một mẻ
     * @param guildShopBuy {@code item_template_id} cần đổi ở cửa hàng bang
     * @param partyFinderSteps bước bảng tìm nhóm: {@code post:GOAL[:minLevel]}, {@code list}, {@code join:<id>},
     *     {@code remove}
     * @param lot20Steps bước lô 20 (xem {@link Lot20Scenario})
     */
    public record Lot19Plan(
            List<String> craftRecipes,
            List<String> guildShopBuy,
            List<String> partyFinderSteps,
            List<String> lot20Steps) {

        /** Danh sách bất biến — record không tự copy. */
        public Lot19Plan {
            craftRecipes = List.copyOf(craftRecipes);
            guildShopBuy = List.copyOf(guildShopBuy);
            partyFinderSteps = List.copyOf(partyFinderSteps);
            lot20Steps = List.copyOf(lot20Steps);
        }

        /** {@code true} khi có ít nhất một bước cần chạy. */
        public boolean isRequested() {
            return !craftRecipes.isEmpty()
                    || !guildShopBuy.isEmpty()
                    || !partyFinderSteps.isEmpty()
                    || !lot20Steps.isEmpty();
        }
    }

    /**
     * Kế hoạch tiến triển nhân vật của lô 17 — chạy TRƯỚC khi đi bộ (xem {@link ProgressionScenario}).
     *
     * @param selectFaction tên hệ cần đổi sang ({@code hoa}, {@code thuy}...); rỗng = không đổi
     * @param upgradeSkills mã chiêu cần nâng bậc, theo thứ tự, mỗi phần tử một điểm; rỗng = không nâng
     * @param donateToGuild số vàng góp cho bang; 0 = không góp
     */
    public record ProgressionPlan(String selectFaction, List<String> upgradeSkills, long donateToGuild) {

        /** Danh sách chiêu bất biến — record không tự copy, mà lệnh gọi có thể truyền list sống. */
        public ProgressionPlan {
            upgradeSkills = List.copyOf(upgradeSkills);
        }

        /** {@code true} khi có ít nhất một bước cần chạy. */
        public boolean isRequested() {
            return !selectFaction.isEmpty() || !upgradeSkills.isEmpty() || donateToGuild > 0;
        }
    }

    /** Giá trị {@code attackTarget} nghĩa là "không đánh, chỉ quan sát". */
    public static final String ATTACK_TARGET_NONE = "none";

    /** Giá trị {@code attackTarget} nghĩa là "đánh con quái gần nhất lúc bắt đầu quan sát". */
    public static final String ATTACK_TARGET_NEAREST = "nearest";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7777;
    private static final String DEFAULT_USERNAME = "kagetest";
    private static final int DEFAULT_ATTACK_INTERVAL_MILLIS = 1000; // CombatConstants.ATTACK_INTERVAL_SECONDS = 1s
    private static final int DEFAULT_OBSERVE_SECONDS = 10;
    private static final int DEFAULT_DUEL_WAIT_SECONDS = 60;
    private static final int DEFAULT_PARTY_WAIT_SECONDS = 30;
    private static final String DEFAULT_CHAT_CHANNEL = "world";
    // Hồi chiêu tự buff dài nhất hiện có là 10 s (phong_bao_kich/phong_gia_toc, SkillRegistry) + nửa giây đệm.
    private static final int DEFAULT_SELF_BUFF_INTERVAL_MILLIS = 10_500;

    /**
     * Dựng cấu hình probe từ tham số dòng lệnh.
     *
     * @param args mảng tham số thô của {@code main}
     */
    public static ProbeConfig parse(String[] args) {
        CommandLineArguments raw = CommandLineArguments.parse(args);
        return new ProbeConfig(
                raw.text("host", DEFAULT_HOST),
                raw.integer("port", DEFAULT_PORT),
                raw.text("username", DEFAULT_USERNAME),
                raw.number("characterId", 0L),
                raw.text("characterName", ""),
                raw.flag("createCharacter", false),
                raw.optionalDouble("moveToX"),
                raw.text("attackTarget", ATTACK_TARGET_NONE).toLowerCase(Locale.ROOT),
                raw.text("attackCode", ""),
                raw.text("attackSkillCode", ""),
                raw.flag("retargetOnKill", false),
                raw.integer("attackIntervalMillis", DEFAULT_ATTACK_INTERVAL_MILLIS),
                raw.integer("observeSeconds", DEFAULT_OBSERVE_SECONDS),
                List.copyOf(raw.list("useItemCode")),
                raw.optionalDouble("returnToX"),
                raw.integer("settleSeconds", 0),
                List.copyOf(raw.list("sellItems")),
                List.copyOf(raw.list("buyItems")),
                List.copyOf(raw.list("equipItemCodes")),
                new QuestPlan(
                        raw.text("talkToNpc", ""),
                        raw.list("dialogueChoices"),
                        raw.text("claimQuest", ""),
                        raw.flag("questReport", false)),
                raw.text("enterDungeon", ""),
                new DuelPlan(
                        raw.text("challengeDuel", ""),
                        raw.flag("acceptDuel", false),
                        raw.integer("duelWaitSeconds", DEFAULT_DUEL_WAIT_SECONDS),
                        raw.integer("acceptDuelDelaySeconds", 0),
                        raw.flag("forfeitDuel", false)),
                new PartyPlan(
                        raw.text("partyInvite", ""),
                        raw.flag("partyAccept", false),
                        raw.integer("partyWaitSeconds", DEFAULT_PARTY_WAIT_SECONDS),
                        raw.flag("partyReport", false)),
                tradePlan(raw),
                edgePlan(raw),
                new ProgressionPlan(
                        raw.text("selectFaction", ""), raw.list("upgradeSkills"), raw.number("donateToGuild", 0L)),
                economyPlan(raw),
                inspectionPlan(raw),
                lot19Plan(raw));
    }

    private static Lot19Plan lot19Plan(CommandLineArguments raw) {
        return new Lot19Plan(
                raw.list("craftRecipes"), raw.list("guildShopBuy"), raw.list("partyFinder"), raw.list("lot20"));
    }

    private static TradePlan tradePlan(CommandLineArguments raw) {
        return new TradePlan(
                raw.text("tradeRequest", ""),
                raw.flag("tradeAccept", false),
                raw.integer("tradeWaitSeconds", DEFAULT_PARTY_WAIT_SECONDS),
                raw.flag("tradeReport", false),
                raw.list("confirmTrade"));
    }

    private static InspectionPlan inspectionPlan(CommandLineArguments raw) {
        return new InspectionPlan(
                raw.text("abandonQuest", ""),
                raw.flag("claimAllMail", false),
                raw.text("sendMail", ""),
                raw.flag("statSheet", false),
                raw.list("leaderboards"),
                raw.list("friendSteps"));
    }

    private static EconomyPlan economyPlan(CommandLineArguments raw) {
        return new EconomyPlan(
                raw.list("depositItems"),
                raw.list("withdrawItems"),
                raw.list("socketGems"),
                raw.text("setTitle", ""),
                raw.list("listOnMarket"),
                raw.list("buyFromMarket"));
    }

    private static EdgePlan edgePlan(CommandLineArguments raw) {
        return new EdgePlan(
                raw.list("unequipSlots"),
                raw.flag("reloginInWorld", false),
                raw.text("reloginUsername", raw.text("username", DEFAULT_USERNAME)),
                raw.text("chatChannel", DEFAULT_CHAT_CHANNEL),
                raw.text("chatBody", ""),
                raw.text("selfBuffSkillCode", ""),
                raw.integer("selfBuffIntervalMillis", DEFAULT_SELF_BUFF_INTERVAL_MILLIS));
    }

    /** {@code true} khi người dùng muốn probe tự chọn con gần nhất trong AoI. */
    public boolean attacksNearest() {
        return ATTACK_TARGET_NEAREST.equals(attackTarget);
    }

    /**
     * Id thực thể người dùng chỉ đích danh, rỗng nếu {@code attackTarget} là {@code none} hoặc
     * {@code nearest}.
     *
     * @throws NumberFormatException nếu giá trị không phải {@code none}/{@code nearest} và cũng
     *     không phải số — gõ sai thì dừng ngay, đừng âm thầm chuyển thành "không đánh"
     */
    public OptionalLong explicitAttackTargetId() {
        if (ATTACK_TARGET_NONE.equals(attackTarget) || attacksNearest()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(attackTarget));
    }
}

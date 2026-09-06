package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.input.InputMapper;
import dev.kage.client.core.net.GameConnection;
import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.hud.DialoguePanel;
import dev.kage.client.core.ui.hud.DuelPanel;
import dev.kage.client.core.ui.hud.FactionPanel;
import dev.kage.client.core.ui.hud.FriendPanel;
import dev.kage.client.core.ui.hud.GuildPanel;
import dev.kage.client.core.ui.hud.InventoryPanel;
import dev.kage.client.core.ui.hud.MailPanel;
import dev.kage.client.core.ui.hud.PartyPanel;
import dev.kage.client.core.ui.hud.QuestLogPanel;
import dev.kage.client.core.ui.hud.ShopPanel;
import dev.kage.client.core.ui.hud.TradePanel;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.engine.ecs.system.VerticalMovementSystem;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterAppearanceChangedEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.NpcDespawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Màn hình gameplay thật đầu tiên (Phase 3 — movement) — server-authoritative, client chỉ gửi Ý
 * ĐỊNH (hướng di chuyển) và render state nhận từ server (CLIENT.md §1).
 *
 * <p>Dự đoán CỤC BỘ chỉ áp dụng cho chính nhân vật của mình (CLIENT.md §6), chạy cùng
 * {@link MovementSystem} với server nên thường khớp — sai lệch được sửa qua {@link #onMoveResponse}.
 * Nhân vật KHÁC không dự đoán, chỉ hiển thị vị trí mới nhất nhận từ {@link CharacterMovedEvent}
 * (CLIENT.md §7 muốn nội suy snapshot buffer ~100ms — CHƯA làm ở lát cắt này, follow-up).
 */
public final class WorldScreen implements GameScreen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldScreen.class);

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    private final InputMapper inputMapper;
    private final WorldCommandSender sender;
    private final EcsWorld world;
    private final KEntity localPlayer;
    /** Người chơi KHÁC — kho + model + idle/run, xem {@link RemoteCharacterPresenter}. */
    private final RemoteCharacterPresenter remoteCharacters;

    /**
     * Id THẬT của chính người chơi (từ DB, luôn dương) — dùng để nhận ra
     * {@link AttackResultEvent}/{@link SkillResultEvent} nào nhắm vào MÌNH (khớp
     * {@code attacker_entity_id}/{@code target_entity_id}) và quy đổi sang
     * {@link WorldConstants#LOCAL_CHARACTER_ID} khi gọi {@link WorldModelRenderer}.
     */
    private final long localCharacterId;

    /**
     * Cấp nhân vật — khởi tạo từ lúc chọn nhân vật, sau đó server cập nhật qua
     * {@link ExperienceChangedEvent} (từ 2026-08-09 đã lên cấp được thật giữa trận).
     */
    private int characterLevel;

    private final OrthographicCamera camera = new OrthographicCamera();

    private final Viewport viewport =
            new FitViewport(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS, camera);
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    // MEDIUM tường minh — bản cài đóng gói đúng 1 tier mặc định theo ADR-028, không phải để trống
    // dựa vào default ngầm (không có default: WorldModelRenderer bắt buộc tham số tier).
    private final WorldModelRenderer models = new WorldModelRenderer(GraphicsTier.MEDIUM);

    /**
     * Chiếu điểm world sang màn hình bằng camera của nhánh render ĐANG hoạt động. Hai method
     * reference dựng SẴN 1 lần thay vì viết {@code models::project} tại chỗ mỗi khung — mỗi lần
     * đánh giá một method reference bắt biến (capture {@code models}) là một lần cấp phát, thứ bị
     * cấm trong game loop (Điều 31).
     */
    private final WorldToScreen modelProjector = models::project;

    private final WorldToScreen fallbackProjector = camera::project;

    private final WorldEntityPicker entityPicker = new WorldEntityPicker();

    /** Toàn bộ phần hiển thị quái (model 3D, hộp thay thế, dấu mục tiêu) — xem {@link MonsterPresenter}. */
    private final MonsterPresenter monsterPresenter = new MonsterPresenter(models);

    /** NPC — server phát {@code NpcSpawnedEvent} từ Phase 6, client mới đọc từ 2026-08-09. */
    private final NpcPresenter npcPresenter = new NpcPresenter(models);

    private final CombatHudController combatHudController;
    private final CombatAnimationTrigger combatAnimationTrigger;
    private final CombatHud combatHud = new CombatHud();

    /** Số damage + hiệu ứng skill neo tại vị trí mục tiêu — xem {@link CombatFeedbackPresenter}. */
    private final CombatFeedbackPresenter combatFeedback;

    /** Khung hội thoại NPC — mượn skin của {@link #combatHud}, xem {@link DialoguePanel}. */
    private final DialoguePanel dialoguePanel = new DialoguePanel(combatHud.skin());

    /** Luồng hội thoại (trạng thái + mạng + panel) — xem {@link DialogueCoordinator}. */
    private final DialogueCoordinator dialogue;

    /** Khung chat — mượn skin của {@link #combatHud} như {@link #dialoguePanel}. */
    private final ChatPanel chatPanel = new ChatPanel(combatHud.skin(), ChatController.MAX_LINES);

    /** Luồng chat (nhật ký + kênh + mạng) — xem {@link ChatCoordinator}. */
    private final ChatCoordinator chat;

    /** Cửa sổ Nhóm — mượn skin của {@link #combatHud} như hai panel trên. */
    private final PartyPanel partyPanel = new PartyPanel(combatHud.skin());

    /** Luồng nhóm (thành viên + lời mời + mạng) — xem {@link PartyCoordinator}. */
    private final PartyCoordinator party;
    /** Cửa sổ bạn bè — mượn skin của {@link #combatHud} như {@link #chatPanel}. */
    private final FriendPanel friendPanel = new FriendPanel(combatHud.skin());

    /** Luồng bạn bè (danh sách + lời mời + mạng) — xem {@link FriendCoordinator}. */
    private final FriendCoordinator friends;
    /** Khung bang hội (phím G) — mượn skin của {@link #combatHud} như {@link #chatPanel}. */
    private final GuildPanel guildPanel = new GuildPanel(combatHud.skin());

    /** Luồng bang hội (tạo/xin gia nhập/duyệt/đuổi + mạng) — xem {@link GuildCoordinator}. */
    private final GuildCoordinator guild;

    /** Cửa sổ đấu tay đôi (phím K) — mượn skin của {@link #combatHud} như {@link #guildPanel}. */
    private final DuelPanel duelPanel = new DuelPanel(combatHud.skin());

    /** Luồng đấu tay đôi + xếp hạng PvP (arena.proto) — xem {@link DuelCoordinator}. */
    private final DuelCoordinator duel;
    /** Khung chọn hệ phái ở Lv10 (phím H) — mượn skin của {@link #combatHud} như {@link #chatPanel}. */
    private final FactionPanel factionPanel = new FactionPanel(combatHud.skin());

    /** Luồng chọn hệ phái (opcode 206/207) — xem {@link FactionCoordinator}. */
    private final FactionCoordinator faction;

    /** Luồng hầm ngục (opcode 1500-1503) — nút nằm trong {@link #partyPanel}, xem {@link DungeonCoordinator}. */
    private final DungeonCoordinator dungeon;

    /** Hai gói mức kết nối (PONG, server tắt) — không có cửa sổ, xem {@link ConnectionCoordinator}. */
    private final ConnectionCoordinator connection;
    /** Hòm thư (phím M) — mượn skin của {@link #combatHud} như {@link #chatPanel}. */
    private final MailPanel mailPanel = new MailPanel(combatHud.skin());

    /** Luồng thư (đọc/nhận/xoá/soạn + mạng) — xem {@link MailCoordinator}. */
    private final MailCoordinator mail;

    /** Túi đồ + trang bị (phím I) — mượn skin của {@link #combatHud} như {@link #mailPanel}. */
    private final InventoryPanel inventoryPanel = new InventoryPanel(combatHud.skin());

    /** Luồng túi đồ (mặc/gỡ/liệt kê + mạng) — xem {@link InventoryCoordinator}. */
    private final InventoryCoordinator inventory;
    /** Cửa hàng (phím B, hoặc từ hội thoại NPC) — mượn skin của {@link #combatHud} như {@link #mailPanel}. */
    private final ShopPanel shopPanel = new ShopPanel(combatHud.skin());

    /** Luồng cửa hàng (mua/bán + catalog/vàng + mạng) — xem {@link ShopCoordinator}. */
    private final ShopCoordinator shop;

    /** Giao dịch giữa hai người chơi (phím N) — mượn skin của {@link #combatHud} như {@link #mailPanel}. */
    private final TradePanel tradePanel = new TradePanel(combatHud.skin());

    /** Luồng giao dịch (mời/chào/xác nhận + mạng) — xem {@link TradeCoordinator}. */
    private final TradeCoordinator trade;
    /** Nhật ký nhiệm vụ (phím J, ADR-035) — mượn skin của {@link #combatHud} như {@link #mailPanel}. */
    private final QuestLogPanel questLogPanel = new QuestLogPanel(combatHud.skin());

    /** Vòng đời nhiệm vụ: nhận → hoàn thành → lĩnh thưởng — xem {@link QuestCoordinator}. */
    private final QuestCoordinator quests;

    /**
     * Catalog vật phẩm (ADR-035, 509/510) — DỮ LIỆU NỀN không có cửa sổ riêng, mọi giao diện đụng
     * vật phẩm tra vào đây để hiện tên thay vì "Vật phẩm #7". Xem {@link ItemCatalogCoordinator}.
     */
    private final ItemCatalogCoordinator itemCatalog;

    /** Số vàng của chính người chơi (ADR-035, 209) — cũng là dữ liệu nền, xem {@link GoldCoordinator}. */
    private final GoldCoordinator gold = new GoldCoordinator();

    /**
     * Sổ đăng ký MỘT chỗ cho mọi khung HUD — vẽ/đổi kích thước/dọn/định tuyến gói/cổng phím. Xem
     * {@link PanelHub} về lý do tồn tại và về thứ tự đăng ký.
     */
    private final PanelHub panels = new PanelHub();

    /** Xem {@link #buildInputProcessor()} về thứ tự nuốt input. */
    private final InputMultiplexer inputProcessor;

    /** Phím → ý định gửi lên server (đi lại/nhảy/đánh/skill) — xem {@link LocalIntentSender}. */
    private final LocalIntentSender intents;

    /**
     * @param context {@link EcsWorld} + {@link InputMapper} gộp lại (CODING-STANDARDS
     *     ParameterNumber max=4) — xem {@link PlayContext}.
     * @param character {@code CharacterSummary} của CHÍNH người chơi, nhận từ
     *     {@code CharacterSelectScreen} lúc chọn/tạo xong. Mang theo {@code faction} vì server
     *     KHÔNG gửi {@link CharacterSpawnedEvent} cho chính mình (đã có {@code MoveResponse}), nên
     *     đây là nguồn duy nhất để biết phải vẽ mình bằng model nào; mang theo {@code id} để nhận ra
     *     {@link AttackResultEvent}/{@link SkillResultEvent} nào đang nhắm vào MÌNH thay vì quái
     *     ({@link WorldConstants#LOCAL_CHARACTER_ID} chỉ là sentinel nội bộ cho render, không phải id thật).
     * @param enterWorld chính gói {@code SelectCharacterResponse} vừa nhận — mang máu/máu tối đa
     *     của người chơi lúc vào world (ADR-036 hậu kiểm). Truyền cả gói thay vì hai số rời vì
     *     constructor đã chạm ngưỡng Checkstyle {@code ParameterNumber} (max=4), và vì đây cùng
     *     khuôn với {@code character}: một message protocol đi thẳng vào màn hình.
     */
    public WorldScreen(
            PlayContext context,
            GameConnection connection,
            CharacterSummary character,
            SelectCharacterResponse enterWorld) {
        this.world = context.world();
        this.inputMapper = context.inputMapper();
        this.sender = new WorldCommandSender(connection);
        this.localCharacterId = character.getId();
        this.characterLevel = character.getLevel();
        this.combatHudController = new CombatHudController(character.getId(), character.getFaction());
        // Máu tối đa phụ thuộc trang bị từ ADR-036 nên KHÔNG còn là hằng số client tự biết — lấy
        // ngay của server, đừng đợi sự kiện đầu tiên (xem CombatHudController#onEnterWorld).
        this.combatHudController.onEnterWorld(enterWorld.getCurrentHp(), enterWorld.getMaxHp());
        this.combatAnimationTrigger =
                new CombatAnimationTrigger(models, localCharacterId, WorldConstants.LOCAL_CHARACTER_ID);
        this.remoteCharacters = new RemoteCharacterPresenter(context.world(), models);
        installMovementSystems();
        this.localPlayer = spawnLocalPlayer(enterWorld);

        // SAU khi localPlayer tồn tại: presenter giữ tham chiếu thẳng tới PositionComponent của
        // chính người chơi (xem javadoc constructor của nó).
        this.combatFeedback = new CombatFeedbackPresenter(
                localCharacterId, POSITION.get(localPlayer), combatHudController, combatHud.font());

        // SAU combatFeedback: quests mượn nó để báo khi hoàn thành/lĩnh thưởng.
        this.quests = new QuestCoordinator(combatHud, questLogPanel, sender, combatFeedback);
        this.dialogue = new DialogueCoordinator(dialoguePanel, sender, quests);
        this.chat = new ChatCoordinator(chatPanel, sender);
        // SAU chat: cả ba coordinator dưới đây mượn nhật ký chat/chữ nổi làm chỗ báo lý do thất bại.
        this.party = new PartyCoordinator(partyPanel, sender, chat);
        this.friends = new FriendCoordinator(friendPanel, sender, combatFeedback, panels::noneOpen);
        this.guild = new GuildCoordinator(guildPanel, sender, panels::noneOpen);
        this.duel = new DuelCoordinator(duelPanel, combatHud, sender, combatFeedback);
        this.dungeon = new DungeonCoordinator(partyPanel, combatHud, sender, chat);
        this.connection = new ConnectionCoordinator(combatHud, chat);
        this.faction = new FactionCoordinator(
                factionPanel,
                sender,
                combatFeedback,
                new LocalCharacterAppearance(models, combatHudController, WorldConstants.LOCAL_CHARACTER_ID));
        this.itemCatalog = new ItemCatalogCoordinator(sender);
        this.mail = new MailCoordinator(mailPanel, sender, combatFeedback, panels::noneOpen);
        this.inventory = buildInventory();
        this.shop = buildShop();
        this.trade = new TradeCoordinator(tradePanel, sender, itemCatalog, panels::noneOpen);
        this.intents = buildIntents();
        registerPanels();
        wireHud(character);
        this.inputProcessor = buildInputProcessor();
    }

    /**
     * Hai hệ di chuyển của client, chạy CÙNG công thức với server để dự đoán cục bộ (CLIENT.md §6)
     * khớp — ADR-033 cho trục dọc: {@code GRAVITY_MPS2} khớp, và CÙNG bộ bệ với server
     * ({@code WorldFacade.DEMO_PLATFORMS}) là BẮT BUỘC, xem javadoc {@link DemoPlatforms#physics()}
     * về hậu quả nếu thiếu (rơi xuyên bệ).
     */
    private void installMovementSystems() {
        world.addSystem(new MovementSystem());
        VerticalMovementSystem verticalMovementSystem = new VerticalMovementSystem(WorldConstants.GRAVITY_MPS2);
        verticalMovementSystem.setPlatforms(DemoPlatforms.physics());
        world.addSystem(verticalMovementSystem);
    }

    /**
     * Dựng nhân vật của CHÍNH mình ở đúng chỗ server nói (ADR-041).
     *
     * <p>⚠️ Trước 2026-09-05 hàm này dùng {@code new PositionComponent()} — tức (0,0) — với javadoc
     * giải thích rằng "chưa có persistence vị trí, MoveResponse đầu tiên sẽ tự sửa nếu lệch". Lúc
     * viết câu đó ĐÚNG. Persistence vị trí sau đó đã có thật, còn giả định thì ở lại; và cái van an
     * toàn của nó ("MoveResponse tự sửa") chính là thứ giấu lỗi đi, vì gói đó chỉ tới khi người chơi
     * BẤM PHÍM ĐI. Ai đứng yên thì thấy sai chỗ: playtest đăng nhập ở x=8, x=-3, x=-5 cho ra khung
     * hình giống nhau từng pixel, và con quái đứng ngay dưới chân thì đánh trúng được nhưng không
     * hiện trên màn hình (nó được vẽ ở toạ độ THẬT, còn camera thì ở 0).
     *
     * <p>Bài học chung: một giả định có ghi chú tử tế vẫn mục khi tiền đề của nó biến mất.
     */
    private KEntity spawnLocalPlayer(SelectCharacterResponse enterWorld) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = enterWorld.getSpawnX();
        position.y = enterWorld.getSpawnY();
        // prev = hiện tại: không có gì để nội suy ở frame đầu, để lệch sẽ thấy nhân vật trượt một
        // đoạn từ (0,0) tới chỗ thật ngay khi vừa vào world.
        position.prevX = position.x;
        position.prevY = position.y;
        entity.add(position);
        entity.add(new VelocityComponent());
        return entity;
    }

    /**
     * Tầng "phím → ý định gửi lên server". PHẢI gọi SAU khi {@link #dialogue} đã gán: đánh và skill
     * bị chặn khi đang đọc lời thoại, còn đi lại/nhảy thì không — xem
     * {@link LocalIntentSender.LocalIntentContext#dialogueOpen()}.
     */
    private LocalIntentSender buildIntents() {
        return new LocalIntentSender(new LocalIntentSender.LocalIntentContext(
                inputMapper,
                sender,
                models,
                localPlayer,
                combatHudController,
                combatFeedback,
                panels::noneOpen,
                dialogue::isOpen));
    }

    /**
     * Tách khỏi constructor THUẦN để giữ nó dưới ngưỡng {@code MethodLength} của Checkstyle — cùng
     * lý do {@link #wireHud} — không phải đổi hành vi. {@link InventoryCoordinator.DataSources} gộp
     * {@code itemCatalog}+{@code gold}+{@code chat} vì constructor kia đã chạm ngưỡng
     * {@code ExcessiveParameterList} của PMD (config/pmd, minimum=5), cùng lý do {@link PlayContext}
     * tồn tại. PHẢI gọi SAU khi {@link #chat} đã gán: túi đồ báo "vừa nhặt được gì" vào nhật ký chat.
     */
    private InventoryCoordinator buildInventory() {
        return new InventoryCoordinator(
                inventoryPanel,
                sender,
                new InventoryCoordinator.DataSources(itemCatalog, gold, chat),
                panels::noneOpen);
    }

    /**
     * Tách khỏi constructor để giữ nó dưới ngưỡng {@code MethodLength} của Checkstyle (max=50) —
     * PHẢI gọi SAU khi {@link #itemCatalog} đã gán: cửa hàng tra tên vật phẩm và hiện số vàng qua
     * hai luồng nền đó (ADR-035), xem {@link ShopCoordinator.SharedFeeds}.
     */
    private ShopCoordinator buildShop() {
        ShopCoordinator.SharedFeeds feeds = new ShopCoordinator.SharedFeeds(itemCatalog, gold);
        return new ShopCoordinator(shopPanel, sender, feeds, panels::noneOpen);
    }

    /**
     * Đăng ký mọi khung HUD vào {@link #panels}. <b>Thứ tự ở đây là thứ tự nuốt input</b>, từ trên
     * xuống — {@link PanelHub} suy ra thứ tự vẽ (ngược lại) và thứ tự dọn tài nguyên (xuôi) từ đúng
     * một danh sách này, xem javadoc của nó.
     *
     * <p>Hội thoại đứng đầu vì đang nói chuyện mà bấm trúng nút Đánh phía sau, hoặc click xuyên qua
     * khung thoại để chọn mục tiêu, đều là hỏng. Các cửa sổ che GIỮA màn hình (Nhóm, Bạn bè, Bang
     * hội, Đấu, Hệ phái, Thư, Cửa hàng) đứng ngay sau vì chúng đè lên cả chat lẫn HUD; thứ tự GIỮA
     * chúng với nhau không quan trọng — {@link PanelHub#isAnyOpen()} đảm bảo không bao giờ có hai
     * cái cùng mở.
     *
     * <p>Cửa sổ bạn bè đứng TRƯỚC chat: cả hai mở ô nhập bằng một phím ở {@code keyUp}, và cái đứng
     * trước là cái duy nhất chặn được phím của cái kia (xem {@code FriendPanel#installKeyHandling}).
     * Chat đứng trên HUD vì click vào nhật ký chat không được rơi xuống tầng thế giới thành lệnh
     * chọn mục tiêu. HUD đăng ký CUỐI CÙNG vì hai lý do cùng chỉ về một hướng: nó phải nhận input
     * sau mọi cửa sổ, và nó là chủ của {@code KageSkin} mà mọi panel mượn, nên nó phải là thứ trả
     * tài nguyên sau cùng.
     *
     * <p>{@code quests} và {@code dungeon} không có cửa sổ riêng ({@link QuestCoordinator} vẽ vào
     * HUD, {@link DungeonCoordinator} vẽ vào khung Nhóm) nên chỉ đăng ký phần luồng gói. Quests đăng
     * ký TRƯỚC dialogue có chủ đích: {@code AcceptQuestResponse} có nghĩa với CẢ HAI (cập nhật
     * nhiệm vụ vs đóng khung thoại), và {@link PanelHub#dispatch} hỏi cả hai chứ không dừng ở cái
     * đầu tiên nhận.
     */
    private void registerPanels() {
        panels.register(itemCatalog);
        panels.register(gold);
        panels.register(questLogPanel, quests);
        panels.register(dialoguePanel, dialogue);
        panels.register(partyPanel, party);
        panels.register(friendPanel, friends);
        panels.register(guildPanel, guild);
        panels.register(duelPanel, duel);
        panels.register(factionPanel, faction);
        panels.register(mailPanel, mail);
        panels.register(inventoryPanel, inventory);
        panels.register(shopPanel, shop);
        panels.register(tradePanel, trade);
        panels.register(chatPanel, chat);
        panels.register(dungeon);
        panels.register(connection);
        panels.register(combatHud);
    }

    /**
     * Nối HUD và cổng mở-cửa-sổ. Tách khỏi constructor thuần để giữ nó dưới ngưỡng
     * {@code MethodLength} của Checkstyle — mỗi khung mới thêm vào lại đẩy constructor lên một dòng,
     * và nó đã chạm trần đúng ba lần trong một ngày.
     *
     * <p>{@link PanelHub#noneOpen()} là cổng CHUNG cho mọi khung mở bằng phím, kể cả chat. Truyền
     * cùng MỘT hàm cho tất cả là điều quan trọng: hai cực khác nhau ở hai chỗ đã sinh ra một lỗi thật
     * (cửa sổ chỉ mở được đúng lúc đáng lẽ phải bị chặn) — xem {@link PanelHub#isAnyOpen()}.
     *
     * @param character {@code CharacterSummary} của chính người chơi: cấp + hệ phái là hai thứ DUY
     *     NHẤT ngoài HUD cần nối ở đây (model 3D của mình vẽ theo hệ phái, và bước chọn hệ phái ở
     *     Lv10 mở ra theo cấp) — cả hai đến từ server, không phải client suy.
     */
    private void wireHud(CharacterSummary character) {
        combatHud.setSkillLabel(combatHudController.skillNameKey());
        combatHud.setOnSkillClicked(intents::onSkillButtonClicked);
        combatHud.setOnAttackClicked(intents::onAttackButtonClicked);
        partyPanel.setCanOpen(panels::noneOpen);
        chatPanel.setCanOpen(panels::noneOpen);
        duelPanel.setCanOpen(panels::noneOpen);
        questLogPanel.setCanOpen(panels::noneOpen);
        // Nút Bán sống trong túi đồ (chỉ ở đó mới có inventory_item.id THẬT mà SellItemCommand đòi)
        // nhưng lệnh Bán thuộc về cửa hàng (nó giữ khoá idempotency của ADR-025). Hai luồng không
        // biết nhau; chỗ này nối chúng lại.
        inventory.setOnSellRequested(shop::sellOne);
        // Cùng khuôn nút Bán: món đồ nằm ở túi đồ, còn phiên giao dịch nằm ở luồng giao dịch.
        inventory.setOnOfferRequested(trade::offerItem, trade::inTrade);
        // ADR-043 — mặc/gỡ/tôi luyện đổi MÁU TỐI ĐA (ADR-036), và túi đồ là nơi nhận được con
        // số mới trước tiên. Thiếu dòng này thì vạch máu kẹt ở giá trị lúc VÀO WORLD cho tới lần
        // hồi sinh đầu tiên — playtest 2026-09-05 thấy `100 / 100` dù đã mặc trọn bộ +18 máu.
        inventory.setOnMaxHpChanged(combatHudController::onOwnMaxHpChanged);
        // Cổng của khung chọn hệ có THÊM một vế so với các khung khác — xem
        // FactionCoordinator#setOpenGate (chỉ bày lối vào cho nhân vật còn được mời chọn).
        faction.setOpenGate(panels::noneOpen);
        faction.onCharacterState(character.getLevel(), character.getFaction());
        // Vứt sạch view của world cũ rồi xin lại vị trí của chính mình — LÝ DO đầy đủ (giao thức
        // không có gói "đổi World" nào) nằm ở DungeonCoordinator#setOnWorldChanged. Gửi lại ĐÚNG
        // hướng đang đi chứ không phải 0: MoveResponse chỉ được phát như ack cho MoveCommand, nên
        // đây là cách duy nhất hỏi "tôi đang ở đâu" bằng gói đã có, mà không đổi ý định nào cả.
        dungeon.setOnWorldChanged(() -> {
            remoteCharacters.clear();
            npcPresenter.clear();
            monsterPresenter.clear(combatHudController.monsters().keySet());
            combatHudController.clearMonsters();
            intents.resendDirection();
        });
        // Bậc trang phục của chính mình chưa suy được từ đâu (xem CharacterAppearance phía server) —
        // dùng đúng giá trị mặc định server đang phát cho người khác, để mình và người khác nhìn
        // thấy mình giống nhau. Đổi hệ phái giữa trận đi lại ĐÚNG đường này
        // (LocalCharacterAppearance#applyFaction), nên nó nằm cạnh phần wiring.
        models.addCharacter(
                WorldConstants.LOCAL_CHARACTER_ID,
                CharacterModelKey.of(character.getFaction(), CostumeTier.COSTUME_TIER_UNSPECIFIED));
    }

    /** Thứ tự nuốt input do {@link #registerPanels()} quyết định; thế giới luôn đứng cuối cùng. */
    private InputMultiplexer buildInputProcessor() {
        InputMultiplexer multiplexer = new InputMultiplexer(panels.stages());
        // Tầng thế giới đứng SAU mọi Stage: Stage chỉ trả true ở touchDown khi có actor thật nhận cú
        // click, nên bấm vào vùng trống của HUD vẫn rơi xuống đây đúng như mong đợi.
        multiplexer.addProcessor(new WorldTargetPicker());
        return multiplexer;
    }

    @Override
    public void show() {
        // Input di chuyển/nhảy/skill vẫn poll qua InputMapper (không đổi) — chỉ CLICK chọn mục tiêu
        // mới cần InputProcessor, vì đó là sự kiện tại một điểm cụ thể chứ không phải trạng thái
        // giữ/nhả để poll được.
        Gdx.input.setInputProcessor(inputProcessor);
        // Hỏi catalog ở show() chứ không phải constructor: lúc dựng màn hình kết nối có thể chưa sẵn
        // sàng, còn show() là thời điểm màn hình thật sự bắt đầu sống.
        itemCatalog.request();
        quests.requestLog();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        // Khung hội thoại không được sống sót qua một lần rời màn hình — quay lại sẽ thấy nó treo ở
        // node cũ trong khi server đã quên sạch cuộc nói chuyện đó.
        panels.resetAll();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        models.resize(camera.viewportWidth, camera.viewportHeight);
        panels.resize(width, height);
        combatFeedback.resize(width, height);
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        processInboundPackets();
        intents.tick(fixedDeltaTime);
        combatHudController.tick(fixedDeltaTime);
        remoteCharacters.tick(fixedDeltaTime);
        monsterPresenter.tick(fixedDeltaTime);
    }

    private void processInboundPackets() {
        PacketCodec.Frame frame;
        while ((frame = sender.pollInbound()) != null) {
            dispatch(frame);
        }
    }

    private void dispatch(PacketCodec.Frame frame) {
        try {
            if (panels.dispatch(frame)) {
                return;
            }
            int opcode = frame.header().opcode();
            byte[] payload = frame.payload();
            if (!dispatchMovement(opcode, payload)) {
                dispatchCombat(opcode, payload);
            }
            // Opcode khác (VD PongResponse của heartbeat) — không thuộc gameplay world, bỏ qua.
        } catch (InvalidProtocolBufferException e) {
            LOG.warn(
                    "Payload protobuf không hợp lệ từ server: opcode={}",
                    frame.header().opcode(),
                    e);
        }
    }

    // Tách dispatch() thành 2 nhóm (di chuyển/spawn nhân vật vs quái/combat) THUẦN để hạ cyclomatic
    // complexity dưới ngưỡng PMD (methodReportLevel=10) — 1 chuỗi if-else 9 nhánh vượt ngưỡng, không
    // phải đổi hành vi.
    /** @return {@code true} nếu opcode thuộc nhóm di chuyển/spawn nhân vật, đã xử lý xong. */
    private boolean dispatchMovement(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
            onMoveResponse(MoveResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE) {
            remoteCharacters.onSpawned(CharacterSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE) {
            remoteCharacters.onMoved(CharacterMovedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT_VALUE) {
            remoteCharacters.onAppearanceChanged(CharacterAppearanceChangedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE) {
            remoteCharacters.onDespawned(CharacterDespawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE) {
            onCharacterRespawned(CharacterRespawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
            onExperienceChanged(ExperienceChangedEvent.parseFrom(payload));
        } else {
            return false;
        }
        return true;
    }

    private void dispatchCombat(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            onMonsterSpawned(MonsterSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE) {
            MonsterDespawnedEvent despawned = MonsterDespawnedEvent.parseFrom(payload);
            combatHudController.onMonsterDespawned(despawned);
            monsterPresenter.onDespawned(despawned.getMonsterId());
        } else if (opcode == Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE) {
            MonsterMovedEvent moved = MonsterMovedEvent.parseFrom(payload);
            combatHudController.onMonsterMoved(moved);
            monsterPresenter.onMoved(moved);
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            onAttackResult(AttackResultEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            onSkillResult(SkillResultEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE) {
            npcPresenter.onSpawned(NpcSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_NPC_DESPAWNED_EVENT_VALUE) {
            npcPresenter.onDespawned(NpcDespawnedEvent.parseFrom(payload).getNpcId());
        }
    }

    /**
     * Kết quả 1 đòn đánh thường THẬT — gửi cho MỌI nhân vật thấy mục tiêu trong AoI (combat.proto),
     * nên {@code attacker_entity_id}/{@code target_entity_id} có thể là CHÍNH mình, người khác, hoặc
     * quái. HP/hồi chiêu (HUD) và animation (model 3D) là 2 mối quan tâm tách biệt — xem
     * {@link CombatAnimationTrigger}.
     */
    private void onAttackResult(AttackResultEvent event) {
        combatHudController.onAttackResult(event);
        combatAnimationTrigger.onAttackResult(event);
        combatFeedback.onAttackResult(event);
    }

    /** Kết quả 1 lần dùng skill THẬT — cùng hình dạng {@link AttackResultEvent}. */
    private void onSkillResult(SkillResultEvent event) {
        combatHudController.onSkillResult(event);
        combatAnimationTrigger.onSkillResult(event);
        combatFeedback.onSkillResult(event);
    }

    private void onMoveResponse(MoveResponse response) {
        PositionComponent pos = POSITION.get(localPlayer);
        float dx = response.getX() - pos.x;
        float dy = response.getY() - pos.y;
        float dz = response.getZ() - pos.z;
        if (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) > WorldConstants.RECONCILE_SNAP_THRESHOLD) {
            // Lệch lớn (mất gói MoveCommand/JumpCommand trước đó, hoặc server điều chỉnh) — sửa
            // NGAY theo server, không tranh cãi (Điều 15, CLIENT.md §6).
            pos.x = response.getX();
            pos.y = response.getY();
            pos.prevX = pos.x;
            pos.prevY = pos.y;
            pos.z = response.getZ();
            pos.prevZ = pos.z;
            pos.grounded = response.getGrounded();
            if (pos.grounded) {
                // Server nói đang đứng đất ⇒ hạn mức nhảy đôi bên server CHẮC CHẮN đã reset
                // (VerticalMovementSystem xoá lúc đáp đất) — theo server luôn, tránh trường hợp
                // client tưởng còn nợ 1 lần nhảy giữa không trung dù đã đứng trên đất.
                pos.airJumpsUsed = 0;
            }
            // vz KHÔNG snap theo server — MoveResponse không mang vz (chỉ x/y/z/grounded, xem
            // movement.proto). Dự đoán cục bộ tiếp tục dùng vz hiện có, tự hội tụ đúng quỹ đạo ở
            // các tick sau vì VerticalMovementSystem chạy CÙNG công thức cả 2 phía (ADR-033) —
            // follow-up nếu số liệu thật cho thấy cần đồng bộ cả vz.
        }
    }

    /**
     * CHÍNH nhân vật vừa hồi sinh (HP về 0 trước đó) — không có {@code character_id} trong packet
     * (cùng quy ước "ngầm định chính mình" như {@link #onMoveResponse}). Snap vị trí NGAY, không
     * nội suy — hồi sinh vốn là dịch chuyển tức thời, khác di chuyển thường.
     */
    private void onCharacterRespawned(CharacterRespawnedEvent event) {
        PositionComponent pos = POSITION.get(localPlayer);
        pos.x = event.getX();
        pos.y = event.getY();
        pos.prevX = pos.x;
        pos.prevY = pos.y;
        // ADR-033 — hồi sinh LUÔN ở mặt đất (WorldFacade.respawnCharacter), reset cả trục dọc: có
        // thể chết giữa lúc đang lơ lửng (z>0, grounded=false), không reset sẽ hồi sinh "bay".
        pos.z = 0f;
        pos.prevZ = 0f;
        pos.vz = 0f;
        pos.grounded = true;
        pos.airJumpsUsed = 0;
        combatHudController.onRespawn(event.getCurrentHp(), event.getMaxHp(), event.getCurrentChi());
        models.resetAfterRespawn(WorldConstants.LOCAL_CHARACTER_ID);
    }

    /**
     * Vạch kinh nghiệm/cấp của CHÍNH mình vừa đổi (2026-08-09) — server gửi cả lúc vừa vào world
     * (để vạch có số ngay) lẫn mỗi lần nhận EXP. Client KHÔNG tự tính đường cong: mọi con số ở đây
     * là do server đưa (Điều 15).
     */
    private void onExperienceChanged(ExperienceChangedEvent event) {
        characterLevel = event.getLevel();
        combatHud.setExperience(event.getExpIntoLevel(), event.getExpForNextLevel());
        // Cấp là ĐIỀU KIỆN của bước chọn hệ phái (Lv10, GDD §4) — đây là gói tin DUY NHẤT báo cấp
        // đổi giữa phiên chơi, nên cũng là chỗ duy nhất lối vào đó có thể mở ra.
        faction.onLevel(event.getLevel());
        if (event.getLeveledUp()) {
            combatFeedback.showLevelUp();
        }
    }

    private void onMonsterSpawned(MonsterSpawnedEvent event) {
        long autoTargetId = combatHudController.onMonsterSpawned(event);
        monsterPresenter.onSpawned(event);
        if (autoTargetId != 0L) {
            sender.setTarget(autoTargetId);
        }
    }

    private boolean selectTargetAt(int screenX, int screenY) {
        // Lật y sang quy ước GL của WorldToScreen (gốc DƯỚI-trái).
        float glScreenY = Gdx.graphics.getHeight() - screenY;
        WorldToScreen projector = models.isEnabled() ? modelProjector : fallbackProjector;
        long monsterId = entityPicker.pick(combatHudController.monsters(), screenX, glScreenY, projector);
        if (monsterId != 0L) {
            if (combatHudController.selectTarget(monsterId)) {
                sender.setTarget(monsterId);
            }
            return true;
        }
        // Người chơi khác đứng CUỐI trong ba loại: quái và NPC là thứ click cả ngày, còn thách đấu
        // là việc hiếm. Click chọn chỉ CHỌN chứ không gửi lời thách đấu nào, và id 0 (click vào chỗ
        // trống) được xử lý bên trong coordinator — xem {@link DuelCoordinator#onCharacterPicked}.
        return talkToNpcAt(screenX, glScreenY, projector)
                || duel.onCharacterPicked(
                        entityPicker.pick(remoteCharacters.characters(), screenX, glScreenY, projector));
    }

    private boolean talkToNpcAt(float screenX, float glScreenY, WorldToScreen projector) {
        long npcId = entityPicker.pick(npcPresenter.npcs(), screenX, glScreenY, projector);
        if (npcId == 0L) {
            return false; // click vào chỗ trống — GIỮ NGUYÊN mục tiêu cũ, không bỏ chọn.
        }
        NpcPresenter.NpcState npc = npcPresenter.npcs().get(npcId);
        // Hai khung nhập liệu cùng mở là một cái bẫy phím: ESC sẽ đóng khung thoại (nó đứng trước
        // trong InputMultiplexer) chứ không đóng ô chat đang giữ keyboard focus.
        panels.closeAll();
        dialogue.talkTo(npc.code());
        return true;
    }

    @Override
    public void render(float alpha) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        // Depth buffer bắt buộc từ ADR-031: nhân vật là hình học 3D thật, thiếu depth thì thứ tự vẽ
        // quyết định cái nào che cái nào — đúng thứ 2.5D phải bỏ.
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (models.isEnabled()) {
            renderModels(alpha);
            renderMonsters(models.combined());
            DemoPlatforms.render(shapeRenderer, models.combined());
            // VFX/số damage vẽ SAU cùng trong nhánh 3D — luôn hiện đè lên nhân vật/địa hình, đúng
            // thứ tự "nhân vật vẽ trước, VFX vẽ đè sau" của RENDERING.md §2.
            combatFeedback.render(Gdx.graphics.getDeltaTime(), models.combined(), modelProjector);
        } else {
            renderPlaceholderBoxes(alpha);
            renderMonsters(camera.combined);
            DemoPlatforms.render(shapeRenderer, camera.combined);
            combatFeedback.render(Gdx.graphics.getDeltaTime(), camera.combined, fallbackProjector);
        }

        syncCombatHud();
        panels.render(Gdx.graphics.getDeltaTime());
    }

    /**
     * Phần vẽ quái mà {@link WorldModelRenderer} không lo được — hộp thay thế cho mã quái chưa có
     * art, và dấu mục tiêu. Dùng ĐÚNG ma trận chiếu của nhánh render đang hoạt động để không lệch
     * không gian chiếu so với model.
     */
    private void renderMonsters(Matrix4 projection) {
        monsterPresenter.render(
                shapeRenderer, projection, combatHudController.monsters(), combatHudController.currentTargetId());
        npcPresenter.render(shapeRenderer, projection);
    }

    private void syncCombatHud() {
        combatHud.setPlayerHp(combatHudController.playerHp(), combatHudController.playerMaxHp());
        combatHud.setPlayerChi(combatHudController.playerChi(), combatHudController.playerMaxChi());
        combatHud.setLevel(characterLevel);
        CombatHudController.MonsterState target = combatHudController.targetState();
        if (target != null) {
            combatHud.setTarget(target.code(), target.hp(), target.maxHp());
        } else {
            combatHud.clearTarget();
        }
        combatHud.setSkillState(combatHudController.skillNameKey(), combatHudController.skillCooldownRemaining());
        combatHud.setSkillReady(combatHudController.canUseSkill());
    }

    /** Nhánh 3D (ADR-031) — model glTF qua camera orthographic khoá góc. */
    private void renderModels(float alpha) {
        float localX = interpolatedX(localPlayer, alpha);
        float localY = interpolatedY(localPlayer, alpha);
        float localZ = interpolatedZ(localPlayer, alpha);
        models.setPosition(WorldConstants.LOCAL_CHARACTER_ID, localX, localY, localZ);
        models.setMoving(WorldConstants.LOCAL_CHARACTER_ID, isLocalPlayerMoving());
        // ADR-033 — grounded là trạng thái dự đoán cục bộ/xác nhận qua MoveResponse, KHÔNG nội suy
        // (giống lý do isLocalPlayerMoving đọc VelocityComponent thẳng, không qua interpolation).
        models.setAirborne(WorldConstants.LOCAL_CHARACTER_ID, !POSITION.get(localPlayer).grounded);
        remoteCharacters.sync(alpha);
        // Quái đi cùng lượt vẽ với nhân vật (cùng Scene list, cùng camera) — chỉ nguồn dữ liệu là
        // khác: chúng không có entity ECS phía client, xem MonsterPresenter.sync.
        monsterPresenter.sync(combatHudController.monsters(), localX);
        npcPresenter.sync(localX);
        models.follow(localX, localY);
        models.render(Gdx.graphics.getDeltaTime());
    }

    /** Vận tốc dự đoán cục bộ (CLIENT.md §6) của chính mình — đủ để quyết định idle vs run. */
    private boolean isLocalPlayerMoving() {
        VelocityComponent velocity = VELOCITY.get(localPlayer);
        return CharacterAnimationClips.isMoving(velocity.x, velocity.y);
    }

    /**
     * Hình thay thế thời trước ADR-031. Còn giữ vì model là tài sản sinh ra, không commit
     * ([ART-PIPELINE §7](docs/14-art/ART-PIPELINE.md)) — bản checkout chưa chạy pipeline art vẫn
     * phải chơi được, không phải màn hình đen.
     */
    private void renderPlaceholderBoxes(float alpha) {
        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(Color.CORAL);
        drawInterpolated(localPlayer, alpha);

        shapeRenderer.setColor(Color.SKY);
        remoteCharacters.renderPlaceholder(this::drawInterpolated, alpha);

        shapeRenderer.end();
    }

    private static float interpolatedX(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevX + ((pos.x - pos.prevX) * alpha);
    }

    private static float interpolatedY(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevY + ((pos.y - pos.prevY) * alpha);
    }

    /** ADR-033 — nội suy trục dọc giống hệt {@link #interpolatedX}/{@link #interpolatedY}. */
    private static float interpolatedZ(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevZ + ((pos.z - pos.prevZ) * alpha);
    }

    private void drawInterpolated(KEntity entity, float alpha) {
        shapeRenderer.rect(
                interpolatedX(entity, alpha) - (WorldConstants.BOX_SIZE / 2f),
                interpolatedY(entity, alpha) - (WorldConstants.BOX_SIZE / 2f),
                WorldConstants.BOX_SIZE,
                WorldConstants.BOX_SIZE);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        models.dispose();
        // TRƯỚC combatHud: combatFeedback mượn font của HUD, dispose HUD trước là kéo font ra khỏi
        // dưới chân nó (dù ở đây nó không dùng font lúc dispose, giữ đúng thứ tự sở hữu).
        combatFeedback.dispose();
        // Panel mượn skin của CombatHud nên phải trả tài nguyên trước chủ skin — PanelHub dọn
        // theo đúng thứ tự đăng ký, và HUD đăng ký cuối cùng chính vì vậy.
        panels.dispose();
    }

    /**
     * Tầng input DƯỚI HUD trong {@link #inputProcessor} — chỉ quan tâm click chuột trái vào thế
     * giới. Không xử lý phím: mọi phím vẫn poll qua {@link InputMapper} như trước (CLIENT.md §5),
     * lớp này không được biết mã phím cụ thể.
     */
    private final class WorldTargetPicker extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            return selectTargetAt(screenX, screenY);
        }
    }
}

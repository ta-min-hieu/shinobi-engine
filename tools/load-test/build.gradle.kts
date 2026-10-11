// tools:load-test — client giả lập nhiều người chơi qua SOCKET TCP THẬT (BENCHMARK.md §5, Phase 9
// lát cắt 2). KHÔNG chạy trong game (REPOSITORY-STRUCTURE.md §2 "tools/* — CLI build-time"),
// KHÔNG phụ thuộc server-gateway/server-world (chỉ nói chuyện qua chính giao thức dây — đúng cách
// người chơi thật kết nối, không gọi tắt qua API nội bộ).
plugins {
    id("kage.quality-conventions")
    application
}
dependencies {
    implementation(project(":shared:shared-protocol"))
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}
application {
    mainClass.set("dev.kage.tools.loadtest.LoadTestRunner")
}

// gameProbe — client HEADLESS QUAN SÁT (ROADMAP mục 58). Cùng module vì nó nói ĐÚNG tầng dây của
// SimulatedPlayer (GameConnection dùng chung); thêm module mới cho việc này là chuyện phải hỏi chủ
// dự án (CLAUDE.md §4) và cũng không mua thêm được gì.
//
// Chạy:
//   ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --moveToX=24 --observeSeconds=15"
// hoặc (khi shell nuốt mất --args):
//   ./gradlew :tools:load-test:gameProbe -Pargs="--username=kagetest --moveToX=24"
//
// Kịch bản NHIỆM VỤ (2026-09-06, probe.QuestScenario) — vòng đời quest đo được không cần giao diện,
// dùng đúng opcode đã có (602/603/604/605/606 hội thoại, 607/610/611 nhật ký, 608/609 lĩnh thưởng,
// 507/508 túi đồ). Mỗi bước của --dialogueChoices là CHỈ SỐ lựa chọn hoặc KHOÁ (action/label_key):
//   ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --characterId=3 \
//       --talkToNpc=town_elder --dialogueChoices=accept_quest:kill_training_dummies --observeSeconds=1"
//   ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --characterId=3 \
//       --claimQuest=kill_training_dummies --observeSeconds=1"
//   ./gradlew :tools:load-test:gameProbe --args="--username=kagetest --questReport=true --observeSeconds=1"
//
// Kịch bản NHÓM hai người (2026-10-11, probe.PartyScenario) — HAI probe trên HAI TÀI KHOẢN (một tài
// khoản chỉ một nhân vật online). Bên nhận chạy TRƯỚC; --partyReport in ai là trưởng nhóm sau vòng
// quan sát. Nhóm sống theo DB: lượt sau cả hai vẫn ở trong nhóm, không cần mời lại.
//   ... --args="--username=aloha --characterId=4 --partyAccept=true --partyReport=true --observeSeconds=20"
//   ... --args="--username=kagetest --characterId=3 --partyInvite=Benkei --observeSeconds=5"
// --useItemCode nhận NHIỀU mã cách nhau dấu phẩy (uống liên tiếp — đo hồi chiêu vật phẩm).
//
// Kịch bản hai người khác (probe.TradeScenario / ArenaScenario) và lệnh lẻ (probe.SessionEdgeScenario):
//   --tradeAccept=true (chạy TRƯỚC) / --tradeRequest=<tên> / --tradeReport=true
//   --acceptDuel=true --acceptDuelDelaySeconds=7   (ngồi yên rồi mới nhận lời thách)
//   --unequipSlots=weapon,armor    --reloginInWorld=true [--reloginUsername=<tài khoản>]
//   --chatChannel=world --chatBody=dong1\ndong2    (chuỗi \n thành xuống dòng thật)
tasks.register<JavaExec>("gameProbe") {
    group = "verification"
    description = "Client headless quan sát: giải mã sự kiện world thành SỐ (vị trí quái, sát thương, vòng đời quest)"
    mainClass.set("dev.kage.tools.loadtest.probe.GameProbe")
    classpath = sourceSets["main"].runtimeClasspath
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" ").filter { it.isNotBlank() })
    }
}

// engine-scripting — sandbox chạy script (GraalJS — ADR-016). Chỉ server (Điều 15).
plugins {
    id("kage.quality-conventions")
}
dependencies {
    api(project(":engine:engine-ecs"))
    // api, không implementation: ScriptSandbox.eval() trả về Value (org.graalvm.polyglot) — public
    // API của module này, module gọi vào (server-world) cần Value ở COMPILE classpath.
    api(libs.graaljs.polyglot)
    runtimeOnly(libs.graaljs.js)
    // js-isolate-community CHƯA thêm — xem javadoc ScriptSandbox: đóng gói lỗi thật (thiếu
    // darwin-amd64, không variant-aware theo OS), cần quyết định nền tảng server mục tiêu trước.
}

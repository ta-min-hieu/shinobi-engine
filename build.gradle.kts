// Root build — cố ý TỐI THIỂU. Cấu hình thật nằm ở build-logic/ convention plugins
// và từng module. Root không khai báo dependency, không apply plugin cho allprojects
// (tránh coupling ngầm — Điều 12 Explicit over Implicit).

tasks.register("printModules") {
    group = "help"
    description = "Liệt kê module đã include (kiểm tra scaffold Phase 0)."
    doLast {
        subprojects.forEach { println(it.path) }
    }
}

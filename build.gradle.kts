// Root build — cố ý TỐI THIỂU. Cấu hình thật nằm ở build-logic/ convention plugins
// và từng module. Root không khai báo dependency, không apply plugin cho allprojects
// (tránh coupling ngầm — Điều 12 Explicit over Implicit).
//
// ⚠️ NGOẠI LỆ BẮT BUỘC (2026-07-30, BUILD.md "Bước 3"): `apply false` ở đây KHÔNG áp dụng
// Spotless cho root project (root vẫn không có hành vi gì) — chỉ ép Gradle resolve/load class
// Spotless đúng 1 LẦN DUY NHẤT ở classloader gốc. Thiếu dòng này, mỗi subproject áp
// `kage.quality-conventions` (qua precompiled script plugin) với tập plugin tổng thể KHÁC NHAU
// (vd `engine-ecs` có thêm `jmh`, `client-core` có thêm libGDX) khiến Gradle tạo classloader
// RIÊNG cho Spotless ở từng project — `SpotlessTaskService` (build service dùng chung) thấy 2
// Class khác nhau cho cùng 1 type → build fail thật
// ("Cannot set the value of task ... property 'taskService' ... loaded with
// InstrumentingVisitableURLClassLoader..."), xác nhận qua chạy `./gradlew build` thật, không suy
// đoán. Đây là giới hạn kỹ thuật đã biết của Spotless với convention plugin đa module (xem
// diffplug/spotless #747, gradle/gradle #17559) — không phải lựa chọn tuỳ ý, không mở rộng thêm
// plugin khác vào đây trừ khi gặp đúng lỗi tương tự.
plugins {
    alias(libs.plugins.spotless) apply false
}

tasks.register("printModules") {
    group = "help"
    description = "Liệt kê module đã include (kiểm tra scaffold Phase 0)."
    doLast {
        subprojects.forEach { println(it.path) }
    }
}

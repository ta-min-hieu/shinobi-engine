# client/

Module client. **Chỉ `client-desktop` được scaffold ở Phase 0.**

`client-android` và `client-ios` **chưa tạo** — cố ý (Điều 9). Chúng cần thiết lập ngoài (Android Gradle Plugin + SDK; RoboVM + máy macOS) là quyết định riêng, và thuộc ưu tiên #2/#3 sau khi Desktop ổn định ([TECHNOLOGY-STACK §9](../docs/01-architecture/TECHNOLOGY-STACK.md)). Tạo khi bắt đầu backend mobile — nhớ: chỉ chứa launcher + PlatformAdapter, không gameplay (Điều 28).

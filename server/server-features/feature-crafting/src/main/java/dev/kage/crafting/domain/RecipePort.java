package dev.kage.crafting.domain;

import java.util.List;

/** Danh sách công thức (ADR-079) — hiện thực ở server-database, đọc một lần (tham số). */
@FunctionalInterface
public interface RecipePort {

    /** Mọi công thức, theo thứ tự hiển thị. */
    List<Recipe> recipes();
}

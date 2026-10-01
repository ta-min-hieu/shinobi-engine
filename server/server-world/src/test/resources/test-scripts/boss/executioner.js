// Boss CHỈ dùng trong test (WorldFacadeTest): luôn tung heavy_strike với sát thương đủ hạ gục bất kỳ
// nhân vật nào ở đòn đầu — để đòn hạ gục chắc chắn đi qua handleBossResults chứ không qua đánh thường.
(function () {
    return { action: "heavy_strike", raw_damage: 100000 };
})()

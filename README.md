# Manhunt Compass (Fabric) — Minecraft 1.26.2

功能：
- 添加速通者（speedrunner）与猎人（hunter）角色。
- 为猎人提供一个可指向速通者位置的指南针（tracker_compass）。
- 猎人重生时会获得该指南针。
- 角色数据持久化到世界存档（world-specific），以实现多存档隔离。
- 命令：`/manhunt set_speedrunner <player>`、`/manhunt add_hunter <player>`、`/manhunt remove_hunter <player>`、`/manhunt list_roles`、`/manhunt clear_roles`、`/manhunt select_random_speedrunner [seed]`、`/manhunt auto_assign_hunters <count>`。

构建：
1. 编辑 `gradle.properties` 中的版本号（若有必要）。
2. 在项目根目录运行 `./gradlew build`（Windows：`gradlew.bat build`）。
3. 在 `build/libs` 中找到生成的 jar，复制到 Minecraft 的 `mods` 文件夹并启动服务端/客户端进行测试。

注意：
- 我改为使用世界存档的 PersistentState 来保存角色信息（多存档隔离）。如果你希望改回 Fabric config 目录或保存到其他位置，请告诉我。
- 如果你要我进一步完善（例如给指南针添加显示名称/说明、GUI 管理面板、权限控制等），我可以继续扩展。

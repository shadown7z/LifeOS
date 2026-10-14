# LifeOS 阶段 2：核心业务与本地存储

更新时间：2026-10-14

## 已完成

- 使用 Room 2.8.5 建立本地 SQLite 数据库 `lifeos.db`。
- 建立 `Habit`、`HabitRecord`、`Goal`、`Note`、`Tag`、`NoteTagRelation`、`JournalEntry` 实体和 DAO。
- `HabitRecord` 对 `(habitId, localDate)` 建唯一索引，按日期独立保存打卡。
- 首次空库自动创建四项默认习惯和一个当前阶段目标，不会在后续启动重复插入。
- 建立 Repository，DAO 只在数据层使用，UI 通过 ViewModel 的 `StateFlow` 获取状态。
- 首页今日习惯、完成数量、完成度和目标列表已切换到真实数据库数据。
- 打卡操作在协程中执行，新增和取消都能持久化。
- 目标、习惯、笔记支持新增、编辑和删除/停用确认；日记支持按日期保存四个引导问题。
- 设置页已接入 Storage Access Framework，支持完整 JSON 导出、导入预览、合并导入和覆盖恢复。
- JSON 恢复使用 Room 单事务，格式版本和应用名都会校验。
- 合并导入按稳定 ID 跳过已存在实体；标签名冲突会复用已有标签，避免覆盖现有数据。
- 数据库已从 v1 升到 v2，提供新增笔记、标签和日记表的显式 Migration。
- 目标和笔记列表支持关键词搜索；笔记详情支持单篇 Markdown 导出。
- 使用 KSP 生成 Room 代码，兼容当前 AGP 9.4 的内置 Kotlin。

## 验证结果

```text
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-configuration-cache
BUILD SUCCESSFUL

./gradlew.bat :app:lintDebug --no-configuration-cache
BUILD SUCCESSFUL
```

AndroidTest 中已加入：

- 不同日期的同一习惯记录互不覆盖。
- 同一习惯同一天重复写入只保留一条记录。

当前机器没有 `adb` 或可用设备，因此 AndroidTest 只完成编译，尚未在设备上执行。

## 当前限制

- 标签实体已预留，但标签编辑和筛选 UI 仍在阶段 3 完成。
- 多篇 Markdown ZIP、标签编辑、收藏/归档筛选仍在阶段 3 完成。
- 当前数据库版本为 2，正式结构变化必须增加 Migration，不能使用破坏性迁移。

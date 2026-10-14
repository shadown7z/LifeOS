# LifeOS

LifeOS 是一款本地优先、离线可用的 Android 个人生活管理工具，用于统一管理每日习惯、阶段目标、笔记、日记和个人数据备份。

## 产品方向

LifeOS 围绕五个动作设计：

- **Capture**：快速记录想法和信息
- **Organize**：整理笔记、目标和计划
- **Execute**：通过今日页面明确当天行动
- **Reflect**：用日记和回顾总结经验
- **Preserve**：导出、备份和恢复个人数据

第一版不需要账号，不依赖云服务，不上传用户数据。

## 当前功能

- 今日 Dashboard：日期、完成度、每日习惯、阶段重点和人生箴言
- 习惯：默认四项每日习惯、按日期打卡、编辑、停用
- 目标：新增、编辑、删除、下一步行动和关键词搜索
- 笔记：新增、编辑、删除、关键词搜索、单篇 Markdown 导出
- 笔记整理：分类、标签编辑与筛选、收藏和归档
- 笔记迁移：单篇 Markdown、全部笔记 ZIP 导出，Markdown 或 ZIP 导入预览与合并
- 日记：按日期保存四个可选反思问题
- 数据导出：通过 Android Storage Access Framework 导出完整 JSON
- 数据导入：JSON 格式校验、导入预览、合并导入和覆盖恢复
- 备份恢复：Room 事务写入，恢复失败时不执行部分替换
- 主题：浅色、深色和跟随系统，使用 DataStore 持久化选择

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- MVVM + ViewModel + StateFlow
- Room + SQLite
- DataStore Preferences
- Kotlin Coroutines / Flow
- KSP
- Storage Access Framework
- JUnit、Android Instrumentation Test、Android Lint

## 工程结构

```text
app/src/main/java/com/atlaes/lifeos/
├── MainActivity.kt                 # Compose 入口、导航和页面
├── LifeOsTheme.kt                  # 浅色和深色主题
├── LifeOsApplication.kt            # 应用级数据库和 Repository
├── LifeOsViewModel.kt              # 页面状态和业务协调
└── data/
    ├── LifeOsRepository.kt         # 数据访问和业务写入
    ├── LifeOsBackupService.kt      # JSON 备份、导入和 Markdown 导出
    ├── NoteMarkdownService.kt      # Markdown 和 ZIP 笔记迁移
    ├── ThemePreference.kt          # 主题偏好
    └── database/
        ├── LifeOsDatabase.kt       # Room 数据库和 Migration
        ├── LifeOsDaos.kt           # DAO
        └── LifeOsEntities.kt       # 数据实体
```

## 数据模型

当前 Room schema 版本为 `2`，包含：

- `Habit`：习惯定义
- `HabitRecord`：某习惯某一天的独立记录，`habitId + localDate` 唯一
- `Goal`：目标、状态、优先级和下一步行动
- `Note`：标题、正文、分类、收藏和归档字段
- `Tag`、`NoteTagRelation`：标签字典和笔记关联
- `JournalEntry`：按日期保存的日记

数据库升级必须使用显式 Migration，不能使用破坏性迁移替代正式数据迁移。

## 导出文件

完整备份使用版本化 JSON：

```json
{
  "formatVersion": 1,
  "appName": "LifeOS",
  "exportedAt": "2026-10-14T12:00:00Z",
  "entities": {
    "habits": [],
    "habitRecords": [],
    "goals": [],
    "notes": [],
    "tags": [],
    "noteTagRelations": [],
    "journalEntries": []
  },
  "preferences": {}
}
```

导入流程会先解析和校验格式，再显示实体数量。合并导入按稳定 ID 跳过已存在记录；标签名称冲突会复用已有标签。覆盖恢复会在用户确认后使用单个 Room 事务替换业务数据。

## 构建和测试

在 Windows PowerShell 中执行：

```powershell
./gradlew.bat :app:assembleDebug --no-configuration-cache
./gradlew.bat :app:testDebugUnitTest --no-configuration-cache
./gradlew.bat :app:assembleDebugAndroidTest --no-configuration-cache
./gradlew.bat :app:lintDebug --no-configuration-cache
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Room 仪器测试覆盖：

- 同一习惯在不同日期的记录不会互相覆盖
- 同一习惯同一天重复写入只保留一条记录
- JSON 备份能够保留中文、换行和引号

已在 `Medium_Phone` Android 模拟器运行 `:app:connectedDebugAndroidTest`，覆盖独立日期打卡、JSON 备份恢复、无效版本不覆盖现有数据、Markdown ZIP 往返和路径穿越拒绝。Debug APK 已在该模拟器安装并启动。尚未在真实手机上验证。

## 手动验证导航

底部导航包含：今日、目标、笔记、习惯、更多。日记和设置从“更多”进入。切换到任意底部入口后再点击“今日”，应直接回到 Dashboard，不应恢复到目标或其他旧页面。

## 当前未完成

- 多篇笔记的任意勾选导出入口（当前支持单篇与全部 ZIP）
- 目标状态、优先级和截止日期编辑，以及习惯历史统计
- 日记历史编辑与搜索
- 完整备份中的主题偏好（当前主题由 DataStore 单独保存）
- 真机回归测试与发布签名 APK

## 许可证和开源参考

LifeOS 当前代码为独立实现。项目调研参考了 Markor、Tasks、Loop Habit Tracker、Fossify Notes、Orgzly、Joplin 和 Standard Notes 的公开产品思路，没有直接复制其 GPL、AGPL 或许可证未明确的业务代码。若未来引入第三方代码，需要在引入前确认许可证并补充版权声明。


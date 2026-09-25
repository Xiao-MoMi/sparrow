# 旧模块未替代功能实现计划

更新日期：2026-09-24

状态：分批实施中。P01–P07、P09–P19、P25–P31、F02 与 F05 已实现并通过自动化测试；原版菜单交互、巡查传送与代理切服仍待上服验收。其余项目按表中状态推进。

## 1. 范围与当前基线

旧源码位于 `bukkit`、`common`。当前参与 Gradle 构建的是 `core`、`bukkit-proxy`、`common-files`。

旧命令注册表最初声明了 69 个命令类。合并玩家版与管理员版后，共 44 类功能；其中重载已有正式实现，本文覆盖剩余 **43 类业务功能**，包括只有底层能力可复用、尚无正式命令的功能。已清理其中 45 个已迁移命令类，旧注册表现保留 24 个命令类。

当前正式模块已有：

- 重载命令、模块启用／停用命令、模块列表管理入口。
- `Feature`、`FeatureManager`、`FeatureSettings` 和独立的 `features.yml`。
- 潜影盒快捷打开 `quick-shulker`，本计划不重复实现。
- 配置、翻译、调度、依赖加载、日志、在线玩家与基础消息发送能力。
- SQL／Mongo 数据库连接管理基础；这不代表旧业务数据已经有存储实现。

本文中的功能 ID 是规划标识，沿用旧命令功能名便于追溯，不代表最终命令用法。玩家版和管理员版的入口、权限与参数在对应功能实现时分别确定。

## 2. 分类及实现约定

| 类型 | 判断依据 | 配置与开关 | 生命周期责任 |
| --- | --- | --- | --- |
| 纯命令功能 | 调用时完成操作，没有独立业务配置，也没有需要功能持续维护的监听器、任务、通道或服务 | 由 `commands.yml` 控制命令注册、权限与用法；不额外注册 Feature | 一次调用的临时处理由命令及底层适配完成 |
| 半模块功能 | 主要由命令触发，需要可配置的业务规则，但通常没有独立的持续触发器或后台资源 | 命令配置仍在 `commands.yml`；业务开关与规则放入 `features.yml`，使用对应 Settings | 通过现有 Feature 生命周期加载配置、关闭入口；必要时使旧交互入口失效 |
| 全模块功能 | 除命令外，还拥有交互监听、玩家会话、延迟任务、网络通道、HTTP 服务等需要管理的资源 | 命令入口独立配置；模块开关与业务设置放入 `features.yml` | 安装资源、启动入口、停用业务、清理活动对象、最终卸载资源 |

具体约定：

1. 每个独立业务命令使用独立命令类，不通过 Action 枚举合并不同功能。本批 P01–P07、P09、P10 使用可选的 `[player]` 参数统一自身／目标玩家操作，默认同时注册 `/sparrow <命令> [player]` 与 `/<命令> [player]`。
2. 同一业务的多个命令可以共享一个模块与 Settings。例如 `head` 和 `urlhead` 共享头颅服务。
3. 命令能被解析，不代表模块允许执行。半模块和全模块的命令必须检查模块运行状态。
4. `features.yml` 中每个模块有 `enabled`；不为纯命令额外制造模块开关。
5. 未发布阶段直接调整配置定义，不编写旧版本兼容迁移补丁，不为本计划提升配置版本。
6. 本计划不新增 reload／启用／停用共用的锁或执行权接口。沿用当前项目已有约束。
7. 表中的配置项是实现时的候选规则，不是已经确定的字段名和默认值；确实没有业务配置需求时，应保持纯命令，而不是为了分类添加配置。
8. 现有框架能够承担的通用能力直接复用；有具体调用需求时再提取辅助方法或适配，不预先搭建新的通用框架。

分类汇总如下。数量按旧业务功能计数，不按 Java 类或模块数计数。

| 类型 | 旧业务功能数 | 规划中的模块组织 |
| --- | ---: | --- |
| 纯命令 | 31 | 不进入 FeatureManager |
| 半模块 | 6 | 暂按 6 个业务模块组织 |
| 全模块 | 6 | 暂按 5 个业务模块组织，`head`／`urlhead` 共用头颅模块 |
| 合计 | 43 | 重载与已实现的 quick-shulker 不在待办范围内 |

## 3. 纯命令功能：31 项

本表保留原始迁移清单，已实现项目单独标记。消息发送、文本解析等已有底层能力的项目，在依赖栏注明。

| 编号 | 状态 | 功能 ID | 实现范围 | 可复用能力／依赖与验收重点 |
| --- | --- | --- | --- | --- |
| P01 | 已实现 | `workbench` | 为自身或目标玩家打开工作台 | 原版菜单接口；验证物品操作与正常关闭 |
| P02 | 已实现 | `anvil` | 打开铁砧 | 原版菜单接口；验证费用、取出结果和关闭行为 |
| P03 | 已实现 | `grindstone` | 打开砂轮 | 原版菜单接口；保留原版处理语义 |
| P04 | 已实现 | `smithing-table` | 打开锻造台 | 核对目标版本的菜单接口与槽位 |
| P05 | 已实现 | `stonecutter` | 打开切石机 | 原版菜单接口 |
| P06 | 已实现 | `cartography-table` | 打开制图台 | 原版菜单接口 |
| P07 | 已实现 | `loom` | 打开织布机 | 原版菜单接口 |
| P08 | 待实现 | `enderchest` | 为自身或目标玩家打开其自己的末影箱 | 复用原末影箱数据；不扩大为查看他人末影箱的功能 |
| P09 | 已实现 | `heal` | 恢复玩家当前最大生命值，同时恢复饥饿值与饱和度 | 本批按 `[player]` 实现；不治疗死亡玩家，不支持非玩家实体 |
| P10 | 已实现 | `feed` | 恢复饥饿值与饱和度 | Bukkit 玩家接口 |
| P11 | 已实现 | `fly` | 切换或指定飞行状态 | 飞行许可与飞行状态保持一致 |
| P12 | 已实现 | `fly-speed` | 设置飞行速度 | 参数范围与底层接口一致 |
| P13 | 已实现 | `walk-speed` | 设置行走速度 | 参数范围与底层接口一致 |
| P14 | 已实现 | `suicide` | 玩家自杀 | 使用正常生命／死亡处理路径 |
| P15 | 已实现 | `burn` | 为所选实体设置燃烧时间 | 补齐时间参数解析；正确转换为 tick |
| P16 | 已实现 | `extinguish` | 清除所选实体的燃烧状态 | Bukkit 实体接口 |
| P17 | 已实现 | `sudo` | 让所选玩家执行命令 | 保留目标玩家身份与权限语义 |
| P18 | 已实现 | `look` | 朝向坐标、方位、实体或玩家 | 统一自身／实体选择器入口；四种目标选项互斥，按实体调度读取目标及调整朝向 |
| P19 | 已实现 | `top-block` | 传送到当前 X/Z 的最高方块上方 | 核对高度与落点；通过正确的实体调度路径传送 |
| P20 | 待实现 | `dye` | 修改手持皮革装备颜色 | Bukkit 物品接口与颜色解析 |
| P21 | 待实现 | `color` | 查询手持可染色装备颜色 | 明确不支持物品的反馈 |
| P22 | 待实现 | `itemname` | 查询／修改显示名称，提供聊天编辑入口 | 复用 Components／AdventureHelper；补物品名称读写 |
| P23 | 待实现 | `custommodeldata` | 查询／设置模型数据 | 旧版为整数参数；先确定新版各目标版本的表达，再适配 |
| P24 | 待实现 | `itemdata` | 以交互文本展示物品数据并支持复制 | 复用组件系统；补充数据读取与递归展示，覆盖 NBT／组件差异 |
| P25 | 已实现 | `actionbar` | 向选中玩家发送 ActionBar | 已有 SparrowPlayer.sendActionBar；补命令、选择器与文本选项 |
| P26 | 已实现 | `broadcast` | 向选中玩家发送消息 | 已有玩家消息接口；补命令与文本选项 |
| P27 | 已实现 | `title` | 发送标题／副标题，指定淡入、停留与淡出时间 | 补标题发送适配；复用文本组件 |
| P28 | 已实现 | `toast` | 指定图标、类型、文本发送 Toast | SparrowPlayer 发送完成进度与移除进度包，客户端排队显示提示 |
| P29 | 已实现 | `totem-animation` | 播放图腾动画 | 补协议适配；动画结束后玩家真实物品状态保持正确 |
| P30 | 已实现 | `demo` | 显示演示版提示 | 补对应客户端事件适配 |
| P31 | 已实现 | `credits` | 显示终末之诗／制作人员界面 | 补对应客户端事件适配 |

### 本批实现与使用

P01–P07、P09、P10 的独立实现位于 `core/src/main/java/net/momirealms/sparrow/plugin/command/feature/`，对应 `WorkbenchCommand`、`AnvilCommand`、`GrindstoneCommand`、`SmithingTableCommand`、`StonecutterCommand`、`CartographyTableCommand`、`LoomCommand`、`HealCommand`、`FeedCommand`。

- 玩家省略 `[player]` 时操作自己；控制台必须指定在线玩家。参数提供在线玩家名补全。
- 每个功能通过 `commands.yml` 的同名节点管理开关、权限与入口；默认权限为 `sparrow.command.<功能名>`，同时允许操作自身和指定玩家。
- `usages` 只填写命令入口，例如 `[/sparrow workbench, /workbench]`；可选参数由命令类注册，不在配置中重复填写。
- 七种菜单调用 Bukkit 原版接口并强制打开，无需附近存在对应方块；玩家操作在目标玩家调度器上执行。
- 治疗与喂食均将饥饿值设为 20、饱和度设为 10；治疗额外恢复当前最大生命值，喂食不改变生命值。
- 中英文反馈已补齐；没有新增业务 Feature，没有提升配置版本或添加兼容迁移。
- 验证：`:core:test` 与 `:core:shadowJar` 通过，共 30 项测试；新增测试覆盖双入口、权限、可选目标、控制台缺参、在线玩家补全、目标玩家调度、直接反馈（不额外调度）、喂食及死亡玩家处理。
- 尚待实际服务器验证：七种原版菜单的物品取放、结果槽、费用、关闭及 Paper／Folia 上的实际交互。自动化测试不等同于这些交互已经验收。

### P12–P19：状态、实体操作与朝向

八个独立命令类位于同一 `command/feature/` 目录：`FlySpeedCommand`、`WalkSpeedCommand`、`SuicideCommand`、`BurnCommand`、`ExtinguishCommand`、`SudoCommand`、`LookCommand`、`TopBlockCommand`。

以下每个入口也默认注册 `/sparrow <命令>` 版本，权限为 `sparrow.command.<命令名>`。可选目标省略时操作执行命令的玩家；控制台必须指定目标，`suicide` 仅玩家可用。

| 命令 | 参数与行为 |
| --- | --- |
| `/fly-speed <speed> [player]` | 直接使用 Bukkit 的 -1～1 速度范围，不改变飞行许可 |
| `/walk-speed <speed> [player]` | 直接使用 Bukkit 的 -1～1 速度范围 |
| `/suicide` | 将自身生命值设为 0，进入正常死亡流程 |
| `/burn <targets> <time>` | 实体选择器；无单位数字为 tick，支持 d/h/m/s/t 及 `1m30s`；范围 0～2147483647 tick，拒绝格式残留和溢出 |
| `/extinguish [targets]` | 实体选择器；清除所选实体的燃烧时间 |
| `/sudo <targets> <command...>` | 玩家选择器；以目标玩家身份执行，保留命令空格与参数，允许开头带 `/`，不临时赋予 OP；按 dispatch 返回值反馈 |
| `/look [targets] --face <direction>` | 实体选择器；支持 BlockFace 方位，包括斜向、上、下和 self；水平转向保留俯仰角 |
| `/look [targets] --location <x> <y> <z>` | 面向坐标；复用 Cloud LocationParser 的坐标解析 |
| `/look [targets] --player <player>` | 面向在线玩家眼睛位置 |
| `/look [targets] --entity_uuid <uuid>` | 面向实体；生物使用眼睛位置，其他实体使用实体位置 |
| `/top-block [targets]` | 实体选择器；保留 X/Z 和朝向，移动到当前列最高方块的可用位置；检查世界高度和落点空间 |

`look` 一次必须且只能指定一种目标选项。读取目标实体的位置与修改所选实体的状态分别在对应实体调度器执行；跨世界朝向请求会被拒绝。玩家转向使用传送接口兼容 Spigot，非玩家实体使用 `setRotation`。Folia 传送使用 `teleportAsync`；登顶与玩家转向在结果返回后反馈，传送取消不报成功。

`sudo` 通过 Bukkit `Player.performCommand` 以玩家当前权限执行命令，仍由命令入口安排玩家线程调度并反馈执行结果。复用了现有 `handleFeedback`，没有重新引入 `executeForPlayer` 或消息调度包装；纯命令不注册 Feature。本批新增 `TimeParser`，以及供朝向和登顶共用的 `EntityUtils` 传送适配；配置版本与迁移规则保持不变。

验证：`:core:test` 与 `:core:shadowJar` 通过，共 50 项测试。覆盖速度边界和 NaN 拒绝、控制台与玩家入口、自杀调度、批量燃烧／灭火、空选择结果、代执行身份与完整参数、时长格式及溢出、斜向角度、跨世界朝向拒绝和世界高度处的登顶失败。实体选择器测试使用已解析的选择结果；原生选择器解析、客户端视角更新、实际传送／取消事件及 Folia 跨区域运行仍待上服验收。

### P25–P27、P29–P31：消息与客户端效果

独立命令类：`ActionBarCommand`、`BroadcastCommand`、`TitleCommand`、`TotemAnimationCommand`、`DemoCommand`、`CreditsCommand`。默认同时注册下表入口与 `/sparrow <命令>` 入口，默认权限为 `sparrow.command.<命令名>`。

| 命令 | 行为与参数 |
| --- | --- |
| `/actionbar <targets> <message...>` | 向玩家选择器选中的玩家发送 ActionBar |
| `/broadcast <targets> <message...>` | 向所选玩家发送聊天消息；全服广播使用 `@a` |
| `/title <targets> <fadeIn> <stay> <fadeOut> <message...>` | 三段时间均为非负 tick；使用字面量 `\n` 分隔主标题和副标题；空标题／副标题会发送空组件以清除旧内容 |
| `/totem-animation <targets> <item>` | 玩家选择器；物品使用 Cloud 原版物品参数解析，必须是不死图腾，可携带自定义组件 |
| `/demo [player]` | 显示原版演示版介绍界面；不改变服务器游戏模式 |
| `/credits [player]` | 显示终末之诗／制作人员界面；不修改服务端通关记录或玩家位置 |

前三个消息命令支持 `--silent`／`-s`、`--legacy-color`／`-l`、`--parse`／`-p`，放在消息参数后。默认使用 MiniMessage；`config.yml` 的 `text-options.parse-placeholder`（默认 true）与 `text-options.parse-legacy-color`（默认 false）控制三个命令的解析默认值，重载后生效。命令带 `--parse` 或 `--legacy-color` 时启用对应解析。传统颜色选项将旧颜色代码转换后解析；PlaceholderAPI 解析以每位接收者为上下文，未安装时保留原文。图腾动画支持 `--silent`／`-s`。`demo` 和 `credits` 省略玩家时操作自己，控制台必须指定在线玩家。

标题修复旧实现把 `fadeOut` 读取为 `fadeIn` 的问题，并拒绝包含两个以上分隔符的内容。图腾动画采用 CraftEngine 的装备包流程：临时将动画物品放入客户端副手，发送实体状态 35，再恢复副手；主手物品带有死亡保护组件时，同时临时清空并恢复主手。整个流程在同一个 bundle 中发送物品快照，不写入服务器背包或消耗图腾。

`SparrowPlayer` 提供标题、图腾动画、演示提示和终末之诗接口，由 `BukkitSparrowPlayer` 负责构造并发送数据包；六个命令直接调用玩家接口，不再提交平台调度任务。文本格式转换复用 `Components`，PlaceholderAPI 解析由命令直接调用 `CompatibilityManager`。已核对本地 Minecraft 1.21.11、26.1.2 和 26.2 源码中的数据包签名、游戏事件及客户端图腾选取顺序。

验证：`:core:test`、`:core:shadowJar` 通过，共 56 项测试。本批覆盖 demo／credits 双入口、控制台缺参、空选择结果、silent 反馈、标题分隔格式与空副标题、按接收者解析占位符、图腾物品类型检查，以及六个命令直接调用玩家接口而不提交平台任务。PlaceholderAPI 缺失时保留原文。实际客户端动画、标题时间、终末之诗退出、原生物品／选择器解析、PAPI 扩展及 Folia 多玩家运行尚待上服验收。

### P11、P28：飞行与进度提示

- `/fly [player] [enabled]`：省略玩家时操作自己，控制台必须指定玩家。省略 enabled 时切换飞行许可；显式 true/false 时设置状态。例如 `/fly Tester true`。开启时先授予飞行许可，再进入飞行；关闭时先停止飞行，再收回许可。读取与修改状态在目标玩家线程执行。
- `/toast <targets> <type> <item> <message...>`：type 支持 task、goal、challenge，图标使用原版物品参数。支持 `--silent/-s`、`--legacy-color/-l`、`--parse/-p`，并读取 `text-options` 中的默认解析选项。
- 两个命令均有对应的 `/sparrow` 前缀入口、独立命令类、配置节点与 `sparrow.command.fly`／`sparrow.command.toast` 默认权限。
- Toast 通过 `SparrowPlayer.sendToast` 发送，采用 CraftEngine 的 `impossible` 条件、完成进度和移除进度流程。在一个 bundle 中先添加并完成客户端临时进度，再移除它；固定使用 `sparrow:toast` 标识，客户端保留已经排队的提示。1.21.11 使用 `ItemStack` 图标，26.1 起使用保留物品组件的 `ItemStackTemplate`；代理兼容 26.2 进度条件相关类迁移到 `advancements.triggers` 包。命令拒绝空气图标。服务端无需登记进度、监听器或延迟清理任务，仍归为纯命令功能。

### Redis 消息基础设施

接入与 Sparrow Sync 相同的 `sparrow-redis-message-broker:0.0.7`，在既有 `redis` 包中增加 `MessageBrokerManager`。`RedisConnector` 管理普通 Redis 连接和 Broker 的 Pub/Sub 连接；插件加载时连接后订阅，关闭时先退订，再释放连接与客户端。

服务器标识位于独立的 `server.yml`，默认留空，留空时拒绝启动；修改后需重启。共享同一 Redis 数据库的各服应使用不同标识。消息频道为 `sparrow:db:<数据库编号>:messages`。业务调用通过 `plugin.messageBrokerManager().broker()` 注册编解码器和收发消息；本批只接入通信基础设施。

消息库随插件打包，包名重定位到 `net.momirealms.sparrow.libraries.redis.messagebroker`；Lettuce、Caffeine 复用现有运行时依赖加载配置。

验证：`:core:test` 共 58 项全部通过，`:core:shadowJar` 成功。新增覆盖飞行许可与状态一致性、双入口、控制台缺参、Toast 图标与样式传递、默认 PAPI 解析及 silent、Broker 数据库频道和退订、服务器标识保存与重启读取；检查成品 JAR 中消息库与 Caffeine 引用的重定位。客户端实际 Toast 显示、原生物品／选择器解析和真实 Redis 跨服通信尚待运行验证。

按 CraftEngine 复核后的命令检查：

| 命令 | 核对结果 |
| --- | --- |
| `toast` | 按 `BukkitAdvancementManager.sendToast` 补齐版本分支和完整进度条件；拒绝空气图标 |
| `totem-animation` | 按 `PlayerUtils.sendTotemAnimation` 使用副手装备包，处理主手已有死亡保护组件的情况 |
| `title` | 按 `BukkitServerPlayer.sendTitle` 的顺序发送主标题、副标题、显示时间 |
| `broadcast`、`actionbar` | 沿用玩家系统消息接口，overlay 区分聊天栏与快捷栏；发送无需额外调度 |
| `demo`、`credits` | 已核对三个版本的游戏事件和构造签名；CraftEngine 中没有可直接对应的实现 |
| `sudo`、`fly` | 沿用玩家线程上的 Bukkit 操作；sudo 调用 `performCommand`，fly 按顺序修改飞行许可和状态 |

`ClientEffectsCompatibilitySmoke` 在独立 JVM 中使用真实 1.21.11、26.1.2、26.2 服务端类全部通过，覆盖 Toast 代理绑定、图标组件保留、进度完成、标题和装备包构造、实体事件构造签名，以及 demo／credits 游戏事件包。运行时需提供对应版本 NMS 和依赖，以版本号为参数并开启 `-ea`。该检查不启动服务器或客户端，不代表已经验证客户端实际显示效果。配置版本保持不变，未增加 YAML 迁移。


## 4. 半模块功能：6 项

这些功能拟将旧实现中硬编码的业务规则提取为设置，主要入口仍是命令。下列配置是规划建议，实施时只保留确定需要开放给服主的选项。

| 编号 | 状态 | 功能 ID | 实现范围 | 候选业务配置 | 停用与验收要求 |
| --- | --- | --- | --- | --- | --- |
| S01 | 待实现 | `world` | 指定／轮换世界、下界坐标换算、寻找落点 | 可切换世界、坐标换算策略、落点搜索范围 | 停用后禁止新请求；验证下界换算、世界边界和搜索终止 |
| S02 | 待实现 | `tpoffline` | 获取离线玩家保存的位置并传送 | 允许的目标世界、是否允许加载未缓存玩家数据 | 停用后禁止新请求；区分未玩过、无位置、世界不可用；读取策略明确 |
| S03 | 待实现 | `enchant` | 指定目标与装备槽位添加附魔，保留等级／冲突／适用性选项 | 等级上限、允许使用的附魔、哪些限制允许通过参数绕过 | 命令参数服从模块规则；验证实体装备、附魔书及版本差异 |
| S04 | 待实现 | `more` | 补满手持物品，或按数量拆分发放 | 单次数量上限、背包满时的处理方式 | 核对实际发放数量与反馈；配置规则在玩家版、管理员版一致 |
| S05 | 待实现 | `itemlore` | 查询、插入、编辑、删除、上移、下移 Lore，提供聊天管理菜单 | 行数／文本长度上限、允许的文本格式 | 停用后旧点击入口也不能继续编辑；验证行号与手持物品变化 |
| S06 | 待实现 | `distance` | 测量目标方块距离，按选项显示调试标记 | 最大检测距离、默认标记开关、颜色和显示时长 | 停用后禁止新请求；确认标记关闭参数与执行逻辑一致 |

S06 按客户端自行到期的单次标记发送规划，不额外建立后台扫描或重复任务。若目标版本适配确实要求持续维护标记，再调整资源归属。

## 5. 全模块功能：6 项，组织为 5 个模块

命令是这些模块的入口之一。监听器、通道、会话与服务由模块负责，不能只迁移命令类。

| 编号 | 状态 | 规划模块／旧功能 | 实现范围 | 资源与触发器 | 候选配置／清理要求 |
| --- | --- | --- | --- | --- | --- |
| F01 | 待实现 | `enchantmenttable` | 虚拟附魔台、指定书架数量、重新计算附魔选项 | 附魔准备与关闭监听、当前打开的附魔会话 | 默认／最大书架数量；停用时关闭模块打开的菜单并清理会话，正常附魔台不受影响 |
| F02 | 已实现 | `patrol` | 选择下一个巡查对象，维护巡查顺序并传送 | 玩家加入／退出监听、候选集合、最后巡查时间 | 绕过权限、候选筛选规则；停用后禁止新巡查，退出玩家及时移出集合 |
| F03 | 待实现 | `highlight` | 通过交互或命令指定区域，展示方块高亮 | 选点交互监听、选区会话、高亮对象、到期清理任务 | 默认颜色／时长、最大范围、实心方块筛选；停用时移除高亮并结束选点会话 |
| F04 | 待实现 | `head` 模块，包含 `head` 与 `urlhead` | 名称／UUID／URL 获取头颅及发放 | Fetcher 注册、HTTP 客户端、缓存、正在进行的请求；在线资料读取 | 获取源顺序、缓存时长、超时、请求服务所需凭据；停用后不接受新请求，已停用模块的异步结果不再发放物品，卸载时释放客户端资源 |
| F05 | 已实现 | `server` | 列出在线后端服务器并发送切服请求 | BungeeCord 收发通道（随命令注册）、补全时查询代理并缓存 | 代理类型／启用方式、允许的后端服务器；停用后关闭业务入口，卸载时解除通道和监听注册 |

全模块沿用当前 Feature 生命周期：

- 开服时读取配置；启用的模块按 `loadConfig(同步) → onLoad(同步) → onEnable(同步)` 安装启动。启动时关闭的模块不注册资源。
- 启动时未安装的模块允许运行中安装，按同一同步安装顺序执行。
- 运行中停用执行 `onDisable(同步)`；停止接受新操作，并完成表中约定的活动资源清理。
- 已安装模块重新启动执行 `loadConfig(异步) → onEnable(同步)`。
- 已安装模块热重载按 `onDisable(同步) → loadConfig(异步) → onEnable(同步)` 处理，最后是否启用仍由配置决定。
- 运行中停用不等于卸载。保留的监听器和回调必须遵守状态闸门；关服通过 `onDisable → onUnload` 完成最终释放。

### F02：巡查

实现位于 `feature/patrol/`（`PatrolFeature`、`PatrolSettings`）与 `plugin/command/feature/PatrolCommand`。

- `/patrol [targets]`，同时注册 `/sparrow patrol`，默认权限 `sparrow.command.patrol`，仅玩家可用。省略目标时从本服全部在线玩家中选择；模块未启用时回复 `command.feature.disabled` 并拒绝执行。
- 巡查队列：模块安装时放入当前在线玩家，玩家进服时放到队首，退出时移出。`/patrol` 跳过巡查者自己、离线玩家、拥有 `sparrow.bypass.patrol` 的玩家和不符合筛选规则的玩家，从队首往后取第一位候选并把他移到队尾；一轮内每名候选各被选中一次，下一轮沿用相同顺序。
- 并发：队列使用 `ConcurrentLinkedDeque`，不加锁。Folia 上多名管理员同时巡查或巡查时有玩家进退服，最多让同一玩家被重复选中或短暂留下重复记录；退出时会清除该玩家的全部记录，不影响后续轮换。
- `features.yml` 的 `patrol` 节点：`enabled`、`skip-spectators`（默认 true，跳过旁观模式玩家）、`excluded-worlds`（位于其中的玩家不被选中）。
- 生命周期：队列从 `onLoad` 起持续维护，不随停用清空；停用只由命令入口拒绝新巡查。
- 命令直接读取目标位置，再在巡查者的调度器执行 `EntityUtils.teleport`；传送失败时回复 `command.teleport.failure`。`FeatureManager` 新增按类型获取模块的 `feature(id, type)`，供模块命令访问业务方法。

验证：`:core:test` 共 13 组测试通过，新增 `PatrolFeatureTest` 覆盖整轮轮换与顺序复现、新进服与重新进服玩家优先、绕过权限／旁观者／排除世界／离线筛选、退出玩家移出队列；`:core:shadowJar` 成功。实际传送、Folia 跨区域读取位置、选择器解析与权限插件下的绕过权限尚待上服验收。

### F05：切换服务器

实现位于 `feature/server/`（`ServerFeature`、`ServerSettings`，只负责开关与允许列表）、`plugin/command/feature/ServerCommand` 与 `plugin/command/parser/ServerParser`。

- `/server <server> [targets]`，同时注册 `/sparrow server`，默认权限 `sparrow.command.server`。省略目标时切换自己，控制台必须指定玩家；模块未启用、目标不在允许列表或就是本服时拒绝执行。
- 服务器名以代理（BungeeCord／Velocity）配置为准，与 `server.yml` 的 `server-id` 无关。切服发送 BungeeCord `Connect` 请求；Velocity 默认接受 `BungeeCord` 通道。
- 服务器列表：`ServerParser` 不依赖模块，自己向代理查询 `GetServers`（全部后端）与 `GetServer`（本服名称）并缓存。玩家每次补全都经由自己的连接查询，没有冷却；应答异步到达，本次返回上一次的结果。控制台不补全。补全排除本服、按传入的筛选条件（模块的允许列表）过滤并按名称排序；命令仍接受手动输入的名称。切服 `Connect` 经由目标玩家自己的连接发出。其他插件经同一通道查询得到的应答也会更新缓存。
- `features.yml` 的 `server` 节点：`enabled`、`allowed-servers`（为空时不限制）。
- 生命周期：`BungeeCord` 收发通道由 `ServerCommand` 在命令注册时注册、注销时解除，与模块是否安装无关；模块没有监听、任务或通道资源，停用时命令入口拒绝新请求。本服判断使用代理返回的名称，未知时跳过。

验证：`:core:test` 通过，新增 `ServerParserTest` 覆盖代理应答解析、补全排除本服与筛选、玩家每次补全都发送 `GetServers`／`GetServer`、控制台不补全；`ServerFeatureTest` 覆盖允许列表。代理实际应答与切服、Velocity 通道兼容尚待上服验收。

## 6. 尚缺的共享支撑能力

下列项目服务于上述功能，不另行计为第四类业务模块。资源应归属于实际使用它的模块或现有插件服务。

| 编号 | 状态 | 工作项 | 已有可复用内容 | 实现边界与关联功能 |
| --- | --- | --- | --- | --- |
| D01 | 待实现 | 选择器及参数扩展 | Cloud 命令系统 | 按命令需要补时间、URL、颜色、离线玩家、后端服务器解析；核对空选择器反馈与权限 |
| D02 | 待实现 | 实体／位置业务辅助 | PlatformExecutor、Bukkit／Folia 调度 | 按实际调用提取治疗、朝向、落点与传送辅助；服务 P09、P18、P19、S01、S02、F02 |
| D03 | 待实现 | 物品读写与发放辅助 | Bukkit 物品接口、现有 NBT 依赖与代理机制 | 名称、Lore、模型数据、头颅和数量处理；不用潜影盒专用容器写回替代通用物品编辑 |
| D04 | 部分实现 | PlaceholderAPI 文本解析 | 当前 CompatibilityManager 已检测 PlaceholderAPI | P25–P27 已接入普通占位符解析；未安装插件时保留原文。关系占位符尚未接入 |
| D05 | 部分实现 | 客户端功能适配 | bukkit-proxy、现有消息发送、Sparrow UI | 按需补标题、Toast、动画、调试标记、高亮与附魔选项能力；菜单按类型选择原版接口或 UI 库 |
| D06 | 待实现 | 头颅服务 | 旧 common/feature/skull 与 bukkit/feature/skull 可作参考 | 跟随 F04 实现；HTTP 客户端、缓存与获取器由头颅模块管理 |
| D07 | 已实现 | 巡查用户状态 | PlayerManager、SparrowPlayer | 巡查记录由 `PatrolFeature` 自行维护，玩家退出时移除；未写入 `SparrowPlayer` |
| D08 | 已实现 | 后端服务器发现与通信 | BungeeCord 插件消息 | 服务器列表与本服名称向代理查询，切服走 BungeeCord `Connect`；Redis 心跳不参与切服 |
| D09 | 待评估 | 自定义事件系统及可取消反馈 | 新版已有命令反馈回调 | 旧事件总线、生成器、优先级订阅没有完整替代；先确定扩展调用方，确有需求后再决定保留哪些能力 |
| D10 | 待评估 | 离线用户抽象 | 新版已有在线玩家管理 | S02 需要离线数据读取；是否需要通用离线用户模型取决于后续实际复用，先满足具体读取需求 |

旧存储层只包含类型声明、生命周期接口和空工厂，不列为需要复刻的完整功能。H2、YAML、JSON 等只是旧枚举值，不能据此新增后端支持。当前连接管理、配置、翻译、依赖下载、日志及调度已有替代，均不安排整体重写。

## 7. 建议实施顺序

每批完成后再进入下一批，不要求一次迁移全部旧代码。

| 批次 | 目标 | 包含范围 | 完成标志 |
| --- | --- | --- | --- |
| A | 建立基础命令迁移范例 | P09–P18：状态、速度、燃烧、代执行与朝向；按需补 D01、D02 | 独立命令类、注册配置、权限、选择器、翻译与实体调度路径形成可复用范例 |
| B | 原版容器与移动规则 | P01–P08、P19、S01、S02、F01 | 原版菜单可正常取放物品；跨世界／离线传送规则明确；附魔会话可停用和释放 |
| C | 物品管理与文本编辑 | P20–P24、S03–S05；按需补 D03 | 数据读写、数量处理与聊天编辑可靠；覆盖目标版本差异 |
| D | 消息与客户端效果 | P25–P31；按需补 D04、D05 | 中英文反馈与文本格式正确；客户端效果经过实际版本验证 |
| E | 有状态管理与诊断功能 | F02、F03、S06 | 玩家加入／退出、选点、模块停用、任务到期均能正确清理 |
| F | 外部服务与跨服通信 | F04、F05；完成 D06–D08，按调用需求处理 D09、D10 | 请求、缓存和通道正常工作；停用／卸载期间不会继续执行已失效业务 |

## 8. 每项功能的完成检查

- [ ] 在正式模块中实现并注册；旧目录中的源码存在不算完成。
- [ ] 各命令有独立实现类、用法、权限、开关与合适的动态补全。
- [ ] 纯命令没有多余的 Feature；半模块／全模块有对应 Settings 和入口状态检查。
- [ ] 中英文提示齐全，聊天按钮按当前配置的命令用法与权限生成。
- [ ] 半模块／全模块覆盖启动关闭、首次热安装、停用、重新启用、重载和关服时适用的路径。
- [ ] Bukkit／Folia 的玩家、实体和世界操作使用正确调度路径。
- [ ] 涉及 NMS 的功能明确版本范围，并在相关版本验证绑定与实际行为；仅编译成功不足以验收。
- [ ] 运行与关闭过程中不遗留该功能的会话、监听订阅、任务、显示对象或外部客户端资源。
- [ ] 完成与改动风险相称的验证；不为简单命令重复编写只复述实现的测试。
- [ ] 无旧配置迁移补丁，无配置版本提升；更新本表状态及实现位置。

## 9. 源码参考

已从旧 `bukkit`／`common` 删除 P01–P07、P09–P19、P25–P31 和重载对应的命令实现与注册，同时移除了专用消息常量、旧 `TimeParser`、时间参数键，以及旧 `EntityUtils` 中的治疗、朝向和登顶方法。

本次追加清理飞行、ActionBar、广播、标题、Toast、图腾动画、演示提示和终末之诗，共 11 个旧命令类；删除旧 Toast 的 `AdvancementType`、26 个专用消息常量，以及 `SparrowNMSProxy` 中对应的 6 个发送方法。F02 完成后删除 `PatrolAdminCommand`、`SparrowBukkitPatrolManager`、`PatrolManager`、`Patrolable`、2 个巡查消息常量，以及 `BukkitOnlineUser` 中的巡查时间字段。F05 完成后删除 `ServerAdminCommand`、`ServerPlayerCommand`、`BackendServerParser`、`SparrowBukkitBungeeManager` 及 3 个切服消息常量。旧注册表剩余 24 个命令类。已删除的实现可通过 Git 历史查阅。

保留尚未迁移的功能及其共享依赖，包括世界切换使用的 `EntityUtils.changeWorld`、末影箱／附魔台使用的菜单标题适配。此次清理按已完成的业务功能进行，不扩大新版功能范围：新版 `heal` 仍仅治疗玩家，旧容器命令的额外标题参数等未随删除补入新版。

- 旧命令注册：`bukkit/src/main/java/net/momirealms/sparrow/bukkit/command/SparrowBukkitCommandManager.java`
- 旧命令实现：`bukkit/src/main/java/net/momirealms/sparrow/bukkit/command/feature/`
- 旧业务资源实现：`bukkit/src/main/java/net/momirealms/sparrow/bukkit/feature/`
- 旧共享业务接口与头颅服务：`common/src/main/java/net/momirealms/sparrow/common/feature/`
- 旧 NMS 委托：`bukkit/src/main/java/net/momirealms/sparrow/bukkit/SparrowNMSProxy.java`
- 正式命令注册：`core/src/main/java/net/momirealms/sparrow/plugin/command/BukkitCommandManager.java`
- 正式模块生命周期：`core/src/main/java/net/momirealms/sparrow/feature/Feature.java`
- 正式模块注册：`core/src/main/java/net/momirealms/sparrow/feature/FeatureManager.java`

旧物品工厂也有空实现，迁移时以可到达的实际行为为准，不按类名或方法名判断完成度。

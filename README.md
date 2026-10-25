# Aura

面向国内网络环境的天气应用 + 自建后端服务。App 不依赖任何被墙服务：定位使用高德
SDK 与平台标准 API，天气数据经自建代理获取，AI 问答使用智谱 GLM——全链路国内直连。

## 功能总览

| 端 | 功能 |
|---|---|
| App · 天气 Tab | 实时天气 + 24 小时逐时 + 7 天预报；三级定位（高德/缓存/系统）；城市搜索；离线快照兜底；下拉刷新 |
| App · 空气 Tab | 多标准 AQI + 污染物浓度卡片（点击查看国标 HJ 633-2012 分级区间）+ 24h AQI 趋势 + 健康建议 |
| App · AI Tab | **天气问答**：自然语言提问，基于该城市实时与预报数据作答；右上角进入**日程天气**（未来 7 天日程 × 天气匹配，全本地计算） |
| 后端 | 和风天气 API 透明代理（JWT 签名、付费接口黑名单、缓存、限流与日额度熔断）+ `/ai/ask` 问答服务（上下文组装 + GLM 生成） |

## 技术栈

| 层 | 技术 |
|---|---|
| App UI | Jetpack Compose + Material 3（动态取色）、Navigation Compose、单 Activity UDF |
| App 架构 | Hilt (KSP) 依赖注入；ViewModel + StateFlow；三层架构（UI / Domain / Data） |
| App 数据 | Retrofit + kotlinx-serialization；DataStore（偏好 + 离线快照） |
| 定位 | 高德定位 SDK（主）→ 系统缓存 → LocationManager 双通道（兜底） |
| 后端 | Kotlin + Ktor 3.6（CIO）；kotlinx.serialization；systemd 部署 |
| AI | 智谱 GLM（模型可配置，当前 glm-5.3-flash） |
| 测试 | App 33 个单元测试；后端 10 个单元测试 |

## 系统拓扑与数据流

```
┌── Android 设备 ──────────────────────────────────────────────┐
│  Aura App（si.jacen.aura）                                   │
│                                                              │
│  定位：高德 SDK ──WiFi/基站指纹──→ 高德国内服务（直连，秒级）  │
│                                                              │
│  天气/空气/问答：HTTP ──────────┐                             │
└────────────────────────────────┼─────────────────────────────┘
                                 ▼
┌── 自建后端（Ktor，腾讯云，:8080）─────────────────────────────┐
│  /v7/* /geo/* /airquality/*   透明转发 → 和风天气 API         │
│      ├ 路由黑名单（付费接口 403）                             │
│      ├ 20 分钟内存缓存（X-Cache: HIT/MISS）                   │
│      ├ 每 IP 限流 30 次/分钟                                  │
│      └ 每日真实出网 ≤ 1500 次（免费额度不可击穿）             │
│                                                              │
│  /ai/ask   问答服务                                           │
│      ├ 并行拉取 4 组天气上下文（复用缓存与额度）              │
│      ├ LLM 独立日额度（默认 100 次/天）                       │
│      └ GLM 生成（仅依据数据作答，禁止编造）                   │
└──────────────────────────────────────────────────────────────┘
```

### 流程 1 · 定位（冷启动，无保存城市时）

```
权限请求 → ① 高德 SDK 单次定位（≤12s，WiFi/基站指纹，室内秒级）
        失败 → ② 系统缓存位置（1 小时内有效）
        失败 → ③ LocationManager：GPS + 网络双通道并发（≤60s，适配各 ROM）
→ 坐标 → 后端 Geo 反查城市（/geo/v2/city/lookup）→ 写入 DataStore
→ 此后启动直接使用已存城市，定位不再是必经路径
```

高德 SDK 的原理：采集周围 WiFi/基站指纹发送高德国内服务器，与数亿设备累积的指纹
数据库匹配返回坐标。数据库与链路均在国内，无 Google 依赖，国行与国际版设备通用。

### 流程 2 · 天气与空气数据

```
App 请求（下拉刷新 / 切换城市 / 打开空气页）
→ 后端：黑名单检查 → 缓存查询（20 分钟内命中则零出网直接返回）
       → 未命中：扣减日额度 → Ed25519 JWT 签名 → 转发和风 → 解压返回并写缓存
→ App：DTO 解析 → 坐标顺序归一（Coordinate 统一处理"经,纬"与"纬/经"差异）
       → 渲染；成功的同时持久化离线快照
→ 请求失败：回退显示快照 + 离线横幅；全新用户失败则展示引导卡
```

缓存对账本的意义：和风观测数据约 10~20 分钟更新一轮，20 分钟缓存让单城市单接口的
出网上限从每天 288 次降至 72 次；配合日额度熔断，月度消耗有硬上限。

### 流程 3 · 天气问答

```
用户提问 → /ai/ask?location=经,纬&question=…
→ 后端：IP 限流 → 并行获取 实时/24h/7d/空气质量 四组上下文（走同一缓存）
       → LLM 额度检查 → GLM 生成（系统提示词约束：只依据数据、引用具体时间与数字、
         数据不足时如实说明——实测越界提问会被正确拒答）
→ 返回 {answer, generatedAt} → App 以对话卡片展示
超时设计：后端最坏 ~92s（重试 2×45s）< App 读超时 120s，链路对齐不出现"App 先放弃"
```

### 流程 4 · 日程天气（日历感知）

```
开关（按需请求 READ_CALENDAR）→ 直查系统日历 Events 表（未来 7 天，跳过重复规则日程）
→ 全本地双层匹配引擎：
    · 24 小时内的定时日程 × 逐小时数据：时段降雨 ≥50% / 温差 ≥6° / 户外关键词遇高温严寒
    · 全天或更远的日程 × 7 天日预报：日级降雨概率 / 昼夜温差 / 户外高温
    · 跨城提醒：事件地点文字 → Geo 反查异地城市（仅传地点文字，日程标题不出网）
→ 产出：洞察卡片 + "天气平稳"日程列表
```

读取目标是 Android 系统 Calendar Provider（日历中枢）：凡写入系统日历的 App
（厂商系统日历、Google 日历、Outlook、滴答清单等）的日程均可读。注意 Google 账户
日程需系统同步完成后才进入本地日历库（国行设备的厂商日历同步走国内服务，无此依赖）。

### 流程 5 · 离线兜底（贯穿全 App）

```
任何网络请求失败 → DataStore 中的最近快照立即顶上 + 离线横幅提示
全新安装且无快照 → 明确的错误提示与引导（定位重试 / 手动搜索城市），不闪退不空转
```

## App 分层与包结构

```
si.jacen.aura
├── ui/            Compose 界面（weather / air / ask / calendar / search / settings / components）
├── domain/        纯 Kotlin：模型（Coordinate 坐标归一等）、仓库接口、
│                  UseCase、CalendarInsightEngine（双层匹配，全本地）
├── data/
│   ├── network/   AuraApi（Retrofit）+ DTO + 映射
│   ├── location/  LocationClient（三级定位链，含 AuraLocation 诊断日志）
│   ├── calendar/  CalendarReaderImpl（系统日历 Events 直查）
│   ├── local/     SettingsDataStore（单位/城市/快照/开关）
│   └── repository/仓库实现
└── di/            Hilt 模块
```

## 后端结构（Aura-Backend/）

```
Main.kt            路由注册（/healthz、/ai/ask、透明转发）与组装
AskService.kt      问答：上下文并行拉取 + GLM 调用 + 额度控制
LlmClient.kt       GLM 客户端（重试、thinking 按模型族适配、独立超时）
QWeatherJwt.kt     Ed25519 JWT 签名（Token 缓存与自动续签）
TtlCache.kt        内存 TTL 缓存
Guard.kt           每 IP 限流 + 和风/LLM 双日额度熔断
ProxyRules.kt      付费接口黑名单（台风/海洋/辐照 → 403）
deploy/            systemd 服务与一键部署脚本
```

配置经 `config.properties` 提供（和风凭据、GLM 模型与地址、限流阈值等，
环境变量可覆盖；`config.properties.example` 为模板）。

## 构建与运行

```bash
# App（Android Studio 打开工程即可；命令行）
./gradlew :app:assembleDebug

# 本地配置 local.properties：
#   sdk.dir=...
#   aura.apiBaseUrl=http://<server-ip>:8080
#   amap.key=<在高德开放平台申请的 Android Key，绑定包名与签名 SHA1>

# 后端
cd Aura-Backend
./gradlew test               # 单元测试
./gradlew installDist        # 构建
./deploy/deploy.sh <user@server-ip>   # 部署（需服务器 OpenJDK 21+）
```

## 已知边界与路线图

- 问答为非流式返回，等待时长等于生成时长（~10-30s）；下一步可做流式输出
- 每日一句话（事件检测引擎 + 偏好 + 模板降级）已实现待接入
- 候选：时机建议卡、拍照识云（GLM 视觉模型）、桌面小部件、HTTPS 域名

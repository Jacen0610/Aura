# Aura 项目约束

## 硬性规则

- **App 面向国内用户，杜绝一切依赖被墙服务的方案**：禁止引入 Google play-services
  （如 play-services-location）、Firebase 等被墙依赖；定位必须使用平台标准
  `LocationManager`（自动随 ROM 使用国内定位服务）。新增任何第三方依赖前先确认
  其服务器在国内可达。

- **禁止调用和风天气的以下三类接口（不在免费订阅范围，会产生费用）：**
  - 热带气旋（台风）相关接口（`/tropical/*`）
  - 海洋类接口（潮汐、海浪等）
  - 辐照（太阳辐射）相关数据
- 其余接口**均允许使用**。
- 代理服务器（`Aura-Backend/`，独立 Gradle 工程，与 App 同仓库不同目录）必须实现
  **路由黑名单**：上述三类路径一律返回 403，从架构上杜绝误调用付费接口；黑名单外透明转发。
- 代理防护（不可移除）：每 IP 限流（默认 30 次/分钟）+ 每日真实出网熔断
  （默认 1500 次/天，确保 1500×31 < 50,000/月免费额度）。调整阈值只能更严格或维持，
  不得放宽到可能击穿月免费额度的值。
- 和风凭据（Ed25519 私钥、项目 ID、凭据 ID、开发者 ID、专属 API Host）**永远不进 git**：
  真实配置在 `Aura-Backend/config.properties` 与 `Aura-Backend/secrets/`（已 gitignore，
  入库的只有 `config.properties.example` 模板），服务器另存一份于 `/opt/aura-backend/`。

## 项目概况

- **包名约定：统一使用 `si.jacen.*`，不使用 `com.jacen.*`**
- `:app` —— Android 天气应用（包名 `si.jacen.aura`；Compose + Hilt + Retrofit，三 Tab：天气/空气/AI 占位）
- `Aura-Backend/` —— Ktor 代理（独立 Gradle 工程部署于腾讯云轻量服务器，JWT 签名转发，
  黑名单拦截 + 缓存；整个目录 gitignored，不参与 Aura 的 Gradle 构建）
- 认证方式：和风新项目 JWT（Ed25519），JWT claims：`iss`=开发者ID、`sub`=项目ID、
  header `kid`=凭据ID；`iat` 取当前时间前 30 秒，`exp` 最长 24 小时
- 服务器：Ubuntu 26.04 + OpenJDK 25，IP 101.34.159.58，代理监听 8080

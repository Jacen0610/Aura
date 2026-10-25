# Aura Backend

和风天气 API 的轻量 Ktor 代理，部署于腾讯云轻量服务器（Ubuntu 26.04 / OpenJDK 25）。
App 不持有任何和风凭据，全部请求经此代理转发；JWT（Ed25519）签名只在本服务内完成。

> 本目录整体被父仓库 `.gitignore` 忽略，不参与 Aura Android 工程的 Gradle 构建。

## 职责

1. **透明转发**：路径与参数与和风 API 完全一致，如 `GET /v7/weather/now?location=116.41,39.92`
2. **JWT 签名**：JDK 原生 Ed25519，Token 有效期 30 分钟，自动续签（规范见
   https://dev.qweather.com/docs/configuration/authentication/ ）
3. **黑名单拦截**：`/v7/tropical/*`、`/v7/ocean/*`、`/v7/solar/*`（台风/海洋/辐照，
   不在免费范围）一律 403，见根目录 `AGENTS.md`
4. **缓存**：上游 200 响应内存缓存 1200 秒（20 分钟）（`X-Cache: HIT/MISS` 响应头可观测）
5. **防护**：每 IP 限流（默认 30 次/分钟）+ 每日真实出网额度熔断（默认 1500 次/天，
   1500×31≈46,500 < 免费额度 50,000/月，保证额度不可能被刷穿）。熔断后缓存 HIT 仍正常返回；
   `/healthz` 不受任何限制

## 本地开发

```bash
./gradlew test          # 单元测试
./gradlew installDist   # 构建 build/install/AuraBackend/
./gradlew run           # 本地起服务（读 ./config.properties）
```

冒烟测试：

```bash
curl -s http://127.0.0.1:8080/healthz
curl -s 'http://127.0.0.1:8080/v7/weather/now?location=116.41,39.92' -i   # X-Cache: MISS
curl -s 'http://127.0.0.1:8080/v7/weather/now?location=116.41,39.92' -i   # X-Cache: HIT
curl -s 'http://127.0.0.1:8080/v7/tropical/storm-list' -i                  # 403
```

## 已验证可用的免费端点（全部走专属 API Host）

| 端点 | 说明 |
|---|---|
| `/geo/v2/city/lookup` | 城市搜索 / 坐标反查（注意：新路径带 `/geo` 前缀） |
| `/v7/weather/now` `/v7/weather/24h` `/v7/weather/7d` | 实时 / 24 小时 / 7 天 |
| `/airquality/v1/current/{lat}/{lon}`（另有 `hourly` `daily`） | 空气质量（**纬度在前**、路径分隔） |

坐标顺序陷阱：天气与城市接口是 `location=经度,纬度`，空气质量 v1 是路径 `纬度/经度`。

## 服务器部署

```bash
./deploy/deploy.sh ubuntu@<server-ip>
```

要求：服务器已装 OpenJDK 21+（当前为 25）、腾讯云控制台防火墙已放行 TCP 8080。
部署内容：`build/install/AuraBackend/` → `/opt/aura-backend`，`config.properties`
与 `secrets/qw_private.pem` 同步至该目录，systemd 服务名 `aura-backend`。

运维命令：

```bash
sudo systemctl status|restart|stop aura-backend
journalctl -u aura-backend -f          # 看日志
```

## 配置（config.properties，环境变量可覆盖）

| 键 | 环境变量 | 说明 |
|---|---|---|
| `server.port` | `AURA_PORT` | 监听端口，默认 8080 |
| `qw.apiHost` | `QW_API_HOST` | 和风专属 API Host |
| `qw.developerId` | `QW_DEVELOPER_ID` | 开发者 ID（JWT `iss`） |
| `qw.projectId` | `QW_PROJECT_ID` | 项目 ID（JWT `sub`） |
| `qw.credentialId` | `QW_CREDENTIAL_ID` | 凭据 ID（JWT header `kid`） |
| `qw.privateKeyPath` | `QW_PRIVATE_KEY_PATH` | Ed25519 私钥路径（相对 WorkingDirectory） |
| `guard.ratePerMinute` | `AURA_RATE_PER_MINUTE` | 每 IP 每分钟请求上限，默认 30 |
| `guard.dailyUpstreamLimit` | `AURA_DAILY_UPSTREAM_LIMIT` | 每日真实出网（MISS）上限，默认 1500 |

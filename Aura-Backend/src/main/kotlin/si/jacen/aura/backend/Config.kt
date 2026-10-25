package si.jacen.aura.backend

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

data class Config(
    val port: Int,
    val apiHost: String,
    val developerId: String,
    val projectId: String,
    val credentialId: String,
    val privateKeyPath: Path,
    val ratePerMinute: Int,
    val dailyUpstreamLimit: Long,
) {
    companion object {
        /**
         * 配置来源优先级：环境变量 > config.properties > 默认值。
         * properties 文件路径可用 -Daura.config=xxx 覆盖。
         */
        fun load(): Config {
            val props = Properties()
            val file = Path.of(System.getProperty("aura.config", "config.properties"))
            if (Files.exists(file)) {
                file.toFile().inputStream().use { props.load(it) }
            }

            fun pick(envKey: String, propKey: String, default: String = ""): String =
                System.getenv(envKey)?.takeIf { it.isNotBlank() }
                    ?: props.getProperty(propKey)?.takeIf { it.isNotBlank() }
                    ?: default

            val cfg = Config(
                port = pick("AURA_PORT", "server.port", "8080").toInt(),
                apiHost = pick("QW_API_HOST", "qw.apiHost"),
                developerId = pick("QW_DEVELOPER_ID", "qw.developerId"),
                projectId = pick("QW_PROJECT_ID", "qw.projectId"),
                credentialId = pick("QW_CREDENTIAL_ID", "qw.credentialId"),
                privateKeyPath = Path.of(pick("QW_PRIVATE_KEY_PATH", "qw.privateKeyPath", "secrets/qw_private.pem")),
                ratePerMinute = pick("AURA_RATE_PER_MINUTE", "guard.ratePerMinute", "30").toInt(),
                dailyUpstreamLimit = pick("AURA_DAILY_UPSTREAM_LIMIT", "guard.dailyUpstreamLimit", "1500").toLong(),
            )
            cfg.validate()
            return cfg
        }
    }

    private fun validate() {
        require(apiHost.isNotBlank()) { "缺少和风 API Host（qw.apiHost / QW_API_HOST）" }
        require(developerId.isNotBlank()) { "缺少开发者 ID（qw.developerId / QW_DEVELOPER_ID）" }
        require(projectId.isNotBlank()) { "缺少项目 ID（qw.projectId / QW_PROJECT_ID）" }
        require(credentialId.isNotBlank()) { "缺少凭据 ID（qw.credentialId / QW_CREDENTIAL_ID）" }
        require(Files.exists(privateKeyPath)) { "私钥文件不存在：$privateKeyPath" }
    }
}

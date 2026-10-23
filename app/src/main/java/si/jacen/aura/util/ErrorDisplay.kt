package si.jacen.aura.util

import retrofit2.HttpException
import java.io.IOException

/** 把数据层异常翻译成用户可读的提示。 */
fun Throwable.userMessage(): String {
    val http = this as? HttpException
    if (http != null) {
        val code = http.code()
        return when (code) {
            502 -> "后端服务不可用，请联系管理员修复"
            429 -> "请求过于频繁或当日额度已用尽，请稍后再试"
            403 -> "请求被代理拦截（付费接口）"
            404 -> "接口不存在，请检查代理与路径"
            else -> "服务返回错误（HTTP $code），请联系管理员"
        }
    }
    return when (this) {
        is IOException -> "无法连接服务器：请检查网络；若网络正常则后端已失效，请联系管理员修复"
        else -> message ?: "未知错误"
    }
}

package si.jacen.aura.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.usecase.LocationProvider
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** 一次性取当前坐标。调用前 UI 已确保权限授予；任何失败一律返回 null，由上层降级。 */
    @SuppressLint("MissingPermission")
    override suspend fun currentCoordinate(): Coordinate? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                val token = com.google.android.gms.tasks.CancellationTokenSource().token
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token)
                    .addOnSuccessListener { location ->
                        cont.resumeWith(Result.success(location?.let { Coordinate(it.latitude, it.longitude) }))
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                    .addOnCanceledListener {
                        if (cont.isActive) cont.cancel(CancellationException("定位请求被取消"))
                    }
            }.onFailure {
                if (cont.isActive) cont.resumeWith(Result.success(null))
            }
        }
}

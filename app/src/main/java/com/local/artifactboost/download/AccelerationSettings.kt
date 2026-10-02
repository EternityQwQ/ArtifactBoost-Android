package com.local.artifactboost.download

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "artifactboost_settings")

/**
 * 持久化的加速设置：在「设置」页调好并保存，下载时直接套用。
 * 与 iOS 版的 AccelerationSettings 行为一致（含测速结果的 24 小时有效期）。
 */
data class AccelerationSettings(
    val connections: Int = 16,
    val mode: RouteMode = RouteMode.SMART,
    val customPrefix: String = "",
    /** 「设置」页测速得到的最快通道 */
    val testedRoute: DownloadRoute? = null,
    val testedSpeed: Double = 0.0,
    val testedAt: Long = 0L,
) {
    val clampedConnections: Int get() = connections.coerceIn(1, 64)

    /** 当前设置下的候选通道（直连永远保留兜底） */
    fun candidateRoutes(isPrivateRepo: Boolean = false): List<DownloadRoute> = when (mode) {
        RouteMode.DIRECT -> listOf(DownloadRoute.Direct)
        RouteMode.CUSTOM -> {
            val prefix = DownloadRoute.normalizedPrefix(customPrefix)
            if (prefix.isEmpty()) listOf(DownloadRoute.Direct)
            else listOf(DownloadRoute("自定义加速", prefix), DownloadRoute.Direct)
        }
        RouteMode.SMART -> {
            // 私有仓库的产物不该交给第三方，强制直连
            if (isPrivateRepo) listOf(DownloadRoute.Direct)
            else listOf(DownloadRoute.Direct) + DownloadRoute.BuiltInMirrors
        }
    }

    /** 可以直接沿用的测速结果（24 小时内有效、且不在私有仓库里用镜像） */
    fun savedPlan(isPrivateRepo: Boolean): List<ScoredRoute>? {
        val route = testedRoute ?: return null
        if (testedAt <= 0L) return null
        if (System.currentTimeMillis() - testedAt >= 24 * 3600_000L) return null
        if (isPrivateRepo && !route.isDirect) return null
        if (route !in candidateRoutes(isPrivateRepo)) return null
        return listOf(ScoredRoute(route, maxOf(testedSpeed, 0.01)))
    }

    companion object {
        val connectionOptions = listOf(8, 16, 32, 64)

        private object Keys {
            val connections = intPreferencesKey("ab.connections")
            val mode = stringPreferencesKey("ab.routeMode")
            val customPrefix = stringPreferencesKey("ab.customPrefix")
            val testedName = stringPreferencesKey("ab.testedRouteName")
            val testedPrefix = stringPreferencesKey("ab.testedRoutePrefix")
            val testedSpeed = doublePreferencesKey("ab.testedRouteSpeed")
            val testedAt = longPreferencesKey("ab.testedRouteDate")
            val loggedIn = booleanPreferencesKey("ab.loggedIn")
        }

        suspend fun load(context: Context): AccelerationSettings {
            val prefs = context.dataStore.data.first()
            val storedConnections = prefs[Keys.connections] ?: 0
            val name = prefs[Keys.testedName]
            return AccelerationSettings(
                connections = if (storedConnections > 0) storedConnections else 16,
                mode = RouteMode.entries.firstOrNull { it.name == prefs[Keys.mode] }
                    ?: RouteMode.SMART,
                customPrefix = prefs[Keys.customPrefix] ?: "",
                testedRoute = name?.let {
                    DownloadRoute(it, prefs[Keys.testedPrefix] ?: "")
                },
                testedSpeed = prefs[Keys.testedSpeed] ?: 0.0,
                testedAt = prefs[Keys.testedAt] ?: 0L,
            )
        }

        suspend fun save(context: Context, settings: AccelerationSettings) {
            context.dataStore.edit { prefs ->
                prefs[Keys.connections] = settings.clampedConnections
                prefs[Keys.mode] = settings.mode.name
                prefs[Keys.customPrefix] = settings.customPrefix

                val route = settings.testedRoute
                if (route != null) {
                    prefs[Keys.testedName] = route.name
                    prefs[Keys.testedPrefix] = route.prefix
                    prefs[Keys.testedSpeed] = settings.testedSpeed
                    prefs[Keys.testedAt] =
                        if (settings.testedAt > 0) settings.testedAt else System.currentTimeMillis()
                } else {
                    prefs.remove(Keys.testedName)
                    prefs.remove(Keys.testedPrefix)
                    prefs.remove(Keys.testedSpeed)
                    prefs.remove(Keys.testedAt)
                }
            }
        }

        /** 记录一次测速结果 */
        suspend fun record(context: Context, settings: AccelerationSettings, route: DownloadRoute, speed: Double) {
            save(
                context,
                settings.copy(
                    testedRoute = route,
                    testedSpeed = speed,
                    testedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}

/** 测速用的真实目标（优先产物，其次构建日志） */
data class SpeedTestTarget(
    val url: String,
    val label: String,
    val isPrivate: Boolean,
)

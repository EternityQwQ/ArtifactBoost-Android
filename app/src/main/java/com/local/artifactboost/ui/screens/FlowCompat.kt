package com.local.artifactboost.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

/**
 * `collectAsState()` 的小包装。StateFlow 在 Compose 里统一用这个读，
 * 免得每个调用点都写一遍 `androidx.compose.runtime.collectAsState` 的一长串 import。
 */
@Composable
fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = this.collectAsState()

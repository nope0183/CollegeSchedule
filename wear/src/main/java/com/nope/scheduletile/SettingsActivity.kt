package com.nope.scheduletile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.nope.scheduletile.data.Prefs
import com.nope.scheduletile.data.ScheduleRepository
import com.nope.scheduletile.data.ScheduleSync
import com.nope.scheduletile.data.Surfaces
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsApp() }
    }
}

/** Material You: динамическая палитра от циферблата, если система её поддерживает. */
@Composable
private fun ScheduleTheme(content: @Composable () -> Unit) {
    val dynamic = dynamicColorScheme(LocalContext.current)
    if (dynamic != null) {
        MaterialTheme(colorScheme = dynamic, content = content)
    } else {
        MaterialTheme(content = content)
    }
}

@Composable
private fun SettingsApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    var groups by remember { mutableStateOf(ScheduleRepository.groups(ctx)) }
    var selected by remember { mutableStateOf(Prefs.group(ctx)) }
    var lastSync by remember { mutableStateOf(Prefs.lastSync(ctx)) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val status = when {
        loading -> "Загрузка…"
        failed -> "Не удалось обновить. Проверьте сеть."
        lastSync == 0L -> "Расписание ещё не загружалось"
        else -> "Обновлено " + Instant.ofEpochMilli(lastSync).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")))
    }

    ScheduleTheme {
        AppScaffold {
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(
                        onClick = {
                            if (loading) return@EdgeButton
                            scope.launch {
                                loading = true
                                failed = false
                                val ok = ScheduleSync.fetchAll(ctx)
                                failed = !ok
                                groups = ScheduleRepository.groups(ctx)
                                lastSync = Prefs.lastSync(ctx)
                                loading = false
                            }
                        },
                        buttonSize = EdgeButtonSize.Medium,
                    ) {
                        Text("Обновить")
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                    item {
                        ListHeader(
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        ) { Text("Группа") }
                    }
                    if (groups.isEmpty()) {
                        item {
                            Text(
                                "Список групп пуст. Нажмите «Обновить» (нужен интернет).",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    items(groups) { g ->
                        RadioButton(
                            label = { Text(g) },
                            selected = g == selected,
                            onSelect = {
                                selected = g
                                Prefs.setGroup(ctx, g)
                                Surfaces.refreshAll(ctx)
                            },
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        )
                    }
                    item {
                        Text(
                            status,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item {
                        Text(
                            "Авто-обновление раз в час, только на зарядке.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

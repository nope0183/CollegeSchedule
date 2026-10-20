package com.nope.scheduletile.phone

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nope.scheduletile.data.Prefs
import com.nope.scheduletile.data.ScheduleRepository
import com.nope.scheduletile.data.ScheduleSync
import com.nope.scheduletile.data.Surfaces
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Выбор группы. Открывается как приложение «Расписание» и как экран настройки виджета
 * (при добавлении на рабочий стол и при «Изменить виджет»).
 */
class SettingsActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val configuring = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (configuring) setResult(RESULT_CANCELED, resultIntent())

        setContent {
            ScheduleTheme {
                SettingsScreen(
                    configuring = configuring,
                    onChanged = { if (configuring) setResult(RESULT_OK, resultIntent()) },
                    onDone = { finish() },
                )
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

/** Material You: динамическая палитра на Android 12+, иначе стандартная. */
@Composable
private fun ScheduleTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SettingsScreen(configuring: Boolean, onChanged: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf(ScheduleRepository.groups(ctx)) }
    var selected by remember { mutableStateOf(Prefs.group(ctx)) }
    var lastSync by remember { mutableStateOf(Prefs.lastSync(ctx)) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val status = when {
        loading -> "Загрузка…"
        failed -> "Не удалось обновить. Проверьте интернет."
        lastSync == 0L -> "Расписание ещё не загружалось"
        else -> "Обновлено " + Instant.ofEpochMilli(lastSync).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")))
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Расписание", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Выберите группу для виджета",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            if (groups.isEmpty()) {
                Text(
                    "Список групп пуст. Нажмите «Обновить» (нужен интернет).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(groups) { g ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = g
                                Prefs.setGroup(ctx, g)
                                Surfaces.refreshAll(ctx)
                                onChanged()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = g == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(g, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = !loading,
                    onClick = {
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
                ) { Text("Обновить") }

                if (configuring) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selected != null,
                        colors = ButtonDefaults.buttonColors(),
                        onClick = onDone,
                    ) { Text("Готово") }
                }
            }
        }
    }
}

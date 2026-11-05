package app.carflash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.carflash.ui.components.AppHead
import app.carflash.ui.screens.HomeActions
import app.carflash.ui.screens.HomeScreen
import app.carflash.ui.screens.OnboardingScreen
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CarflashTheme
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.vehicle.VehicleGateway.Companion.log
import java.util.Calendar

/** 화면 경로. 와이어프레임 v0.3 번호를 그대로 쓴다. */
sealed interface Route {
    data object Onboard : Route
    data object Home : Route
    data class Prep(val showId: String) : Route
    data class Todo(val title: String) : Route
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = app
        val first = !app.prefs.getBoolean("onboarded", false)
        setContent {
            BoxWithConstraints(Modifier.fillMaxSize().background(Cf.bg).semantics { testTagsAsResourceId = true }) {
                // 앱 영역 1682 = landscape, 분할 840 = portrait
                val layout = if (maxWidth < 1200.dp) Layout.Portrait else Layout.Landscape
                CarflashTheme(layout) {
                    var route by remember { mutableStateOf<Route>(if (first) Route.Onboard else Route.Home) }
                    val go: (Route) -> Unit = { r -> log("NAV $r layout=$layout"); route = r }
                    val gear by app.vehicle.gear.collectAsState()
                    val caps by app.vehicle.caps.collectAsState()
                    BackHandler(enabled = route !is Route.Home && route !is Route.Onboard) { go(Route.Home) }
                    when (val r = route) {
                        Route.Onboard -> OnboardingScreen(gear, caps) {
                            app.prefs.edit().putBoolean("onboarded", true).apply()
                            go(Route.Home)
                        }
                        Route.Home -> {
                            val today = app.shows.today(Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
                            HomeScreen(
                                gear = gear, repo = app.shows, today = today, recent = emptyList(), online = true, banner = null,
                                a = HomeActions(
                                    openShow = { go(Route.Prep(it.id)) },
                                    code = { go(Route.Todo("코드로 불러오기")) },
                                    group = { go(Route.Todo("플래시몹 초대")) },
                                    create = { go(Route.Todo("플래시몹 만들기")) },
                                    settings = { go(Route.Onboard) },
                                ),
                            )
                        }
                        is Route.Prep -> Todo(app.shows.byId(r.showId)?.title ?: "플래시몹", gear) { go(Route.Home) }
                        is Route.Todo -> Todo(r.title, gear) { go(Route.Home) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

/** 다음 스프린트에서 채울 화면 자리. */
@Composable
private fun Todo(title: String, gear: app.carflash.vehicle.Gear, back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_todo")) {
        AppHead(title, gear, onBack = back)
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("준비 중인 화면이에요", style = CfType.body, color = Cf.text2)
        }
    }
}

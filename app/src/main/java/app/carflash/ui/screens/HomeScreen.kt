package app.carflash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carflash.data.Show
import app.carflash.data.ShowRepository
import app.carflash.haechi.HaechiState
import app.carflash.ui.components.AppHead
import app.carflash.ui.components.Cover
import app.carflash.ui.components.EffectBadges
import app.carflash.ui.components.HaechiView
import app.carflash.ui.components.IconBtn
import app.carflash.ui.components.TileButton
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.ui.theme.LocalLayout
import app.carflash.vehicle.Gear

data class HomeActions(
    val openShow: (Show) -> Unit,
    val code: () -> Unit,
    val group: () -> Unit,
    val create: () -> Unit,
    val settings: () -> Unit,
)

/** A2 홈. 가장 큰 면 = 오늘의 플래시몹. 자동 재생·자동 넘김 없음. 해치만 작게 움직인다. */
@Composable
fun HomeScreen(
    gear: Gear,
    repo: ShowRepository,
    today: Show,
    recent: List<Show>,
    online: Boolean,
    banner: (@Composable () -> Unit)?,
    a: HomeActions,
) {
    val port = LocalLayout.current == Layout.Portrait
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_home")) {
        AppHead("카플래시", gear, right = { IconBtn(Icons.Rounded.Settings, "설정", Modifier.testTag("settings"), plain = true, onClick = a.settings) })
        val recommended = repo.presets.filter { it.id != today.id }
        if (port) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                banner?.invoke()
                Box {
                    HeroTile(today, repo, Modifier.fillMaxWidth().height(if (banner != null) 360.dp else 440.dp), a.openShow)
                    HaechiView(HaechiState.IDLE, 250.dp, Modifier.align(Alignment.TopEnd).padding(end = 8.dp))
                }
                Tiles(online, short = true, a)
                Text("추천 플래시몹", style = CfType.title, color = Cf.text)
                recommended.forEach { ShowRow(it, repo, a.openShow) }
                if (recent.isNotEmpty()) {
                    Text("최근", style = CfType.title, color = Cf.text)
                    recent.forEach { ShowRow(it, repo, a.openShow) }
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.width(760.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    banner?.invoke()
                    Box(Modifier.weight(1f)) {
                        HeroTile(today, repo, Modifier.fillMaxSize(), a.openShow)
                        HaechiView(HaechiState.IDLE, 330.dp, Modifier.align(Alignment.TopEnd))
                    }
                    Tiles(online, short = false, a)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("추천 플래시몹", style = CfType.title, color = Cf.text)
                    recommended.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { ShowCard(it, repo, Modifier.weight(1f), a.openShow) }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                    if (recent.isNotEmpty()) {
                        Text("최근", style = CfType.title, color = Cf.text, modifier = Modifier.padding(top = 8.dp))
                        recent.forEach { ShowRow(it, repo, a.openShow) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tiles(online: Boolean, short: Boolean, a: HomeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TileButton(if (short) "코드" else "코드로 불러오기", Icons.Rounded.Pin, Modifier.weight(1f).testTag("home_code"), enabled = online, onClick = a.code)
        TileButton("플래시몹 초대", Icons.Rounded.Groups, Modifier.weight(1f).testTag("home_group"), enabled = online, onClick = a.group)
        TileButton(if (short) "만들기" else "플래시몹 만들기", Icons.Rounded.Edit, Modifier.weight(1f).testTag("home_create"), onClick = a.create)
    }
}

@Composable
private fun HeroTile(show: Show, repo: ShowRepository, modifier: Modifier, open: (Show) -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(Cf.rLg)).clickable(role = Role.Button) { open(show) }.testTag("home_today"),
    ) {
        Cover(show.cover, show.gradient, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC131417)), startY = 120f)))
        Column(Modifier.align(Alignment.BottomStart).padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("오늘의 플래시몹 · 누르면 바로 준비", style = CfType.label, color = Color.White.copy(alpha = 0.85f))
            Text(show.title, style = CfType.headline, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(repo.durationLabel(show), style = CfType.label, color = Color.White.copy(alpha = 0.85f))
                Text(show.effects.sortedBy { it.ordinal }.joinToString(" · ") { it.label }, style = CfType.label, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(48.dp).size(Cf.touchXl).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, "준비", tint = Color(0xFF0E0F13), modifier = Modifier.size(72.dp)) }
    }
}

@Composable
fun ShowCard(show: Show, repo: ShowRepository, modifier: Modifier, open: (Show) -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface).clickable(role = Role.Button) { open(show) }.testTag("show_${show.id}"),
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            Cover(show.cover, show.gradient, Modifier.fillMaxSize())
            EffectBadges(show.effects, Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)) {
            Text(show.title, style = CfType.title.copy(fontSize = CfType.title.fontSize * 0.75f), color = Cf.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(repo.durationLabel(show), style = CfType.caption, color = Cf.text2)
        }
    }
}

@Composable
fun ShowRow(show: Show, repo: ShowRepository, open: (Show) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface)
            .clickable(role = Role.Button) { open(show) }.testTag("show_${show.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(show.cover, show.gradient, Modifier.size(120.dp))
        Column(Modifier.padding(horizontal = 24.dp)) {
            val prefix = when (show.origin) { Show.Origin.FRIEND -> "친구 플래시몹 · "; Show.Origin.MINE -> "내 플래시몹 · "; else -> "" }
            Text(prefix + show.title + (show.code?.let { " · ${it.take(3)} ${it.drop(3)}" } ?: ""), style = CfType.body, color = Cf.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${repo.durationLabel(show)} · 효과 ${show.effects.size}", style = CfType.caption, color = Cf.text2)
        }
    }
}

package com.hamfilm.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.hamfilm.app.ui.theme.*

/** رنگ‌های زیرنویس قابل انتخاب */
data class SubtitleColorOption(val name: String, val color: Color, val preview: Color)

val SubtitleColors = listOf(
    SubtitleColorOption("سفید", Color.White, Color.White),
    SubtitleColorOption("زرد", Color(0xFFFFD54F), Color(0xFFFFD54F)),
    SubtitleColorOption("سبز", Color(0xFF81C784), Color(0xFF81C784)),
    SubtitleColorOption("صورتی", Color(0xFFF48FB1), Color(0xFFF48FB1)),
    SubtitleColorOption("آبی", Color(0xFF64B5F6), Color(0xFF64B5F6)),
    SubtitleColorOption("قرمز", Color(0xFFE57373), Color(0xFFE57373)),
)

/**
 * شیت تنظیمات حرفه‌ای پلیر:
 *  - انتخاب ترک صوتی فایل/ویدیو
 *  - انتخاب فایل زیرنویس (.srt / .vtt)
 *  - تغییر رنگ زیرنویس
 */
@Composable
fun PlayerSettingsSheet(
    player: ExoPlayer?,
    onClose: () -> Unit,
    onSubtitleFile: (Uri) -> Unit,
    subtitleColorIndex: Int,
    onSubtitleColor: (Int) -> Unit,
    onSubtitleEnabled: (Boolean) -> Unit,
    subtitleEnabled: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = BrandCard,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(Modifier.padding(bottom = 32.dp).padding(horizontal = 20.dp)) {
            Text("⚙️ تنظیمات پلیر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))

            // ── ترک صوتی ──
            Text("🔊 ترک صوتی", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandText)
            Spacer(Modifier.height(8.dp))
            val audioTracks = remember(player) {
                player?.currentTracks?.groups
                    ?.filter { it.type == C.TRACK_TYPE_AUDIO }
                    ?: emptyList()
            }
            if (audioTracks.isEmpty()) {
                Text(
                    "ترک صوتی جداگانه‌ای پیدا نشد (پخش پیش‌فرض صدا)",
                    fontSize = 11.sp,
                    color = BrandTextMuted
                )
            } else {
                audioTracks.forEachIndexed { gi, group ->
                    val format = group.getTrackFormat(0)
                    val lang = format.language?.takeIf { it.isNotBlank() } ?: "ترک ${gi + 1}"
                    val selected = player?.trackSelectionParameters?.overrides?.any {
                        it.trackGroup == group
                    } == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) BrandCyan.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                player?.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .clearOverrides()
                                    .addOverride(TrackSelectionOverride(group, 0))
                                    .build()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selected) "✅ $lang" else lang,
                            fontSize = 13.sp,
                            color = if (selected) BrandCyan else BrandText,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── زیرنویس ──
            Text("📝 زیرنویس", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandText)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // سوییچ فعال/غیرفعال
                androidx.compose.material3.Switch(
                    checked = subtitleEnabled,
                    onCheckedChange = { onSubtitleEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = BrandCyan,
                        checkedThumbColor = Color.White
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (subtitleEnabled) "زیرنویس فعال" else "زیرنویس غیرفعال",
                    fontSize = 12.sp,
                    color = BrandTextMuted
                )
            }
            Spacer(Modifier.height(8.dp))
            // انتخاب فایل زیرنویس
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandPurple.copy(alpha = 0.15f))
                    .clickable { onSubtitleFile(Uri.EMPTY) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📄", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("انتخاب فایل زیرنویس", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandText)
                    Text(".srt یا .vtt", fontSize = 10.sp, color = BrandTextMuted)
                }
            }

            // ── رنگ زیرنویس ──
            Text("🎨 رنگ زیرنویس", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitleColors.forEachIndexed { i, opt ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(opt.color)
                            .then(
                                if (i == subtitleColorIndex)
                                    Modifier
                                        .then(
                                            Modifier.background(Color.Transparent)
                                        )
                                else Modifier
                            )
                            .clickable { onSubtitleColor(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (i == subtitleColorIndex) {
                            Text("✓", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** ساخت CaptionStyle برای رنگ زیرنویس */
fun buildCaptionStyle(index: Int): CaptionStyleCompat {
    val color = SubtitleColors[index.coerceIn(0, SubtitleColors.size - 1)].color
    return CaptionStyleCompat.Builder()
        .setForegroundColor(color)
        .setBackgroundColor(Color.Black.copy(alpha = 0.6f))
        .build()
}

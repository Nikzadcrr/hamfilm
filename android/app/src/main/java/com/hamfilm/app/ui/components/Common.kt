package com.hamfilm.app.ui.components

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamfilm.app.data.ApiConfig
import com.hamfilm.app.ui.theme.*

// ---------- پس‌زمینه گرادیانی ----------
@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(BrandBg, BrandCard, BrandBg)))
    ) { content() }
}

// ---------- دکمه گرادیانی اصلی ----------
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BrandGradient),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = BrandCardLight,
            disabledContentColor = BrandTextMuted
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            icon?.invoke()
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ---------- کارت ----------
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(BrandCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content
    )
}

// ---------- فیلد ورودی استاندارد ----------
@Composable
fun HamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { if (label.isNotBlank()) Text(label) },
        placeholder = { Text(placeholder, color = BrandTextMuted) },
        singleLine = singleLine,
        visualTransformation = if (password)
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandCyan,
            unfocusedBorderColor = BrandCardLight,
            focusedLabelColor = BrandCyan,
            cursorColor = BrandCyan
        )
    )
}

// ---------- لیست آواتارهای تصویری (id بک‌اند: a1..a10) ----------
val AvatarIds = listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10")

/** آدرس تصویر آواتار از id (روی همان بک‌اند) */
fun avatarUrl(id: String): String = ApiConfig.baseUrl.trimEnd('/') + "/avatars/avatar-" + id.removePrefix("a") + ".jpg"

/** آواتار تصویری گرد — اگر id نبود، ایموجی پیش‌فرض 🎬 */
@Composable
fun AvatarImage(avatarId: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(BrandGradientSoft),
        contentAlignment = Alignment.Center
    ) {
        if (avatarId.isNotBlank() && avatarId in AvatarIds) {
            AsyncImage(
                model = avatarUrl(avatarId),
                contentDescription = "آواتار",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size / 2))
            )
        } else {
            Text("🎬", fontSize = (size.value * 0.5).sp)
        }
    }
}

// ---------- آواتار انتخابی در ساخت/ورود اتاق (گرید) ----------
@Composable
fun AvatarChip(avatarId: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    AvatarImage(avatarId = avatarId, modifier = modifier, size = size)
}

// ---------- نشانگر وضعیت ----------
@Composable
fun StatusBar(text: String, color: Color = BrandGreen, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- پیام خالی ----------
@Composable
fun EmptyState(emoji: String, title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = BrandTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------- برچسب بخش (بالای فیلدها) ----------
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = BrandTextMuted,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

// ---------- دکمه گرد رنگی برای نوار بالای اتاق ----------
@Composable
fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF43F5E))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    badge.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------- نقطه زنده چشمک‌زن ----------
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "live")
    val alpha by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )
    Box(
        Modifier
            .size(7.dp)
            .clip(RoundedCornerShape(50))
            .background(BrandGreen.copy(alpha = alpha))
    )
}

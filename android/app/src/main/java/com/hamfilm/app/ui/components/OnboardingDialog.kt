package com.hamfilm.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamfilm.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * راهنمای قدم‌به‌قدم اولین اجرا (الهام از اپ ببینیم)
 */
@Composable
fun OnboardingDialog(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple("🎬", "اتاق بساز", "لینک فیلم یا ویدیوی یوتیوب را بگذار و یک کد اتاق بگیر."),
        Triple("🔑", "کد را بفرست", "کد را برای دوستانت بفرست؛ آن‌ها بدون ثبت‌نام وارد می‌شوند."),
        Triple("⚡", "هم‌زمان ببینید", "پخش ویدیو برای همه هماهنگ است؛ چت و واکنش لحظه‌ای هم دارید!")
    )
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandCard,
        title = {
            Text(
                "خوش آمدید به هم‌فیلم 👋",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val (emoji, title, desc) = steps[step]
                Text(emoji, fontSize = 52.sp)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(if (i == step) 18.dp else 8.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (i == step) BrandPurple else BrandCardLight)
                        )
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = if (step < 2) "بعدی" else "شروع!",
                onClick = {
                    if (step < 2) step++ else onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

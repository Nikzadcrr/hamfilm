package com.hamfilm.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import com.hamfilm.app.data.ApiConfig
import com.hamfilm.app.R
import com.hamfilm.app.data.api.ApiClient
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * تنظیمات سرور: انتخاب بین بک‌اند کلادفلر و VPS
 * اینجا همان‌جاست که اپ را بین دو سرور جابه‌جا می‌کنی.
 */
@Composable
fun SettingsScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val repo = remember { AppRepository() }
    var url by remember { mutableStateOf(ApiConfig.baseUrl.trimEnd('/')) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_arrow_forward), "بازگشت", tint = BrandTextMuted) }
                Text("⚙️ تنظیمات سرور", style = MaterialTheme.typography.headlineSmall, color = BrandText)
            }
            Spacer(Modifier.height(16.dp))
            Text("آدرس بک‌اند (API, color = BrandText):", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            HamTextField(
                url, { url = it; testResult = null; saved = false },
                "Base URL",
                placeholder = "https://example.workers.dev/ یا https://vps.example.com/"
            )
            Spacer(Modifier.height(10.dp))
            Text("💡 هم برای کلادفلر (workers.dev, color = BrandText) و هم برای VPS قابل استفاده است — کافی است آدرس را عوض کنی.", fontSize = 12.sp, color = BrandTextMuted)

            testResult?.let {
                Spacer(Modifier.height(12.dp))
                StatusBar(it, if (testOk) BrandGreen else BrandDanger, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    testing = true; testResult = null
                    scope.launch {
                        val old = ApiConfig.baseUrl
                        ApiConfig.setBaseUrl(context, url)
                        ApiClient.reset()
                        val ok = runCatching { repo.settings() }.isSuccess
                        if (!ok) {
                            // برگرداندن آدرس قبلی اگر وصل نشد
                            ApiConfig.setBaseUrl(context, old)
                            ApiClient.reset()
                        }
                        testOk = ok
                        testResult = if (ok) "✅ اتصال موفق — سرور فعال است" else "❌ اتصال برقرار نشد (آدرس قبلی برگشت)"
                        testing = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan)
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = BrandCyan, strokeWidth = 2.dp)
                } else {
                    Text("🧪 تست اتصال", fontWeight = FontWeight.Bold, color = BrandText)
                }
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = "ذخیره و استفاده از این سرور",
                onClick = {
                    ApiConfig.setBaseUrl(context, url)
                    ApiClient.reset()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (saved) {
                Spacer(Modifier.height(10.dp))
                StatusBar("✅ ذخیره شد", BrandGreen, Modifier.fillMaxWidth())
            }
        }
    }
}

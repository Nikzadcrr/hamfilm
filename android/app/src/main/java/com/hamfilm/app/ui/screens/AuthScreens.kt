package com.hamfilm.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import com.hamfilm.app.viewmodel.AuthViewModel

@Composable
fun LoginScreen(nav: NavHostController) {
    val vm = remember { AuthViewModel() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("👋", fontSize = 44.sp)
            Text("ورود به هم‌فیلم", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            HamTextField(email, { email = it }, "ایمیل", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            HamTextField(password, { password = it }, "رمز عبور", password = true)
            vm.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("ورود", loading = vm.loading, onClick = {
                vm.login(email, password) { ok -> if (ok) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                Text("حساب نداری؟", color = BrandTextMuted)
                TextButton(onClick = { nav.navigate(Routes.REGISTER) }) {
                    Text("ثبت‌نام", color = BrandCyan, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = { nav.navigate(Routes.HOME) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("ورود بدون حساب (مهمان)", color = BrandTextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun RegisterScreen(nav: NavHostController) {
    val vm = remember { AuthViewModel() }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("📝", fontSize = 44.sp)
            Text("ساخت حساب", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            HamTextField(name, { name = it }, "نام نمایشی")
            Spacer(Modifier.height(14.dp))
            HamTextField(email, { email = it }, "ایمیل", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            HamTextField(password, { password = it }, "رمز عبور", password = true)
            vm.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("ثبت‌نام", loading = vm.loading, onClick = {
                vm.register(name, email, password) { ok ->
                    if (ok) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                }
            }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("قبلاً حساب داشتم — ورود", color = BrandCyan)
            }
        }
    }
}

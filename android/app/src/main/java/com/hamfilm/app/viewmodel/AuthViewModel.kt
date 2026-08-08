package com.hamfilm.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.api.ApiClient
import com.hamfilm.app.data.api.AppRepository
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repo = AppRepository()

    var isLoggedIn by mutableStateOf(TokenStore.token.isNotBlank())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        // اگر توکن هست، اعتبارش را چک کن
        if (isLoggedIn) {
            viewModelScope.launch {
                val me = repo.me()
                if (me == null) {
                    logout()
                } else {
                    me.user?.let {
                        if (it.name.isNotBlank()) TokenStore.name = it.name
                        if (it.avatar.isNotBlank()) TokenStore.avatar = it.avatar
                        if (!it.email.isNullOrBlank()) TokenStore.email = it.email
                    }
                }
            }
        }
    }

    fun login(email: String, password: String, onDone: (Boolean) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            error = "ایمیل و رمز را وارد کنید"
            onDone(false)
            return
        }
        viewModelScope.launch {
            loading = true; error = null
            try {
                val res = repo.login(email.trim(), password)
                saveSession(res)
                onDone(true)
            } catch (e: Exception) {
                error = e.message ?: "خطا در ورود"
                onDone(false)
            } finally { loading = false }
        }
    }

    fun register(name: String, email: String, password: String, onDone: (Boolean) -> Unit) {
        if (name.isBlank() || email.isBlank() || password.length < 4) {
            error = "نام، ایمیل و رمز (حداقل ۴ کاراکتر) را وارد کنید"
            onDone(false)
            return
        }
        viewModelScope.launch {
            loading = true; error = null
            try {
                val res = repo.register(name.trim(), email.trim(), password)
                saveSession(res)
                onDone(true)
            } catch (e: Exception) {
                error = e.message ?: "خطا در ثبت‌نام"
                onDone(false)
            } finally { loading = false }
        }
    }

    private fun saveSession(res: com.hamfilm.app.data.model.AuthResponse) {
        TokenStore.token = res.token
        res.user?.let {
            TokenStore.name = it.name.ifBlank { TokenStore.name }
            if (it.avatar.isNotBlank()) TokenStore.avatar = it.avatar
            if (!it.email.isNullOrBlank()) TokenStore.email = it.email
        }
        isLoggedIn = true
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            TokenStore.clear()
            ApiClient.reset()
            isLoggedIn = false
        }
    }
}

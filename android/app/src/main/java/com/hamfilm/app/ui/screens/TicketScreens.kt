package com.hamfilm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.sp
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.R
import com.hamfilm.app.data.model.TicketDetail
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.Ticket
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TicketsScreen(nav: NavHostController) {
    val repo = remember { AppRepository() }
    var tickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showNew by remember { mutableStateOf(false) }

    suspend fun load() {
        tickets = repo.tickets()
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_arrow_forward), "بازگشت", tint = BrandTextMuted) }
                Text("🎟️ تیکت‌های من", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (TokenStore.token.isNotBlank()) {
                    FilledIconButton(onClick = { showNew = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandPurple)) {
                        Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_add), "تیکت جدید", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandCyan) }
            } else if (tickets.isEmpty()) {
                EmptyState("📭", "تیکتی نداری", if (TokenStore.token.isBlank()) "برای ارسال تیکت، ابتدا وارد حساب شو." else "برای ارتباط با پشتیبانی، تیکت جدید بساز.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tickets, key = { it.id }) { t ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.subject, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, color = BrandText)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(t.createdAt)),
                                        color = BrandTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                StatusBar(
                                    when (t.status) {
                                        "open" -> "باز"
                                        "closed" -> "بسته"
                                        else -> t.status
                                    },
                                    if (t.status == "open") BrandGreen else BrandTextMuted
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { nav.navigate(Routes.ticket(t.id)) }) {
                                Text("مشاهده گفتگو ‹", color = BrandCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewTicketDialog(
            onClose = { showNew = false },
            onCreated = { showNew = false; loading = true }
        )
    }
}

@Composable
private fun NewTicketDialog(onClose: () -> Unit, onCreated: () -> Unit) {
    val repo = remember { AppRepository() }
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandCard,
        title = { Text("تیکت جدید", color = BrandText) },
        text = {
            Column {
                HamTextField(subject, { subject = it }, "موضوع")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("توضیح مشکل", color = BrandText) },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandCyan, unfocusedBorderColor = BrandCardLight)
                )
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = BrandDanger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            GradientButton(
                text = "ارسال",
                loading = sending,
                onClick = {
                    if (subject.isBlank() || body.isBlank()) {
                        error = "موضوع و توضیح را کامل کن"
                        return@GradientButton
                    }
                    sending = true
                    scope.launch {
                        try {
                            repo.createTicket(subject.trim(), body.trim())
                            onCreated()
                        } catch (e: Exception) {
                            error = e.message
                        }
                        sending = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("انصراف", color = BrandTextMuted) }
        }
    )
}

@Composable
fun TicketDetailScreen(nav: NavHostController, id: String) {
    val repo = remember { AppRepository() }
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<com.hamfilm.app.data.model.TicketDetail?>(null) }
    var reply by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            val msgs = repo.ticketMessages(id)
            detail = detail?.copy(messages = msgs) ?: TicketDetail(id = id, messages = msgs)
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    GradientBackground(Modifier.fillMaxSize()) {
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = BrandDanger)
                    TextButton(onClick = { nav.popBackStack() }) { Text("بازگشت", color = BrandText) }
                } else CircularProgressIndicator(color = BrandCyan)
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_arrow_forward), "بازگشت", tint = BrandTextMuted) }
                    Text(d.subject, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    StatusBar(if (d.status == "open") "باز" else "بسته", if (d.status == "open") BrandGreen else BrandTextMuted)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(d.messages, key = { it.id }) { r ->
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (r.isMine) BrandPurple.copy(alpha = 0.25f) else BrandCard)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(if (r.isBot) "🤖 " + r.author else r.author, fontSize = 11.sp, color = if (r.isBot) BrandCyan else BrandTextMuted, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(r.text, fontSize = 14.sp, color = BrandText)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(r.ts)),
                                        fontSize = 10.sp,
                                        color = BrandTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
                if (d.status == "open" && TokenStore.token.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { reply = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("پاسخ شما…", color = BrandTextMuted) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandCyan, unfocusedBorderColor = BrandCardLight)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (reply.isBlank()) return@FilledIconButton
                                sending = true
                                scope.launch {
                                    try {
                                        repo.replyTicket(id, reply.trim())
                                        reply = ""
                                        load()
                                    } catch (e: Exception) { /* خطا */ }
                                    sending = false
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandPurple)
                        ) {
                            Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_send), null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
    }
}

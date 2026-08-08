package com.hamfilm.app.data.model

import com.google.gson.annotations.SerializedName

// ---------- احراز هویت (بک‌اند: نام کاربری + رمز — بدون ایمیل) ----------
data class User(
    val id: String = "",
    val name: String = "",
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("last_seen") val lastSeen: Long? = null
)

data class AuthResponse(
    val token: String = "",
    val user: User? = null
)

data class RegisterRequest(val name: String, val pass: String)
data class LoginRequest(val name: String, val pass: String)

// ---------- تنظیمات عمومی ----------
data class PublicSettings(
    val registrationRequired: Boolean = false,
    val maintenance: Boolean = false,
    val announcement: String = "",
    val announcementActive: Boolean = false,
    val welcomeMessage: String = "خوش آمدید!",
    val subscriptionsEnabled: Boolean = false,
    @SerializedName("featuredMovies") val featuredMovies: List<String> = emptyList()
)

// ---------- فیلم‌ها ----------
data class DownloadLink(
    val label: String = "",
    val url: String = "",
    val kind: String = ""
)

data class Movie(
    val slug: String = "",
    val title: String = "",
    @SerializedName("title_en") val titleEn: String = "",
    @SerializedName("title_fa") val titleFa: String = "",
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val country: String = "",
    val language: String = "",
    @SerializedName("duration_min") val durationMin: Int? = null,
    @SerializedName("age_rating") val ageRating: String = "",
    @SerializedName("imdb_rating") val imdbRating: Double? = null,
    @SerializedName("imdb_id") val imdbId: String = "",
    val satisfaction: Int? = null,
    val views: Int = 0,
    val description: String = "",
    @SerializedName("cover_url") val coverUrl: String = "",
    @SerializedName("source_url") val sourceUrl: String = "",
    @SerializedName("download_links") val downloadLinks: List<DownloadLink> = emptyList()
) {
    val displayTitle: String get() = titleFa.ifBlank { title }
    val displayYear: String get() = year?.let { it.toString() } ?: ""
}

data class MoviesResponse(
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
    val movies: List<Movie> = emptyList()
)

data class FeaturedResponse(val featured: Boolean = false, val movies: List<Movie> = emptyList())

/** جزئیات فیلم — بک‌اند: { movie: {...} } */
data class MovieResponse(val movie: Movie? = null)

data class Genre(val name: String = "", val count: Int = 0)
data class GenresResponse(val genres: List<Genre> = emptyList())

// ---------- پلن‌ها و اشتراک ----------
data class Plan(
    val id: String = "",
    val name: String = "",
    @SerializedName("price_toman") val priceToman: Long = 0,
    @SerializedName("discount_percent") val discountPercent: Int = 0,
    @SerializedName("final_price_toman") val finalPriceToman: Long = 0,
    @SerializedName("users_per_room") val usersPerRoom: Int = 0,
    @SerializedName("duration_days") val durationDays: Int = 0,
    val features: List<String> = emptyList(),
    @SerializedName("is_popular") val isPopular: Boolean = false
)

data class PlansResponse(val enabled: Boolean = false, val plans: List<Plan> = emptyList())

/** بک‌اند: { subscription: { ends_at, plan_id } | null } */
data class SubscriptionInfo(
    @SerializedName("ends_at") val endsAt: Long = 0,
    @SerializedName("plan_id") val planId: String = ""
)

data class SubscriptionResponse(val subscription: SubscriptionInfo? = null)

data class CheckoutRequest(@SerializedName("plan_id") val planId: String = "")

/** بک‌اند: { redirectUrl, sandbox?, amount_toman } */
data class CheckoutResponse(
    @SerializedName("redirectUrl") val redirectUrl: String = "",
    val sandbox: Boolean = false,
    @SerializedName("amount_toman") val amountToman: Long = 0
)

// ---------- اتاق‌ها ----------
data class CreateRoomRequest(
    val name: String = "اتاق من",
    val videoUrl: String = "",
    val password: String = "",
    val avatar: String = ""
)

/** بک‌اند: { id, name, videoUrl, hasPassword, inviteUrl } */
data class RoomInfo(
    val id: String = "",
    val name: String = "",
    val videoUrl: String = "",
    val hasPassword: Boolean = false,
    val inviteUrl: String = ""
)

// ---------- گزارش ----------
data class ReportRequest(
    @SerializedName("roomId") val roomId: String = "",
    @SerializedName("msgId") val msgId: String = "",
    @SerializedName("msgText") val msgText: String = "",
    val reason: String = ""
)

// ---------- تیکت پشتیبانی ----------
data class Ticket(
    val id: String = "",
    val subject: String = "",
    val status: String = "open",
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("updated_at") val updatedAt: Long = 0
)

data class TicketsResponse(val tickets: List<Ticket> = emptyList())

/** پیام تیکت — بک‌اند: { id, sender(user|admin|bot), author, text, ts } */
data class TicketMsg(
    val id: String = "",
    val sender: String = "user",
    val author: String = "",
    val text: String = "",
    val ts: Long = 0
) {
    val isMine: Boolean get() = sender == "user"
    val isBot: Boolean get() = sender == "bot"
}

data class TicketDetail(
    val id: String = "",
    val subject: String = "",
    val status: String = "open",
    val messages: List<TicketMsg> = emptyList()
)

data class TicketMessagesResponse(val messages: List<TicketMsg> = emptyList())

/** بک‌اند ساخت تیکت: { subject, message } → { ok, id } */
data class CreateTicketRequest(
    val subject: String,
    val message: String
)

data class TicketCreateResponse(val ok: Boolean = false, val id: String = "")

/** بک‌اند پاسخ: POST /api/support/tickets/{id}/messages با { text } */
data class ReplyTicketRequest(val text: String)

package com.hamfilm.app.data.model

import com.google.gson.annotations.SerializedName

// ---------- احراز هویت ----------
data class User(
    val id: String = "",
    val name: String = "",
    val email: String? = null,
    val avatar: String = "🎬",
    @SerializedName("created_at") val createdAt: Long = 0L
)

data class AuthResponse(
    val token: String = "",
    val user: User? = null
)

data class RegisterRequest(val name: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)

// ---------- تنظیمات عمومی ----------
data class PublicSettings(
    val registrationRequired: Boolean = false,
    val maintenance: Boolean = false,
    val announcement: String = "",
    val announcementActive: Boolean = false,
    val welcomeMessage: String = "خوش آمدید!",
    val subscriptionsEnabled: Boolean = false
)

// ---------- فیلم‌ها ----------
data class Movie(
    val slug: String = "",
    val title: String = "",
    @SerializedName("title_en") val titleEn: String = "",
    @SerializedName("title_fa") val titleFa: String = "",
    val year: Int = 0,
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
    val featured: Boolean = false
) {
    val displayTitle: String get() = titleFa.ifBlank { title }
}

data class MoviesResponse(val movies: List<Movie> = emptyList())
data class FeaturedResponse(val featured: Boolean = false, val movies: List<Movie> = emptyList())
data class Genre(val name: String = "", val count: Int = 0)
data class GenresResponse(val genres: List<Genre> = emptyList())

// ---------- پلن‌ها ----------
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
data class SubscriptionStatus(
    val active: Boolean = false,
    @SerializedName("plan_name") val planName: String = "",
    @SerializedName("expires_at") val expiresAt: Long = 0
)
data class CheckoutRequest(@SerializedName("plan_id") val planId: String = "")
data class CheckoutResponse(
    @SerializedName("payment_url") val paymentUrl: String = "",
    @SerializedName("order_id") val orderId: String = ""
)

// ---------- اتاق‌ها ----------
data class CreateRoomRequest(
    val name: String = "اتاق من",
    val videoUrl: String = "",
    val password: String = "",
    val avatar: String = "🎬"
)

data class RoomInfo(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val password: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val locked: Boolean = false,
    val memberCount: Int = 0,
    val videoUrl: String = "",
    val createdAt: Long = 0
)

// ---------- تیکت پشتیبانی ----------
data class Ticket(
    val id: String = "",
    val subject: String = "",
    val status: String = "open",
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("last_reply_at") val lastReplyAt: Long = 0
)

data class TicketsResponse(val tickets: List<Ticket> = emptyList())

data class TicketReply(
    val id: String = "",
    val author: String = "",
    val body: String = "",
    @SerializedName("created_at") val createdAt: Long = 0
)

data class TicketDetail(
    val id: String = "",
    val subject: String = "",
    val status: String = "open",
    val replies: List<TicketReply> = emptyList()
)

data class CreateTicketRequest(val subject: String, val body: String)
data class ReplyTicketRequest(val body: String)

// ---------- خطا ----------
data class ErrorBody(val error: String? = null)

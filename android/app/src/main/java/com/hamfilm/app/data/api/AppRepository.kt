package com.hamfilm.app.data.api

import com.hamfilm.app.data.model.*
import retrofit2.Response

/** لایه دسترسی داده — همه تماس‌های REST با مدیریت خطاهای فارسی */
class AppRepository(private val api: ApiService = ApiClient.service) {

    private fun err(response: Response<*>, fallback: String): Exception {
        val body = response.errorBody()?.string()
        val msg = try {
            com.google.gson.Gson().fromJson(body, ErrorBody::class.java)?.error
        } catch (_: Exception) { null }
        return Exception(msg ?: fallback)
    }

    suspend fun register(name: String, email: String, password: String): AuthResponse {
        val r = api.register(RegisterRequest(name, email, password))
        if (!r.isSuccessful || r.body() == null) throw err(r, "ثبت‌نام ناموفق بود")
        return r.body()!!
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val r = api.login(LoginRequest(email, password))
        if (!r.isSuccessful || r.body() == null) throw err(r, "ورود ناموفق بود")
        return r.body()!!
    }

    suspend fun me(): AuthResponse? {
        val r = api.me()
        return if (r.isSuccessful) r.body() else null
    }

    suspend fun logout() {
        runCatching { api.logout() }
    }

    suspend fun settings(): PublicSettings {
        val r = api.publicSettings()
        return if (r.isSuccessful) r.body() ?: PublicSettings() else PublicSettings()
    }

    suspend fun featured(): List<Movie> {
        val r = api.featuredMovies()
        return if (r.isSuccessful) r.body()?.movies ?: emptyList() else emptyList()
    }

    suspend fun movies(page: Int, genre: String?, query: String?): List<Movie> {
        val r = api.movies(page, genre, query)
        if (!r.isSuccessful) throw err(r, "دریافت فیلم‌ها ناموفق بود")
        return r.body()?.movies ?: emptyList()
    }

    suspend fun genres(): List<Genre> {
        val r = api.genres()
        return if (r.isSuccessful) r.body()?.genres ?: emptyList() else emptyList()
    }

    suspend fun movie(slug: String): Movie {
        val r = api.movie(slug)
        if (!r.isSuccessful) throw err(r, "دریافت فیلم ناموفق بود")
        return r.body() ?: Movie(slug = slug)
    }

    suspend fun plans(): PlansResponse {
        val r = api.plans()
        return if (r.isSuccessful) r.body() ?: PlansResponse() else PlansResponse()
    }

    suspend fun mySubscription(): SubscriptionStatus? {
        val r = api.mySubscription()
        return if (r.isSuccessful) r.body() else null
    }

    suspend fun checkout(planId: String): CheckoutResponse {
        val r = api.checkout(CheckoutRequest(planId))
        if (!r.isSuccessful) throw err(r, "شروع پرداخت ناموفق بود")
        return r.body() ?: CheckoutResponse()
    }

    suspend fun createRoom(body: CreateRoomRequest): RoomInfo {
        val r = api.createRoom(body)
        if (!r.isSuccessful || r.body() == null) throw err(r, "ساخت اتاق ناموفق بود")
        return r.body()!!
    }

    suspend fun roomInfo(code: String): RoomInfo? {
        val r = api.roomInfo(code)
        return if (r.isSuccessful) r.body() else null
    }

    suspend fun report(code: String, reason: String) {
        runCatching { api.report(mapOf("roomCode" to code, "reason" to reason)) }
    }

    suspend fun tickets(): List<Ticket> {
        val r = api.tickets()
        return if (r.isSuccessful) r.body()?.tickets ?: emptyList() else emptyList()
    }

    suspend fun createTicket(subject: String, body: String): TicketDetail {
        val r = api.createTicket(CreateTicketRequest(subject, body))
        if (!r.isSuccessful) throw err(r, "ارسال تیکت ناموفق بود")
        return r.body() ?: TicketDetail()
    }

    suspend fun ticketDetail(id: String): TicketDetail {
        val r = api.ticketDetail(id)
        if (!r.isSuccessful) throw err(r, "دریافت تیکت ناموفق بود")
        return r.body() ?: TicketDetail()
    }

    suspend fun replyTicket(id: String, body: String): TicketDetail {
        val r = api.replyTicket(id, ReplyTicketRequest(body))
        if (!r.isSuccessful) throw err(r, "ارسال پاسخ ناموفق بود")
        return r.body() ?: TicketDetail()
    }
}

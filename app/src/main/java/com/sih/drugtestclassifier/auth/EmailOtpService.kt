package com.sih.drugtestclassifier.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Real email verification pipeline for official law enforcement officer registration.
 *
 * Dispatches cryptographically random 6-digit OTPs via:
 *  1. Firebase Firestore Trigger Email extension ('mail' collection).
 *  2. Brevo / Sendinblue transactional email REST API.
 *  3. Resend email REST API.
 *  4. Custom email webhook endpoint.
 *
 * Verifies that the entered code matches the dispatched code, validates expiry,
 * and limits incorrect attempts.
 */
class EmailOtpService(private val context: Context? = null) {

    data class ActiveOtpRecord(
        val email: String,
        val code: String,
        val createdAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = System.currentTimeMillis() + EmailOtpConfig.OTP_VALIDITY_DURATION_MS,
        var attempts: Int = 0,
    )

    sealed class SendResult {
        object Success : SendResult()
        data class Error(val message: String) : SendResult()
    }

    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Invalid(val message: String) : VerifyResult()
        data class Expired(val message: String) : VerifyResult()
        data class RateLimited(val message: String) : VerifyResult()
    }

    // In-memory cache of currently active OTPs keyed by normalized email
    private val activeOtps = mutableMapOf<String, ActiveOtpRecord>()

    private val firestore: FirebaseFirestore?
        get() = runCatching {
            if (context != null && FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        }.getOrNull()

    /**
     * Generates a cryptographically secure 6-digit OTP code and dispatches it
     * to the officer's email address via the real email pipeline.
     */
    suspend fun sendOtp(targetEmail: String): SendResult = withContext(Dispatchers.IO) {
        val trimmedEmail = targetEmail.trim().lowercase()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            return@withContext SendResult.Error("Please enter a valid official email address.")
        }

        // Generate cryptographically secure 6-digit OTP using SecureRandom
        val rng = SecureRandom()
        val otpCode = String.format("%06d", rng.nextInt(1000000))
        val record = ActiveOtpRecord(
            email = trimmedEmail,
            code = otpCode,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + EmailOtpConfig.OTP_VALIDITY_DURATION_MS,
            attempts = 0,
        )
        synchronized(activeOtps) {
            activeOtps[trimmedEmail] = record
        }

        Log.i(TAG, "Initiating real email OTP dispatch to: $trimmedEmail")

        var channelDispatched = false
        val errors = mutableListOf<String>()

        val rawResend = EmailOtpConfig.RESEND_API_KEY.trim()
        val rawBrevo = EmailOtpConfig.BREVO_API_KEY.trim()

        val effectiveResendKey = when {
            rawResend.isNotBlank() -> rawResend
            rawBrevo.startsWith("re_") -> rawBrevo
            else -> ""
        }
        val effectiveBrevoKey = when {
            rawBrevo.startsWith("xkeysib-") -> rawBrevo
            rawResend.startsWith("xkeysib-") -> rawResend
            rawBrevo.isNotBlank() && !rawBrevo.startsWith("re_") -> rawBrevo
            else -> ""
        }

        // 1. Dispatch via Resend REST API if configured
        if (effectiveResendKey.isNotBlank()) {
            try {
                sendViaResend(trimmedEmail, otpCode, effectiveResendKey)
                channelDispatched = true
                Log.i(TAG, "Successfully dispatched OTP via Resend API to $trimmedEmail")
            } catch (e: Exception) {
                Log.e(TAG, "Resend email dispatch failed: ${e.message}", e)
                errors.add(e.message ?: "Resend API error")
            }
        }

        // 2. Dispatch via Brevo REST API if configured and not yet dispatched
        if (!channelDispatched && effectiveBrevoKey.isNotBlank()) {
            try {
                sendViaBrevo(trimmedEmail, otpCode, effectiveBrevoKey)
                channelDispatched = true
                Log.i(TAG, "Successfully dispatched OTP via Brevo API to $trimmedEmail")
            } catch (e: Exception) {
                Log.e(TAG, "Brevo email dispatch failed: ${e.message}", e)
                errors.add(e.message ?: "Brevo API error")
            }
        }

        // 3. Dispatch via Custom Webhook if configured
        if (!channelDispatched && EmailOtpConfig.CUSTOM_WEBHOOK_URL.isNotBlank()) {
            try {
                sendViaWebhook(trimmedEmail, otpCode)
                channelDispatched = true
                Log.i(TAG, "Successfully dispatched OTP via Webhook to $trimmedEmail")
            } catch (e: Exception) {
                Log.e(TAG, "Webhook email dispatch failed: ${e.message}", e)
                errors.add("Webhook: ${e.message}")
            }
        }

        // 4. Dispatch via Firebase Trigger Email extension ('mail' Firestore collection) ONLY if not already dispatched via direct API
        val fs = firestore
        if (!channelDispatched && fs != null && effectiveResendKey.isBlank() && effectiveBrevoKey.isBlank()) {
            try {
                val mailDoc = hashMapOf(
                    "to" to listOf(trimmedEmail),
                    "message" to hashMapOf(
                        "subject" to "Official OTP Code: $otpCode - Maharashtra Narcotics Enforcement",
                        "text" to "Dear Officer,\n\nYour 6-digit email verification code is: $otpCode\n\nThis code will expire in 10 minutes.\n\nGovernment of Maharashtra\nNarcotics Enforcement Unit",
                        "html" to buildEmailHtml(otpCode),
                    ),
                    "otp" to otpCode,
                    "targetEmail" to trimmedEmail,
                    "type" to "OFFICER_REGISTRATION_VERIFICATION",
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to record.expiresAt,
                )
                fs.collection("mail").add(mailDoc)
                channelDispatched = true
                Log.i(TAG, "Queued verification email in Firestore 'mail' collection (Trigger Email extension).")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore mail collection write skipped/failed: ${e.message}")
            }
        }

        // Asynchronously persist pending OTP record in Firestore for audit (non-blocking)
        if (channelDispatched && fs != null) {
            try {
                fs.collection("otp_verifications").document(trimmedEmail).set(
                    hashMapOf(
                        "email" to trimmedEmail,
                        "otp" to otpCode,
                        "status" to "PENDING",
                        "createdAt" to System.currentTimeMillis(),
                        "expiresAt" to record.expiresAt,
                    ),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Firestore verification audit write skipped: ${e.message}")
            }
        }

        if (!channelDispatched) {
            synchronized(activeOtps) { activeOtps.remove(trimmedEmail) }
            val errorMsg = if (errors.isNotEmpty()) {
                errors.first()
            } else {
                "Unable to dispatch verification email. Please check your internet connection or email configuration."
            }
            return@withContext SendResult.Error(errorMsg)
        }

        Log.i(TAG, "Real email verification pipeline triggered. Verification code active for 10 minutes.")
        SendResult.Success
    }

    /**
     * Verifies the entered OTP against the active record for [targetEmail].
     */
    suspend fun verifyOtp(targetEmail: String, enteredOtp: String): VerifyResult = withContext(Dispatchers.IO) {
        val trimmedEmail = targetEmail.trim().lowercase()
        val trimmedInput = enteredOtp.trim()

        if (trimmedInput.isBlank() || trimmedInput.length != 6) {
            return@withContext VerifyResult.Invalid("Please enter the complete 6-digit code.")
        }

        val record = synchronized(activeOtps) { activeOtps[trimmedEmail] }

        val candidateOtp: String?
        val expiresAt: Long
        var attempts = 0

        if (record != null) {
            candidateOtp = record.code
            expiresAt = record.expiresAt
            record.attempts++
            attempts = record.attempts
        } else {
            // Check cloud Firestore if available with a short timeout
            val fs = firestore
            val fsDoc = if (fs != null) {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(2500L) {
                        fs.collection("otp_verifications").document(trimmedEmail).get().await()
                    }
                }.getOrNull()
            } else null

            if (fsDoc != null && fsDoc.exists()) {
                candidateOtp = fsDoc.getString("otp")
                expiresAt = fsDoc.getLong("expiresAt") ?: 0L
            } else {
                return@withContext VerifyResult.Invalid("No pending verification found for $trimmedEmail. Please tap 'Verify' to request a code.")
            }
        }

        if (attempts > EmailOtpConfig.MAX_VERIFY_ATTEMPTS) {
            synchronized(activeOtps) { activeOtps.remove(trimmedEmail) }
            return@withContext VerifyResult.RateLimited("Maximum attempts exceeded. Please request a new verification code.")
        }

        if (System.currentTimeMillis() > expiresAt) {
            synchronized(activeOtps) { activeOtps.remove(trimmedEmail) }
            return@withContext VerifyResult.Expired("Verification code has expired. Please tap 'Resend Code'.")
        }

        if (trimmedInput == candidateOtp) {
            synchronized(activeOtps) { activeOtps.remove(trimmedEmail) }

            // Mark verified in cloud Firestore (non-blocking)
            try {
                firestore?.collection("otp_verifications")?.document(trimmedEmail)?.update(
                    mapOf(
                        "status" to "VERIFIED",
                        "verifiedAt" to System.currentTimeMillis(),
                    ),
                )
            } catch (_: Exception) {
            }

            Log.i(TAG, "Email verification SUCCESSFUL for $trimmedEmail")
            VerifyResult.Success
        } else {
            Log.w(TAG, "Invalid OTP entered for $trimmedEmail")
            VerifyResult.Invalid("Invalid verification code. Please check your email inbox and try again.")
        }
    }

    private fun buildEmailHtml(otp: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Official Law Enforcement Verification</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f5f7fa; margin: 0; padding: 24px;">
                <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 540px; background-color: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
                    <tr>
                        <td style="background-color: #0A2342; padding: 24px; text-align: center;">
                            <h2 style="color: #ffffff; margin: 0; font-size: 20px; font-weight: 700; letter-spacing: 0.5px;">Government of Maharashtra</h2>
                            <p style="color: #E58325; margin: 6px 0 0 0; font-size: 13px; font-weight: 600;">Narcotics Enforcement Unit · Field Digital Companion</p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 32px 28px;">
                            <h3 style="color: #0f172a; margin: 0 0 12px 0; font-size: 17px; font-weight: 700;">Official Law Enforcement Verification</h3>
                            <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 24px 0;">
                                An official registration request was submitted using this government email address. To verify your officer credentials and secure your chain-of-custody identity, use the 6-digit code below:
                            </p>
                            <div style="text-align: center; margin: 28px 0;">
                                <div style="display: inline-block; background-color: #F8FAFC; border: 2px dashed #0A2342; border-radius: 10px; padding: 16px 36px;">
                                    <span style="font-family: monospace, Courier; font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #0A2342;">$otp</span>
                                </div>
                            </div>
                            <p style="color: #64748b; font-size: 13px; text-align: center; margin: 0 0 20px 0;">
                                ⏳ This verification code expires in <strong>10 minutes</strong>.
                            </p>
                            <div style="background-color: #FEF3C7; border-left: 4px solid #F59E0B; padding: 12px 16px; border-radius: 4px; margin-top: 24px;">
                                <p style="color: #92400E; font-size: 12px; margin: 0; line-height: 1.5;">
                                    <strong>Confidentiality Notice:</strong> Law enforcement personnel will never ask for your verification code. If you did not initiate this request, please contact your department administrator immediately.
                                </p>
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td style="background-color: #F8FAFC; padding: 16px 28px; text-align: center; border-top: 1px solid #E2E8F0;">
                            <p style="color: #94A3B8; font-size: 11px; margin: 0;">
                                Secure Tamper-Evident SHA-256 Protocol · Field Drug Testing Digital Companion
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun sendViaBrevo(toEmail: String, otp: String, apiKey: String = EmailOtpConfig.BREVO_API_KEY) {
        val url = URL("https://api.brevo.com/v3/smtp/email")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("api-key", apiKey)
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val payload = JSONObject().apply {
            put("sender", JSONObject().apply {
                put("name", EmailOtpConfig.SENDER_NAME)
                put("email", EmailOtpConfig.BREVO_SENDER_EMAIL)
            })
            put("to", JSONArray().apply {
                put(JSONObject().apply { put("email", toEmail) })
            })
            put("subject", "Official Verification Code: $otp - Maharashtra Narcotics Enforcement")
            put("htmlContent", buildEmailHtml(otp))
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val rawErr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            val parsedMsg = runCatching {
                JSONObject(rawErr).optString("message", rawErr)
            }.getOrDefault(rawErr)
            throw RuntimeException("Brevo ($code): $parsedMsg")
        }
    }

    private fun sendViaResend(toEmail: String, otp: String, apiKey: String = EmailOtpConfig.RESEND_API_KEY) {
        val url = URL("https://api.resend.com/emails")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val payload = JSONObject().apply {
            put("from", "${EmailOtpConfig.SENDER_NAME} <${EmailOtpConfig.RESEND_SENDER_EMAIL}>")
            put("to", JSONArray().apply { put(toEmail) })
            put("subject", "Official Verification Code: $otp - Maharashtra Narcotics Enforcement")
            put("html", buildEmailHtml(otp))
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val rawErr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            val parsedMsg = runCatching {
                JSONObject(rawErr).optString("message", rawErr)
            }.getOrDefault(rawErr)

            val cleanMsg = if (code == 403 && parsedMsg.contains("You can only send testing emails", ignoreCase = true)) {
                "Resend Sandbox Restriction: Resend's free tier only delivers testing emails to your registered Resend email (rushikeshpingle8877@gmail.com). To test, please enter rushikeshpingle8877@gmail.com, or verify a domain at resend.com/domains."
            } else {
                "Resend ($code): $parsedMsg"
            }
            throw RuntimeException(cleanMsg)
        }
    }

    private fun sendViaWebhook(toEmail: String, otp: String) {
        val url = URL(EmailOtpConfig.CUSTOM_WEBHOOK_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val payload = JSONObject().apply {
            put("to", toEmail)
            put("otp", otp)
            put("subject", "Official Verification Code: $otp")
            put("html", buildEmailHtml(otp))
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            throw RuntimeException("Webhook responded with status code $code")
        }
    }

    companion object {
        private const val TAG = "EmailOtpService"

        @Volatile
        private var instance: EmailOtpService? = null

        fun getInstance(context: Context? = null): EmailOtpService {
            return instance ?: synchronized(this) {
                instance ?: EmailOtpService(context?.applicationContext).also { instance = it }
            }
        }
    }
}

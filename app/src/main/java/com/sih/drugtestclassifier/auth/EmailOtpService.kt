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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Real email verification pipeline for official law enforcement officer registration.
 *
 * Dispatches cryptographically random 6-digit OTPs via:
 *  1. Brevo (Sendinblue) transactional email REST API.
 *  2. Custom email webhook endpoint (if configured).
 *  3. Firebase Firestore Trigger Email extension ('mail' collection, if configured).
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

        Log.i(TAG, "==========================================================")
        Log.i(TAG, ">>> OFFICIAL VERIFICATION OTP FOR $trimmedEmail: $otpCode <<<")
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "Initiating real email OTP dispatch to: $trimmedEmail")

        var channelDispatched = false
        val errors = mutableListOf<String>()

        // 1. Dispatch via Gmail SMTP (Direct SMTPS SSL on port 465) if configured
        val effectiveGmailPass = EmailOtpConfig.GMAIL_APP_PASSWORD.trim().replace(" ", "")
        val effectiveGmailSender = EmailOtpConfig.GMAIL_SENDER_EMAIL.trim()
        if (effectiveGmailSender.isNotBlank() && effectiveGmailPass.isNotBlank()) {
            try {
                sendViaGmailSmtp(trimmedEmail, otpCode, effectiveGmailSender, effectiveGmailPass)
                channelDispatched = true
                Log.i(TAG, "Successfully dispatched OTP via Gmail SMTP to $trimmedEmail")
            } catch (e: Exception) {
                Log.e(TAG, "Gmail SMTP email dispatch failed: ${e.message}", e)
                errors.add("Gmail SMTP: ${e.message}")
            }
        }

        val effectiveBrevoKey = EmailOtpConfig.BREVO_API_KEY.trim()

        // 2. Dispatch via Brevo REST API if configured (fallback)
        if (!channelDispatched && effectiveBrevoKey.isNotBlank()) {
            if (effectiveBrevoKey.startsWith("xsmtpsib-")) {
                val msg = "Brevo Error: You entered an SMTP Key ('xsmtpsib-...'). Brevo REST API requires an API Key starting with 'xkeysib-...'. In your Brevo dashboard, go to 'SMTP & API' -> 'API Keys' tab -> 'Generate a new API key'."
                Log.e(TAG, msg)
                errors.add(msg)
            } else {
                try {
                    sendViaBrevo(trimmedEmail, otpCode, effectiveBrevoKey)
                    channelDispatched = true
                    Log.i(TAG, "Successfully dispatched OTP via Brevo API to $trimmedEmail")
                } catch (e: Exception) {
                    Log.e(TAG, "Brevo email dispatch failed: ${e.message}", e)
                    errors.add(e.message ?: "Brevo API error")
                }
            }
        }

        // 2. Dispatch via Custom Webhook if configured
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

        // 3. Dispatch via Firebase Trigger Email extension ('mail' Firestore collection) if Brevo not configured/dispatched
        val fs = firestore
        if (!channelDispatched && fs != null && effectiveBrevoKey.isBlank()) {
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
            Log.e(TAG, "Email OTP dispatch failed for $trimmedEmail: $errorMsg")
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

    private fun buildEmailHtml(otp: String, hasLogo: Boolean = true): String {
        val logoHtml = if (hasLogo) {
            """
            <div style="text-align: center; margin-bottom: 14px;">
                <img src="cid:app_logo" alt="Government Emblem" width="76" height="76" style="display: block; margin: 0 auto; width: 76px; height: 76px; border: 0; outline: none; text-decoration: none;" />
            </div>
            """.trimIndent()
        } else ""

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
                        <td style="background-color: #0A2342; padding: 28px 24px; text-align: center;">
                            $logoHtml
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
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("api-key", apiKey)
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val logoBytes = getLogoBytes()
        val hasLogo = logoBytes != null

        val payload = JSONObject().apply {
            put("sender", JSONObject().apply {
                put("name", EmailOtpConfig.SENDER_NAME)
                put("email", EmailOtpConfig.BREVO_SENDER_EMAIL)
            })
            put("to", JSONArray().apply {
                put(JSONObject().apply { put("email", toEmail) })
            })
            put("subject", "Official Verification Code: $otp - Maharashtra Narcotics Enforcement")
            put("htmlContent", buildEmailHtml(otp, hasLogo))
            if (logoBytes != null) {
                put("inlineImage", JSONArray().apply {
                    put(JSONObject().apply {
                        put("content", toBase64(logoBytes))
                        put("name", "app_logo")
                    })
                })
            }
        }

        conn.outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(payload.toString())
            it.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseBody = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Log.i(TAG, "Brevo email dispatched successfully (HTTP $code): $responseBody")
        } else {
            val rawErr = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $code"
            val parsedMsg = runCatching {
                JSONObject(rawErr).optString("message", rawErr)
            }.getOrDefault(rawErr)
            val friendlyMsg = when {
                code == 401 -> "Brevo (401 Unauthorized): Invalid API key or key type. Make sure you use an API Key ('xkeysib-...') from Brevo -> SMTP & API -> API Keys tab (not an SMTP key)."
                parsedMsg.contains("sender", ignoreCase = true) || parsedMsg.contains("not validated", ignoreCase = true) ->
                    "Brevo Error: Sender '${EmailOtpConfig.BREVO_SENDER_EMAIL}' is not verified. Please verify this email in Brevo under Senders & IP."
                code == 402 || code == 429 || parsedMsg.contains("quota", ignoreCase = true) || parsedMsg.contains("rate", ignoreCase = true) || parsedMsg.contains("limit", ignoreCase = true) ->
                    "Brevo Rate Limit ($code): Brevo throttled rapid email dispatch. Please wait 1-2 minutes before resending."
                else -> "Brevo ($code): $parsedMsg"
            }
            throw RuntimeException(friendlyMsg)
        }
    }

    private fun sendViaWebhook(toEmail: String, otp: String) {
        val url = URL(EmailOtpConfig.CUSTOM_WEBHOOK_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val payload = JSONObject().apply {
            put("to", toEmail)
            put("otp", otp)
            put("subject", "Official Verification Code: $otp")
            put("html", buildEmailHtml(otp))
        }

        conn.outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(payload.toString())
            it.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            throw RuntimeException("Webhook responded with status code $code")
        }
    }

    private fun sendViaGmailSmtp(
        toEmail: String,
        otp: String,
        senderEmail: String = EmailOtpConfig.GMAIL_SENDER_EMAIL.trim(),
        appPassword: String = EmailOtpConfig.GMAIL_APP_PASSWORD.trim().replace(" ", ""),
    ) {
        val socketFactory = SSLSocketFactory.getDefault()
        val socket = (socketFactory.createSocket() as SSLSocket).apply {
            connect(InetSocketAddress("smtp.gmail.com", 465), 15000)
            soTimeout = 15000
            startHandshake()
        }

        try {
            val reader = socket.inputStream.bufferedReader(Charsets.UTF_8)
            val writer = socket.outputStream.bufferedWriter(Charsets.UTF_8)

            fun readResponse(): String {
                var line = reader.readLine() ?: throw IOException("Gmail SMTP connection closed unexpectedly.")
                val sb = StringBuilder(line)
                while (line.length >= 4 && line[3] == '-') {
                    line = reader.readLine() ?: break
                    sb.append("\n").append(line)
                }
                return sb.toString()
            }

            fun exec(command: String, expectedPrefix: String? = null): String {
                writer.write("$command\r\n")
                writer.flush()
                val response = readResponse()
                if (expectedPrefix != null && !response.startsWith(expectedPrefix)) {
                    throw IOException("Gmail SMTP command error ($command): $response")
                }
                return response
            }

            val banner = readResponse()
            if (!banner.startsWith("220")) {
                throw IOException("Unexpected Gmail SMTP greeting: $banner")
            }

            exec("EHLO localhost", "250")
            exec("AUTH LOGIN", "334")

            val userB64 = toBase64(senderEmail)
            val passB64 = toBase64(appPassword)

            exec(userB64, "334")
            val authResp = exec(passB64)
            if (!authResp.startsWith("235")) {
                throw IOException("Gmail SMTP authentication failed: $authResp")
            }

            exec("MAIL FROM:<$senderEmail>", "250")
            exec("RCPT TO:<$toEmail>", "250")
            exec("DATA", "354")

            val subject = "Official Verification Code: $otp - Maharashtra Narcotics Enforcement"
            val logoBytes = getLogoBytes()
            val hasLogo = logoBytes != null
            val htmlContent = buildEmailHtml(otp, hasLogo)
            val base64Html = toBase64(htmlContent)

            val emailData = if (logoBytes != null) {
                val boundary = "----=_Part_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "")}"
                val base64Logo = toBase64(logoBytes)
                buildString {
                    append("From: ${EmailOtpConfig.SENDER_NAME} <$senderEmail>\r\n")
                    append("To: <$toEmail>\r\n")
                    append("Subject: $subject\r\n")
                    append("MIME-Version: 1.0\r\n")
                    append("Content-Type: multipart/related; boundary=\"$boundary\"\r\n")
                    append("\r\n")
                    // Part 1: HTML Body
                    append("--$boundary\r\n")
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("Content-Transfer-Encoding: base64\r\n")
                    append("\r\n")
                    base64Html.chunked(76).forEach { line ->
                        append(line).append("\r\n")
                    }
                    append("\r\n")
                    // Part 2: Inline App Logo (CID: app_logo)
                    append("--$boundary\r\n")
                    append("Content-Type: image/png; name=\"app_logo.png\"\r\n")
                    append("Content-Transfer-Encoding: base64\r\n")
                    append("Content-ID: <app_logo>\r\n")
                    append("Content-Disposition: inline; filename=\"app_logo.png\"\r\n")
                    append("\r\n")
                    base64Logo.chunked(76).forEach { line ->
                        append(line).append("\r\n")
                    }
                    append("\r\n")
                    append("--$boundary--\r\n")
                    append(".\r\n")
                }
            } else {
                buildString {
                    append("From: ${EmailOtpConfig.SENDER_NAME} <$senderEmail>\r\n")
                    append("To: <$toEmail>\r\n")
                    append("Subject: $subject\r\n")
                    append("MIME-Version: 1.0\r\n")
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("Content-Transfer-Encoding: base64\r\n")
                    append("\r\n")
                    base64Html.chunked(76).forEach { line ->
                        append(line).append("\r\n")
                    }
                    append("\r\n.\r\n")
                }
            }

            writer.write(emailData)
            writer.flush()

            val sendResp = readResponse()
            if (!sendResp.startsWith("250")) {
                throw IOException("Failed sending verification message: $sendResp")
            }

            try {
                exec("QUIT", "221")
            } catch (_: Exception) {
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun getLogoBytes(): ByteArray? {
        return runCatching {
            context?.resources?.openRawResource(com.sih.drugtestclassifier.R.drawable.app_logo)?.use {
                it.readBytes()
            }
        }.getOrNull() ?: runCatching {
            val file = java.io.File("app/src/main/res/drawable/app_logo.png")
            if (file.exists()) file.readBytes() else null
        }.getOrNull()
    }

    private fun toBase64(bytes: ByteArray): String {
        return runCatching {
            val clazz = Class.forName("android.util.Base64")
            val method = clazz.getMethod("encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType)
            method.invoke(null, bytes, 2 /* android.util.Base64.NO_WRAP */) as String
        }.getOrElse {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun toBase64(str: String): String = toBase64(str.toByteArray(Charsets.UTF_8))

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

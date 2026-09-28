package com.sih.drugtestclassifier.auth

/**
 * Configuration for the Real Email OTP Verification Pipeline.
 *
 * Supported delivery mechanisms:
 *  1. Brevo (Sendinblue) Direct Transactional Email API (HTTP REST):
 *     Set [BREVO_API_KEY] and [BREVO_SENDER_EMAIL].
 *
 *  2. Custom Webhook / Gateway: Set [CUSTOM_WEBHOOK_URL].
 *
 *  3. Firebase Firestore Trigger Email Extension (Fallback if configured):
 *     Writes directly to the 'mail' Firestore collection with recipient, HTML template, and OTP.
 */
object EmailOtpConfig {

    /**
     * Gmail sender email address configured with an App Password.
     */
    var GMAIL_SENDER_EMAIL: String = "hckr7887@gmail.com"

    /**
     * Google 16-character App Password (e.g. "szmv xtsp gexq zqgc").
     * Generated from Google Account -> Security -> 2-Step Verification -> App Passwords.
     */
    var GMAIL_APP_PASSWORD: String = "szmv xtsp gexq zqgc"

    /**
     * Brevo (Sendinblue) API Key (Fallback).
     * Obtain from: https://app.brevo.com/settings/keys/api
     * NOTE: Must be an API Key (starts with "xkeysib-..."), NOT an SMTP key ("xsmtpsib-...").
     */
    var BREVO_API_KEY: String = "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"

    /**
     * Verified sender email configured in your Brevo account.
     * Must be verified in Brevo (e.g. your Brevo account email).
     */
    var BREVO_SENDER_EMAIL: String = "hckr7887@gmail.com"

    /**
     * Sender display name.
     */
    var SENDER_NAME: String = "Narcotics Enforcement - Digital Field Companion"

    /**
     * Optional custom HTTP Webhook endpoint that accepts JSON { "to": "...", "otp": "...", "subject": "..." }.
     */
    var CUSTOM_WEBHOOK_URL: String = ""

    /**
     * Validity duration of the generated OTP in milliseconds (default: 10 minutes).
     */
    const val OTP_VALIDITY_DURATION_MS: Long = 10 * 60 * 1000L

    /**
     * Maximum failed verification attempts before the OTP is invalidated.
     */
    const val MAX_VERIFY_ATTEMPTS: Int = 5
}

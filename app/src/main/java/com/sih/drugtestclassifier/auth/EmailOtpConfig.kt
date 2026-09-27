package com.sih.drugtestclassifier.auth

/**
 * Configuration for the Real Email OTP Verification Pipeline.
 *
 * Supported delivery mechanisms:
 *  1. Firebase Firestore Trigger Email Extension (Automatic if Firebase extension is installed):
 *     Writes directly to the 'mail' Firestore collection with recipient, HTML template, and OTP.
 *
 *  2. Direct Transactional Email API (HTTP REST):
 *     - Brevo (Sendinblue) REST API: Set [BREVO_API_KEY] and [BREVO_SENDER_EMAIL].
 *     - Resend REST API: Set [RESEND_API_KEY] and [RESEND_SENDER_EMAIL].
 *     - Custom Webhook / SMTP Gateway: Set [CUSTOM_WEBHOOK_URL].
 *
 * When an API key is configured, [EmailOtpService] sends HTTP POST requests directly
 * to the transactional email provider in addition to logging the dispatch in Firestore.
 */
object EmailOtpConfig {

    /**
     * Brevo (Sendinblue) API Key.
     * Obtain from: https://app.brevo.com/settings/keys/api
     * Leave blank if using Resend, Firebase Trigger Email extension or custom webhook.
     */
    var BREVO_API_KEY: String = "xsmtpsib-a296235494dd40578ff78bcc157493fcee0c7db6ca4c908ddacff663a4ef7cad-p9FNduy8CXnhRc04"

    /**
     * Verified sender email configured in your Brevo account.
     */
    var BREVO_SENDER_EMAIL: String = "narcotics-companion@gov.in"

    /**
     * Sender display name.
     */
    var SENDER_NAME: String = "Narcotics Enforcement - Digital Field Companion"

    /**
     * Resend API Key.
     * Obtain from: https://resend.com/api-keys
     */
    var RESEND_API_KEY: String = "re_apJUyvD1_8SPS3qqB2CddS1rYqRHMcPVQ"

    /**
     * Verified sender email for Resend (e.g. "onboarding@resend.dev").
     */
    var RESEND_SENDER_EMAIL: String = "onboarding@resend.dev"

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

package com.sih.drugtestclassifier.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOtpServiceTest {

    @Test
    fun testGmailSmtpConfigured() {
        val email = EmailOtpConfig.GMAIL_SENDER_EMAIL
        val pass = EmailOtpConfig.GMAIL_APP_PASSWORD.replace(" ", "")

        assertEquals("hckr7887@gmail.com", email)
        assertEquals(16, pass.length)
        assertEquals("szmvxtspgexqzqgc", pass)
    }

    @Test
    fun testOtpRecordDefaults() {
        val record = EmailOtpService.ActiveOtpRecord(
            email = "officer@narcotics.gov.in",
            code = "654321",
        )

        assertEquals("officer@narcotics.gov.in", record.email)
        assertEquals("654321", record.code)
        assertEquals(0, record.attempts)
        assertTrue(record.expiresAt > record.createdAt)
        assertEquals(EmailOtpConfig.OTP_VALIDITY_DURATION_MS, record.expiresAt - record.createdAt)
    }
}

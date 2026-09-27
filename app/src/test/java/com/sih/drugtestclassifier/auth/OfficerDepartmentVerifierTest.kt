package com.sih.drugtestclassifier.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formal verification test suite ensuring that:
 * 1. Each officer provides their own ID during registration.
 * 2. Each officer has a strictly unique, different ID within their department.
 * 3. No other officer can be granted the same ID in the same department.
 * 4. Distinct departments can each have their own independent officer IDs.
 * 5. Whitespace and case normalization strictly prevents duplication bypass.
 */
class OfficerDepartmentVerifierTest {

    private val sampleOfficer1 = OfficerDepartmentVerifier.OfficerRecord(
        uid = "uid-officer-1",
        email = "r.sharma@narcotics.gov.in",
        badgeId = "OFFICER-7421",
        name = "Insp. R. Sharma",
        department = "Narcotics Enforcement Unit - Nashik",
    )

    private val sampleOfficer2 = OfficerDepartmentVerifier.OfficerRecord(
        uid = "uid-officer-2",
        email = "v.patil@narcotics.gov.in",
        badgeId = "OFFICER-8842",
        name = "Sub-Insp. V. Patil",
        department = "Narcotics Enforcement Unit - Nashik",
    )

    private val sampleOfficerDifferentDept = OfficerDepartmentVerifier.OfficerRecord(
        uid = "uid-officer-3",
        email = "a.deshmukh@excise.gov.in",
        badgeId = "OFFICER-7421", // Identical ID number, but in Excise Dept
        name = "Insp. A. Deshmukh",
        department = "State Excise Department - Pune",
    )

    @Test
    fun testOfficerProvidesOwnId_canBeGrantedWhenUniqueInDepartment() {
        val existing = listOf(sampleOfficer1)
        val result = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = "Narcotics Enforcement Unit - Nashik",
            candidateBadgeId = "OFFICER-9999", // New unique ID provided by officer
            candidateUid = "uid-new-officer",
        )
        assertTrue(
            "Unique ID provided by officer should be granted",
            result is OfficerDepartmentVerifier.GrantValidationResult.Granted,
        )
    }

    @Test
    fun testNoOtherCanGrantSameIdInSameDepartment_isDenied() {
        val existing = listOf(sampleOfficer1)
        // Candidate attempts to register with sampleOfficer1's ID in the same department
        val result = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = "Narcotics Enforcement Unit - Nashik",
            candidateBadgeId = "OFFICER-7421",
            candidateUid = "uid-attacker-or-duplicate",
        )
        assertTrue(
            "System must DENY granting the same ID to another officer in the same department",
            result is OfficerDepartmentVerifier.GrantValidationResult.Denied,
        )
        val denied = result as OfficerDepartmentVerifier.GrantValidationResult.Denied
        assertTrue(denied.reason.contains("already registered in 'Narcotics Enforcement Unit - Nashik'"))
        assertTrue(denied.reason.contains("Insp. R. Sharma"))
    }

    @Test
    fun testSameIdCanBeGrantedInDifferentDepartment() {
        val existing = listOf(sampleOfficer1)
        // Another officer in a DIFFERENT department provides the same ID number
        val result = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = "State Excise Department - Pune",
            candidateBadgeId = "OFFICER-7421",
            candidateUid = "uid-excise-officer",
        )
        assertTrue(
            "Same ID is permitted in a DIFFERENT department because each department is its own jurisdiction",
            result is OfficerDepartmentVerifier.GrantValidationResult.Granted,
        )
    }

    @Test
    fun testMultipleOfficersInSameDepartmentWithDifferentIds_areGranted() {
        val existing = listOf(sampleOfficer1, sampleOfficer2)
        val result = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = "Narcotics Enforcement Unit - Nashik",
            candidateBadgeId = "OFFICER-3301",
            candidateUid = "uid-officer-4",
        )
        assertTrue(
            "Multiple officers with different IDs in the same department must all be granted",
            result is OfficerDepartmentVerifier.GrantValidationResult.Granted,
        )
    }

    @Test
    fun testCaseInsensitiveAndWhitespaceNormalizedUniqueness() {
        val existing = listOf(sampleOfficer1)
        // Try variations: lowercase, leading/trailing whitespace, extra spaces in department
        val duplicateVariations = listOf(
            Pair("  narcotics   enforcement unit - nashik  ", " officer-7421 "),
            Pair("narcotics enforcement unit - nashik", "OFFICER-7421"),
            Pair("NARCOTICS ENFORCEMENT UNIT - NASHIK", "officer-7421"),
            Pair("Narcotics  Enforcement  Unit - Nashik", " officer - 7421 "),
        )

        for ((dept, badge) in duplicateVariations) {
            val result = OfficerDepartmentVerifier.checkCanGrantOfficerId(
                existingOfficers = existing,
                candidateDept = dept,
                candidateBadgeId = badge,
                candidateUid = "uid-different-officer",
            )
            assertTrue(
                "Variation '$dept' / '$badge' must be detected as duplicate and DENIED",
                result is OfficerDepartmentVerifier.GrantValidationResult.Denied,
            )
        }
    }

    @Test
    fun testBlankBadgeIdOrDepartment_isDenied() {
        val existing = listOf(sampleOfficer1)
        val blankBadge = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = "Narcotics Enforcement Unit - Nashik",
            candidateBadgeId = "   ",
        )
        assertTrue(blankBadge is OfficerDepartmentVerifier.GrantValidationResult.Denied)

        val blankDept = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = "   ",
            candidateBadgeId = "OFFICER-1234",
        )
        assertTrue(blankDept is OfficerDepartmentVerifier.GrantValidationResult.Denied)
    }

    @Test
    fun testOfficerCanUpdateOwnProfileWithoutSelfCollision() {
        val existing = listOf(sampleOfficer1, sampleOfficer2)
        // sampleOfficer1 updates profile with the same badge & department
        val result = OfficerDepartmentVerifier.checkCanGrantOfficerId(
            existingOfficers = existing,
            candidateDept = sampleOfficer1.department,
            candidateBadgeId = sampleOfficer1.badgeId,
            candidateUid = sampleOfficer1.uid, // Same UID
        )
        assertTrue(
            "Officer updating their own profile must NOT trigger a self-collision",
            result is OfficerDepartmentVerifier.GrantValidationResult.Granted,
        )
    }

    @Test
    fun testOfficerVerificationReport_compliantSystem() {
        val officers = listOf(
            sampleOfficer1,
            sampleOfficer2,
            sampleOfficerDifferentDept, // same ID as officer 1, but in a different dept -> fully compliant!
        )
        val report = OfficerDepartmentVerifier.verifyOfficerUniqueness(officers)

        assertTrue("Report must be compliant when all IDs are unique within their departments", report.isCompliant)
        assertEquals(0, report.violations.size)
        assertEquals(3, report.totalOfficers)
        assertEquals(2, report.totalDepartments)
    }

    @Test
    fun testOfficerVerificationReport_detectsDuplicateViolationInDepartment() {
        val duplicateInSameDept = OfficerDepartmentVerifier.OfficerRecord(
            uid = "uid-conflict",
            email = "imposter@narcotics.gov.in",
            badgeId = "OFFICER-7421", // DUPLICATE in same department!
            name = "Fake Officer",
            department = "Narcotics Enforcement Unit - Nashik",
        )
        val officersWithViolation = listOf(
            sampleOfficer1,
            duplicateInSameDept,
            sampleOfficer2,
            sampleOfficerDifferentDept,
        )

        val report = OfficerDepartmentVerifier.verifyOfficerUniqueness(officersWithViolation)

        assertFalse("Report must fail compliance when duplicate ID exists in a department", report.isCompliant)
        assertEquals(1, report.violations.size)
        val violation = report.violations.first()
        assertEquals("Narcotics Enforcement Unit - Nashik", violation.department)
        assertEquals(2, violation.conflictingOfficers.size)
    }

    @Test
    fun testCompositeKeyUniqueness() {
        val key1 = OfficerDepartmentVerifier.compositeKey("Narcotics Enforcement Unit", "OFFICER-7421")
        val key2 = OfficerDepartmentVerifier.compositeKey("narcotics enforcement unit", "officer-7421")
        val key3 = OfficerDepartmentVerifier.compositeKey("State Police", "OFFICER-7421")

        assertEquals(key1, key2)
        assertTrue(key1 != key3)
    }
}

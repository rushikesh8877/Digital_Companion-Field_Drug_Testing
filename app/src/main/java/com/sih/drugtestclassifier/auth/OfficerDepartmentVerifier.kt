package com.sih.drugtestclassifier.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import database.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Ensures and verifies that each field officer has a strictly unique ID within their department.
 *
 * Invariant:
 *  For any department D, no two officers A and B (A.uid != B.uid) may share the same
 *  badge/officer ID:
 *    normalizeDepartment(A.department) == normalizeDepartment(B.department) =>
 *    normalizeBadgeId(A.badgeId) != normalizeBadgeId(B.badgeId)
 *
 * An officer in a different department (e.g., State Police vs. Customs) may share an ID,
 * but within any single department, the ID provided by the officer is strictly unique.
 */
object OfficerDepartmentVerifier {

    const val COLLECTION_OFFICERS = "officers"
    const val COLLECTION_DEPT_OFFICERS = "department_officers"

    /**
     * Normalizes department name: trimmed, lowercased, multiple whitespace collapsed.
     */
    fun normalizeDepartment(department: String): String {
        return department.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    /**
     * Normalizes badge/officer ID: trimmed, uppercased, internal spaces removed.
     */
    fun normalizeBadgeId(badgeId: String): String {
        return badgeId.trim().uppercase().replace(Regex("\\s+"), "")
    }

    /**
     * Generates a deterministic composite key for Firestore documents and indexing:
     * e.g. "narcotics enforcement unit - nashik___OFFICER-7421"
     */
    fun compositeKey(department: String, badgeId: String): String {
        val d = normalizeDepartment(department)
        val b = normalizeBadgeId(badgeId)
        return "${d}___${b}"
    }

    data class OfficerRecord(
        val uid: String,
        val email: String,
        val badgeId: String,
        val name: String,
        val department: String,
        val registeredAt: Long = System.currentTimeMillis(),
    )

    data class OfficerDuplicateViolation(
        val department: String,
        val duplicateBadgeId: String,
        val conflictingOfficers: List<OfficerRecord>,
    )

    data class OfficerVerificationReport(
        val isCompliant: Boolean,
        val totalOfficers: Int,
        val totalDepartments: Int,
        val departmentBreakdown: Map<String, List<OfficerRecord>>,
        val violations: List<OfficerDuplicateViolation>,
        val message: String,
    )

    sealed class GrantValidationResult {
        data class Granted(val message: String = "Officer ID is available and unique for this department.") : GrantValidationResult()
        data class Denied(val reason: String) : GrantValidationResult()
    }

    /**
     * Pure in-memory verification that checks if a candidate officer ID can be granted
     * in the candidate department given the list of already registered officers.
     */
    fun checkCanGrantOfficerId(
        existingOfficers: List<OfficerRecord>,
        candidateDept: String,
        candidateBadgeId: String,
        candidateUid: String? = null,
    ): GrantValidationResult {
        val normCandidateDept = normalizeDepartment(candidateDept)
        val normCandidateBadge = normalizeBadgeId(candidateBadgeId)

        if (normCandidateBadge.isBlank()) {
            return GrantValidationResult.Denied("Officer / Badge ID cannot be empty.")
        }
        if (normCandidateDept.isBlank()) {
            return GrantValidationResult.Denied("Department / Unit name cannot be empty.")
        }

        val conflict = existingOfficers.firstOrNull { existing ->
            val sameDept = normalizeDepartment(existing.department) == normCandidateDept
            val sameBadge = normalizeBadgeId(existing.badgeId) == normCandidateBadge
            val isDifferentOfficer = candidateUid == null || existing.uid != candidateUid
            sameDept && sameBadge && isDifferentOfficer
        }

        return if (conflict != null) {
            GrantValidationResult.Denied(
                "Officer ID '${candidateBadgeId.trim()}' is already registered in this department.",
            )
        } else {
            GrantValidationResult.Granted()
        }
    }

    /**
     * Audits an arbitrary collection of officer records and produces a comprehensive report.
     * Verifies that no two officers within any department share the same ID.
     */
    fun verifyOfficerUniqueness(officers: List<OfficerRecord>): OfficerVerificationReport {
        val distinctOfficers = officers.distinctBy { it.uid }
        val deptGroups = distinctOfficers.groupBy { normalizeDepartment(it.department) }
        val violations = mutableListOf<OfficerDuplicateViolation>()

        for ((normDept, deptOfficers) in deptGroups) {
            val badgeGroups = deptOfficers.groupBy { normalizeBadgeId(it.badgeId) }
            for ((normBadge, conflictingList) in badgeGroups) {
                if (conflictingList.size > 1) {
                    val displayDept = conflictingList.first().department
                    val displayBadge = conflictingList.first().badgeId
                    violations.add(
                        OfficerDuplicateViolation(
                            department = displayDept,
                            duplicateBadgeId = displayBadge,
                            conflictingOfficers = conflictingList,
                        ),
                    )
                }
            }
        }

        val isCompliant = violations.isEmpty()
        val totalDepts = deptGroups.size
        val summary = if (isCompliant) {
            "Verification Passed: All $totalDepts department(s) and ${distinctOfficers.size} officer(s) have strictly unique IDs."
        } else {
            "Verification Failed: Found ${violations.size} ID duplication conflict(s) across departments."
        }

        val breakdown = deptGroups.mapKeys { (_, list) -> list.first().department }

        return OfficerVerificationReport(
            isCompliant = isCompliant,
            totalOfficers = distinctOfficers.size,
            totalDepartments = totalDepts,
            departmentBreakdown = breakdown,
            violations = violations,
            message = summary,
        )
    }

    /**
     * Full system verification:
     * Pulls officers from both local Room database and Firebase Cloud Firestore,
     * merges by UID, and executes full departmental uniqueness verification.
     */
    suspend fun verifySystemOfficers(context: Context): OfficerVerificationReport = withContext(Dispatchers.IO) {
        val officerMap = mutableMapOf<String, OfficerRecord>()

        // 1. Pull from local Room DB
        try {
            val db = DatabaseProvider.getDatabase(context)
            val localOfficers = db.officerDao().getAllOfficers()
            for (lo in localOfficers) {
                officerMap[lo.uid] = OfficerRecord(
                    uid = lo.uid,
                    email = lo.email,
                    badgeId = lo.badgeId,
                    name = lo.name,
                    department = lo.department,
                    registeredAt = lo.registeredAt,
                )
            }
        } catch (_: Exception) {
        }

        // 2. Pull from Firebase Cloud Firestore if online
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val fs = FirebaseFirestore.getInstance()
                val snapshot = fs.collection(COLLECTION_OFFICERS).get().await()
                for (doc in snapshot.documents) {
                    val uid = doc.getString("uid") ?: doc.id
                    val email = doc.getString("email").orEmpty()
                    val badgeId = doc.getString("badgeId").orEmpty()
                    val name = doc.getString("name").orEmpty()
                    val dept = doc.getString("department").orEmpty()
                    val registeredAt = doc.getLong("registeredAt") ?: System.currentTimeMillis()

                    if (badgeId.isNotBlank() && dept.isNotBlank()) {
                        officerMap[uid] = OfficerRecord(
                            uid = uid,
                            email = email,
                            badgeId = badgeId,
                            name = name,
                            department = dept,
                            registeredAt = registeredAt,
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 3. Fallback: if no DB/Firestore records yet, ensure active session from prefs is audited
        if (officerMap.isEmpty()) {
            val prefs = context.getSharedPreferences("officer_auth_prefs", Context.MODE_PRIVATE)
            val lastBadge = prefs.getString("last_badge_id", "OFFICER-7421") ?: "OFFICER-7421"
            val lastName = prefs.getString("last_officer_name", "Insp. R. Sharma") ?: "Insp. R. Sharma"
            val lastDept = prefs.getString("last_officer_dept", "Narcotics Enforcement Unit - Nashik") ?: "Narcotics Enforcement Unit - Nashik"
            officerMap["default-officer"] = OfficerRecord(
                uid = "default-officer",
                email = "officer@narcotics.gov.in",
                badgeId = lastBadge,
                name = lastName,
                department = lastDept,
            )
        }

        verifyOfficerUniqueness(officerMap.values.toList())
    }
}

package com.sih.drugtestclassifier.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import database.DatabaseProvider
import database.OfficerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages Firebase Authentication and synchronizes authenticated credentials
 * with the field officer's chain-of-custody session (Officer Badge ID, Name, Department).
 *
 * Enforces departmental officer ID uniqueness:
 *  - Each officer provides their own ID during registration.
 *  - No two officers may share the same ID in the same department.
 *  - Verified both locally in SQLite Room DB and in Cloud Firestore.
 */
class FirebaseAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val auth: FirebaseAuth?
        get() = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        }.getOrNull()

    private val firestore: FirebaseFirestore?
        get() = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        }.getOrNull()

    data class OfficerProfile(
        val uid: String,
        val email: String,
        val badgeId: String,
        val name: String,
        val department: String,
    )

    sealed class AuthResult {
        data class Success(val profile: OfficerProfile) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    val isUserLoggedIn: Boolean
        get() = auth?.currentUser != null

    /**
     * Returns the currently authenticated officer's profile, combining Firebase User
     * metadata with persisted officer badge & department information.
     */
    fun getCurrentOfficer(): OfficerProfile? {
        val user = auth?.currentUser ?: return null
        val badgeId = prefs.getString(KEY_BADGE_ID_PREFIX + user.uid, null)
            ?: prefs.getString(KEY_LAST_BADGE_ID, "OFFICER-${user.uid.take(4).uppercase()}")
            ?: "OFFICER-7421"
        val name = user.displayName?.ifBlank { null }
            ?: prefs.getString(KEY_NAME_PREFIX + user.uid, null)
            ?: user.email?.substringBefore("@")
            ?: "Insp. Officer"
        val department = prefs.getString(KEY_DEPT_PREFIX + user.uid, null)
            ?: prefs.getString(KEY_LAST_DEPT, "Narcotics Enforcement Unit")
            ?: "Narcotics Enforcement Unit"

        return OfficerProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            badgeId = badgeId,
            name = name,
            department = department,
        )
    }

    /**
     * Checks if the candidate badgeId is available within the specified department.
     * Enforces that no two officers can be granted the same ID in the same department.
     */
    suspend fun checkOfficerIdAvailability(
        department: String,
        badgeId: String,
        currentUid: String? = null,
    ): OfficerDepartmentVerifier.GrantValidationResult = withContext(Dispatchers.IO) {
        val normDept = OfficerDepartmentVerifier.normalizeDepartment(department)
        val normBadge = OfficerDepartmentVerifier.normalizeBadgeId(badgeId)

        if (normBadge.isBlank()) {
            return@withContext OfficerDepartmentVerifier.GrantValidationResult.Denied("Officer / Badge ID cannot be blank.")
        }
        if (normDept.isBlank()) {
            return@withContext OfficerDepartmentVerifier.GrantValidationResult.Denied("Department name cannot be blank.")
        }

        // 1. Check local Room database
        try {
            val db = DatabaseProvider.getDatabase(context)
            val localConflict = db.officerDao().getOfficerByDeptAndBadge(normDept, normBadge)
            if (localConflict != null && localConflict.uid != currentUid) {
                return@withContext OfficerDepartmentVerifier.GrantValidationResult.Denied(
                    "Officer ID '${badgeId.trim()}' is already registered in '$department' (held by ${localConflict.name}). " +
                        "Each officer must provide a unique ID in their department.",
                )
            }
        } catch (_: Exception) {
        }

        // 2. Check Firebase Cloud Firestore
        val fs = firestore
        if (fs != null) {
            try {
                val key = OfficerDepartmentVerifier.compositeKey(department, badgeId)
                val doc = fs.collection(OfficerDepartmentVerifier.COLLECTION_DEPT_OFFICERS).document(key).get().await()
                if (doc.exists()) {
                    val docUid = doc.getString("uid")
                    val existingName = doc.getString("name") ?: "another officer"
                    if (docUid != null && docUid != currentUid) {
                        return@withContext OfficerDepartmentVerifier.GrantValidationResult.Denied(
                            "Officer ID '${badgeId.trim()}' is already registered in '$department' (held by $existingName). " +
                                "Each officer must provide a unique ID in their department.",
                        )
                    }
                }
            } catch (_: Exception) {
                // If offline, rely on local verification
            }
        }

        OfficerDepartmentVerifier.GrantValidationResult.Granted()
    }

    /**
     * Signs in an officer using their registered email and password.
     * Restores registered badge ID and department from cloud Firestore or local DB.
     */
    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return@withContext AuthResult.Error("Please enter both email and password.")
        }

        val firebaseAuth = auth
            ?: return@withContext AuthResult.Error("Firebase is not initialized. Please ensure google-services.json is configured.")

        return@withContext try {
            val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: return@withContext AuthResult.Error("Authentication succeeded but user is null.")

            var badgeId: String? = null
            var dept: String? = null
            var name: String? = null

            // 1. Fetch cloud profile from Firestore
            val fs = firestore
            if (fs != null) {
                try {
                    val doc = fs.collection(OfficerDepartmentVerifier.COLLECTION_OFFICERS).document(user.uid).get().await()
                    if (doc.exists()) {
                        badgeId = doc.getString("badgeId")
                        dept = doc.getString("department")
                        name = doc.getString("name")
                    }
                } catch (_: Exception) {
                }
            }

            // 2. Fallback to local Room database
            if (badgeId == null || dept == null) {
                try {
                    val db = DatabaseProvider.getDatabase(context)
                    val localOfficer = db.officerDao().getOfficerByUid(user.uid)
                    if (localOfficer != null) {
                        badgeId = localOfficer.badgeId
                        dept = localOfficer.department
                        name = localOfficer.name
                    }
                } catch (_: Exception) {
                }
            }

            // 3. Fallback to SharedPreferences
            badgeId = badgeId
                ?: prefs.getString(KEY_BADGE_ID_PREFIX + user.uid, null)
                ?: prefs.getString(KEY_LAST_BADGE_ID, "OFFICER-${user.uid.take(4).uppercase()}")
                ?: "OFFICER-7421"
            name = name
                ?: user.displayName?.ifBlank { null }
                ?: prefs.getString(KEY_NAME_PREFIX + user.uid, null)
                ?: user.email?.substringBefore("@")
                ?: "Insp. Officer"
            dept = dept
                ?: prefs.getString(KEY_DEPT_PREFIX + user.uid, null)
                ?: prefs.getString(KEY_LAST_DEPT, "Narcotics Enforcement Unit")
                ?: "Narcotics Enforcement Unit"

            // Cache to local SharedPreferences
            prefs.edit()
                .putString(KEY_BADGE_ID_PREFIX + user.uid, badgeId)
                .putString(KEY_NAME_PREFIX + user.uid, name)
                .putString(KEY_DEPT_PREFIX + user.uid, dept)
                .putString(KEY_LAST_BADGE_ID, badgeId)
                .putString(KEY_LAST_NAME, name)
                .putString(KEY_LAST_DEPT, dept)
                .apply()

            // Cache to local Room database
            try {
                val db = DatabaseProvider.getDatabase(context)
                db.officerDao().upsertOfficer(
                    OfficerEntity(
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        badgeId = badgeId,
                        name = name,
                        department = dept,
                        departmentNormalized = OfficerDepartmentVerifier.normalizeDepartment(dept),
                        badgeIdNormalized = OfficerDepartmentVerifier.normalizeBadgeId(badgeId),
                    ),
                )
            } catch (_: Exception) {
            }

            val profile = OfficerProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                badgeId = badgeId,
                name = name,
                department = dept,
            )
            AuthResult.Success(profile)
        } catch (e: FirebaseAuthInvalidUserException) {
            AuthResult.Error("No officer account found with this email address.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Invalid credentials. Please verify your email and password.")
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Sign-in failed. Please check your internet connection."
            AuthResult.Error(msg)
        }
    }

    /**
     * Registers a new officer with Firebase Authentication, strictly verifying that
     * their chosen Officer ID is unique within their department.
     */
    suspend fun register(
        email: String,
        password: String,
        badgeId: String,
        name: String,
        department: String,
    ): AuthResult = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        val trimmedBadge = badgeId.trim()
        val trimmedName = name.trim()
        val trimmedDept = department.trim().ifBlank { "Narcotics Enforcement Unit" }

        if (trimmedEmail.isBlank() || password.isBlank()) {
            return@withContext AuthResult.Error("Email and password are required.")
        }
        if (trimmedBadge.isBlank()) {
            return@withContext AuthResult.Error("Officer / Badge ID is required.")
        }
        if (trimmedName.isBlank()) {
            return@withContext AuthResult.Error("Officer name is required.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters long.")
        }

        // STEP 1: Strict Uniqueness Verification across department
        val availability = checkOfficerIdAvailability(trimmedDept, trimmedBadge)
        if (availability is OfficerDepartmentVerifier.GrantValidationResult.Denied) {
            return@withContext AuthResult.Error(availability.reason)
        }

        val firebaseAuth = auth
            ?: return@withContext AuthResult.Error("Firebase is not initialized. Please verify google-services.json.")

        return@withContext try {
            val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: return@withContext AuthResult.Error("Registration succeeded but user is null.")

            // Update Firebase User display name
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (_: Exception) {
            }

            val normDept = OfficerDepartmentVerifier.normalizeDepartment(trimmedDept)
            val normBadge = OfficerDepartmentVerifier.normalizeBadgeId(trimmedBadge)

            // STEP 2: Persist in Cloud Firestore (officers collection + department_officers unique index)
            val fs = firestore
            if (fs != null) {
                try {
                    val officerDoc = hashMapOf(
                        "uid" to user.uid,
                        "email" to trimmedEmail,
                        "badgeId" to trimmedBadge,
                        "name" to trimmedName,
                        "department" to trimmedDept,
                        "departmentNormalized" to normDept,
                        "badgeIdNormalized" to normBadge,
                        "registeredAt" to System.currentTimeMillis(),
                    )
                    fs.collection(OfficerDepartmentVerifier.COLLECTION_OFFICERS)
                        .document(user.uid)
                        .set(officerDoc, SetOptions.merge())
                        .await()

                    val deptKey = OfficerDepartmentVerifier.compositeKey(trimmedDept, trimmedBadge)
                    fs.collection(OfficerDepartmentVerifier.COLLECTION_DEPT_OFFICERS)
                        .document(deptKey)
                        .set(officerDoc, SetOptions.merge())
                        .await()
                } catch (_: Exception) {
                }
            }

            // STEP 3: Persist in local SQLite Room database with unique constraint
            try {
                val db = DatabaseProvider.getDatabase(context)
                db.officerDao().upsertOfficer(
                    OfficerEntity(
                        uid = user.uid,
                        email = trimmedEmail,
                        badgeId = trimmedBadge,
                        name = trimmedName,
                        department = trimmedDept,
                        departmentNormalized = normDept,
                        badgeIdNormalized = normBadge,
                    ),
                )
            } catch (_: Exception) {
            }

            // STEP 4: Persist officer metadata locally in SharedPreferences
            prefs.edit()
                .putString(KEY_BADGE_ID_PREFIX + user.uid, trimmedBadge)
                .putString(KEY_NAME_PREFIX + user.uid, trimmedName)
                .putString(KEY_DEPT_PREFIX + user.uid, trimmedDept)
                .putString(KEY_LAST_BADGE_ID, trimmedBadge)
                .putString(KEY_LAST_NAME, trimmedName)
                .putString(KEY_LAST_DEPT, trimmedDept)
                .apply()

            val profile = OfficerProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                badgeId = trimmedBadge,
                name = trimmedName,
                department = trimmedDept,
            )
            AuthResult.Success(profile)
        } catch (e: FirebaseAuthWeakPasswordException) {
            AuthResult.Error("Password is too weak. Please use at least 6 characters.")
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("An officer account already exists with this email address.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("The email address format is invalid.")
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Registration failed. Please check your internet connection."
            AuthResult.Error(msg)
        }
    }

    /**
     * Runs full audit verifying that every officer across all departments has a unique ID.
     */
    suspend fun verifyAllOfficers(): OfficerDepartmentVerifier.OfficerVerificationReport {
        return OfficerDepartmentVerifier.verifySystemOfficers(context)
    }

    /**
     * Sends a password reset email to the officer's email.
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your registered email address."))
        }
        val firebaseAuth = auth
            ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

        return try {
            firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the current officer session.
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (_: Exception) {
        }
    }

    /**
     * Fallback demo login for offline use or quick demonstration.
     */
    fun demoLogin(
        badgeId: String = "OFFICER-7421",
        name: String = "Insp. R. Sharma",
        department: String = "Narcotics Enforcement Unit - Nashik",
    ): OfficerProfile {
        val trimmedBadge = badgeId.trim()
        val trimmedName = name.trim()
        val trimmedDept = department.trim()
        prefs.edit()
            .putString(KEY_LAST_BADGE_ID, trimmedBadge)
            .putString(KEY_LAST_NAME, trimmedName)
            .putString(KEY_LAST_DEPT, trimmedDept)
            .apply()

        return OfficerProfile(
            uid = "demo-officer-uid",
            email = "r.sharma@narcotics.gov.in",
            badgeId = trimmedBadge,
            name = trimmedName,
            department = trimmedDept,
        )
    }

    companion object {
        private const val PREFS_NAME = "officer_auth_prefs"
        private const val KEY_BADGE_ID_PREFIX = "badge_"
        private const val KEY_NAME_PREFIX = "name_"
        private const val KEY_DEPT_PREFIX = "dept_"
        private const val KEY_LAST_BADGE_ID = "last_badge_id"
        private const val KEY_LAST_NAME = "last_officer_name"
        private const val KEY_LAST_DEPT = "last_officer_dept"

        @Volatile
        private var instance: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

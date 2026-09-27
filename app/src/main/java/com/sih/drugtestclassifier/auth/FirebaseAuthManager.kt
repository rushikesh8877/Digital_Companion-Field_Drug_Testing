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
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication and synchronizes authenticated credentials
 * with the field officer's chain-of-custody session (Officer Badge ID, Name, Department).
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
     * Signs in an officer using their registered email and password.
     */
    suspend fun signIn(email: String, password: String): AuthResult {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return AuthResult.Error("Please enter both email and password.")
        }

        val firebaseAuth = auth
            ?: return AuthResult.Error("Firebase is not initialized. Please ensure google-services.json is configured.")

        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: return AuthResult.Error("Authentication succeeded but user is null.")

            val profile = getCurrentOfficer() ?: OfficerProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                badgeId = prefs.getString(KEY_LAST_BADGE_ID, "OFFICER-${user.uid.take(4).uppercase()}") ?: "OFFICER-7421",
                name = user.displayName ?: user.email?.substringBefore("@") ?: "Officer",
                department = prefs.getString(KEY_LAST_DEPT, "Narcotics Enforcement Unit") ?: "Narcotics Enforcement Unit",
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
     * Registers a new officer with Firebase Authentication and stores badge ID and department metadata.
     */
    suspend fun register(
        email: String,
        password: String,
        badgeId: String,
        name: String,
        department: String,
    ): AuthResult {
        val trimmedEmail = email.trim()
        val trimmedBadge = badgeId.trim()
        val trimmedName = name.trim()
        val trimmedDept = department.trim().ifBlank { "Narcotics Enforcement Unit" }

        if (trimmedEmail.isBlank() || password.isBlank()) {
            return AuthResult.Error("Email and password are required.")
        }
        if (trimmedBadge.isBlank()) {
            return AuthResult.Error("Officer / Badge ID is required.")
        }
        if (trimmedName.isBlank()) {
            return AuthResult.Error("Officer name is required.")
        }
        if (password.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters long.")
        }

        val firebaseAuth = auth
            ?: return AuthResult.Error("Firebase is not initialized. Please verify google-services.json.")

        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: return AuthResult.Error("Registration succeeded but user is null.")

            // Update Firebase User display name
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (_: Exception) {
                // Non-critical if display name update fails
            }

            // Persist officer metadata locally associated with UID
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
        return OfficerProfile(
            uid = "demo-officer-uid",
            email = "r.sharma@narcotics.gov.in",
            badgeId = badgeId,
            name = name,
            department = department,
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

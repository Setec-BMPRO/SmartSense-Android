package com.smartsense.app.domain.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            // Firebase creates the user and signs them in automatically
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Sign-up failed: User is null")
            Result.success(user)
        } catch (e: Exception) {
            // Common errors: "Email already in use", "Weak password"
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("User not found")
            Result.success(user) // Returns the actual user object
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
        return try {
            // This is the Firebase call that actually changes the password in the cloud
            firebaseAuth.confirmPasswordReset(code, newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser ?: throw Exception("No authenticated user found.")

            try {
                user.delete().await()
                Result.success(Unit)
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                // This is where you trigger a UI event to ask the user to re-login
                Result.failure(Exception("Please re-authenticate to delete your account."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override fun getCurrentUser() = firebaseAuth.currentUser
}
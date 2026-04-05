package com.smartsense.app.domain.firebase

import com.google.firebase.auth.FirebaseUser

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<FirebaseUser>
    suspend fun signIn(email: String, password: String): Result<FirebaseUser>
    fun getCurrentUser(): FirebaseUser?

    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    fun signOut()
}

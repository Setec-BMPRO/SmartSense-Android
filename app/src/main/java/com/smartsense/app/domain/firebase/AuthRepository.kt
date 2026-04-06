package com.smartsense.app.domain.firebase

import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<FirebaseUser>
    suspend fun signIn(email: String, password: String): Result<FirebaseUser>
    fun getCurrentUser(): FirebaseUser?

    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    fun signOut()
    /**
     * Streams the sensors currently stored in the user's Firestore account.
     * Use this to merge with your local Room database.
     */
    fun getRemoteSensorsFlow(): Flow<List<SensorEntity>>
}

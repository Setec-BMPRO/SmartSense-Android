package com.smartsense.app.domain.firebase

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.domain.network.NetworkConnectivityObserver
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val connectivityObserver:NetworkConnectivityObserver
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    override suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        Timber.tag(TAG).i("📧 Starting sign-up for: $email")
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Sign-up failed: User is null")
            Timber.tag(TAG).d("✅ Sign-up successful. UID: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Sign-up failed for $email")
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        Timber.tag(TAG).i("🔑 Attempting sign-in for: $email")
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("User not found")
            Timber.tag(TAG).d("✅ Sign-in successful. UID: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Sign-in failed for $email")
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        Timber.tag(TAG).i("📨 Sending password reset email to: $email")
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Timber.tag(TAG).d("✅ Reset email sent.")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to send reset email to $email")
            Result.failure(e)
        }
    }

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
        Timber.tag(TAG).i("🔄 Confirming password reset with code.")
        return try {
            firebaseAuth.confirmPasswordReset(code, newPassword).await()
            Timber.tag(TAG).d("✅ Password reset confirmed.")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Password reset confirmation failed.")
            Result.failure(e)
        }
    }

    /**
     * Deletes a sensor from both the user's registry and the tank's registry.
     * Uses a Batch to ensure "all-or-nothing" execution.
     */
    override suspend fun deleteSensor(address: String): Result<Unit> {
        val userId = firebaseAuth.currentUser?.uid
        Timber.tag(TAG).i("🗑️ deleteSensor: Requesting deletion of $address (UID: $userId)")

        return try {
            if (userId == null) throw Exception("User not authenticated")

            val batch = firestore.batch()

            val userSensorRef = firestore.collection("users")
                .document(userId)
                .collection("sensors")
                .document(address)

            val tankSensorRef = firestore.collection("users")
                .document(userId)
                .collection("tanks")
                .document(address)

            Timber.tag(TAG).v("⏳ deleteSensor: Adding batch delete for paths: sensors/$address and tanks/$address")
            batch.delete(userSensorRef)
            batch.delete(tankSensorRef)

            batch.commit().await()
            Timber.tag(TAG).d("✅ deleteSensor: Batch commit successful for $address")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ deleteSensor: Failed to delete cloud records for $address")
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val user = firebaseAuth.currentUser
        Timber.tag(TAG).w("🧨 deleteAccount: Requested for UID: ${user?.uid}")

        return try {
            if (user == null) throw Exception("No authenticated user found.")
            user.delete().await()
            Timber.tag(TAG).i("✅ deleteAccount: Account successfully deleted from Firebase Auth.")
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Timber.tag(TAG).w("⚠️ deleteAccount: Recent login required. Exception triggered.")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ deleteAccount: Error deleting user account.")
            Result.failure(e)
        }
    }

    override fun signOut() {
        val uid = firebaseAuth.currentUser?.uid
        Timber.tag(TAG).i("🚪 Signing out user: $uid")
        firebaseAuth.signOut()
    }

    override fun getCurrentUser() = firebaseAuth.currentUser

    override fun getRemoteSensorsFlow(): Flow<List<SensorEntity>>  {
        // 1. Observe the Network Status first
        return connectivityObserver.status.flatMapLatest { status ->
            if (status == NetworkConnectivityObserver.Status.Lost) {
                Timber.tag(TAG).w("📡 Network Lost: Returning empty cloud list.")
                flowOf(emptyList())
            } else {
                // 2. Network is Available, start the Firestore Listener
                createFirestoreSnapshotFlow()
            }
        }
    }

    private fun createFirestoreSnapshotFlow(): Flow<List<SensorEntity>> = callbackFlow {
        val userId = firebaseAuth.currentUser?.uid

        if (userId == null) {
            Timber.tag(TAG).w("📥 No user UID, closing flow.")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val subscription = firestore.collection("users/$userId/sensors")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // If it's a permission error (e.g. account deleted), cancel
                    Timber.tag(TAG).e(error, "🔥 Firestore Snapshot error")
                    cancel("Firestore error", error)
                    return@addSnapshotListener
                }

                val sensors = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SensorEntity::class.java)
                } ?: emptyList()

                trySend(sensors)
            }

        awaitClose {
            Timber.tag(TAG).d("📥 Closing Firestore snapshot listener.")
            subscription.remove()
        }
    }


    override suspend fun reauthenticateAndDelete(credential: AuthCredential): Result<Unit> {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: return Result.failure(Exception("No user found"))

            // 1. Re-authenticate in the background
            user.reauthenticate(credential).await()
            Timber.d("🔐 Background: Re-authentication successful.")

            // 2. Delete Account in the background
            user.delete().await()
            Timber.i("🗑️ Background: Firebase account deleted successfully.")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "❌ Background: Force delete failed")
            Result.failure(e)
        }
    }
}
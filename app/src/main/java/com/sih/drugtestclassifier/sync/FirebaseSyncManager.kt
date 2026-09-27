package com.sih.drugtestclassifier.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sih.drugtestclassifier.models.DigitalTestRecord
import database.DatabaseProvider
import database.TestRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages the offline-first synchronization pipeline between the local Room database
 * and Firebase Cloud Firestore.
 *
 * Rules:
 *  1. When offline, tests are saved exclusively to the local Room database (isSynced = false).
 *  2. As soon as the device connects to the internet (detected automatically via
 *     ConnectivityManager NetworkCallback), all pending offline tests are uploaded to Firestore.
 *  3. Remote tests uploaded by other officers are pulled down from Firestore and merged
 *     into the local database, making all test reports visible to all officers.
 */
class FirebaseSyncManager(private val context: Context) {

    private val dbRepository = TestRepository(DatabaseProvider.getDatabase(context).testDao())
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(isNetworkAvailable())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private var realtimeListener: ListenerRegistration? = null
    private var onDataChangedCallback: (() -> Unit)? = null

    private val firestore: FirebaseFirestore?
        get() = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        }.getOrNull()

    init {
        registerNetworkCallback()
        updatePendingCount()
    }

    fun setDataChangedListener(callback: () -> Unit) {
        this.onDataChangedCallback = callback
    }

    /**
     * Checks whether an active internet connection is currently available.
     */
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    Log.d(TAG, "Network became available. Triggering automatic background sync...")
                    syncScope.launch {
                        triggerSync()
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = isNetworkAvailable()
                    Log.d(TAG, "Network connection lost. Switched to offline mode.")
                }
            },
        )
    }

    /**
     * Updates the number of records waiting to be synced to Firebase.
     */
    fun updatePendingCount() {
        syncScope.launch {
            try {
                val count = dbRepository.getUnsyncedTests().size
                _pendingSyncCount.value = count
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Called whenever a new test report is saved locally.
     * If online, attempts an immediate sync; otherwise queues it for when internet is restored.
     */
    fun onRecordSavedLocally(record: DigitalTestRecord) {
        syncScope.launch {
            updatePendingCount()
            if (isNetworkAvailable()) {
                syncSingleRecord(record)
                triggerSync()
            }
        }
    }

    /**
     * Performs a full bidirectional sync:
     * 1. Pushes all local unsynced reports to Firebase Firestore.
     * 2. Pulls all shared reports from Firestore to local Room database.
     */
    suspend fun triggerSync(): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext false
        if (!isNetworkAvailable()) {
            updatePendingCount()
            return@withContext false
        }

        _isSyncing.value = true
        try {
            // Step 1: Upload unsynced local records
            val unsynced = dbRepository.getUnsyncedTests()
            val fs = firestore
            if (fs != null && unsynced.isNotEmpty()) {
                val currentUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: "offline-officer"
                for (rec in unsynced) {
                    try {
                        val docData = recordToFirestoreMap(rec, currentUid)
                        fs.collection(COLLECTION_RECORDS)
                            .document(rec.testId)
                            .set(docData, SetOptions.merge())
                            .await()

                        dbRepository.markAsSynced(rec.testId)
                        Log.d(TAG, "Successfully synced report ${rec.testId} to Firebase.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to upload record ${rec.testId}: ${e.localizedMessage}")
                    }
                }
            }

            // Step 2: Download cloud reports from all officers
            if (fs != null) {
                try {
                    val snapshot = fs.collection(COLLECTION_RECORDS)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(200)
                        .get()
                        .await()

                    val remoteRecords = snapshot.documents.mapNotNull { doc ->
                        documentToRecord(doc.data ?: return@mapNotNull null)
                    }

                    if (remoteRecords.isNotEmpty()) {
                        // Insert remote records, keeping localImageUri if present locally
                        val localExisting = dbRepository.getAllTests().associateBy { it.testId }
                        val merged = remoteRecords.map { remote ->
                            val local = localExisting[remote.testId]
                            if (local != null && local.localImageUri.isNotBlank()) {
                                remote.copy(localImageUri = local.localImageUri, isSynced = true)
                            } else {
                                remote.copy(isSynced = true)
                            }
                        }
                        dbRepository.insertTests(merged)
                        Log.d(TAG, "Pulled ${remoteRecords.size} cloud reports into local database.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch cloud records: ${e.localizedMessage}")
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            updatePendingCount()
            withContext(Dispatchers.Main) {
                onDataChangedCallback?.invoke()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.localizedMessage}")
            false
        } finally {
            _isSyncing.value = false
            updatePendingCount()
        }
    }

    private suspend fun syncSingleRecord(record: DigitalTestRecord) {
        val fs = firestore ?: return
        try {
            val currentUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: "offline-officer"
            val docData = recordToFirestoreMap(record, currentUid)
            fs.collection(COLLECTION_RECORDS)
                .document(record.testId)
                .set(docData, SetOptions.merge())
                .await()

            dbRepository.markAsSynced(record.testId)
            updatePendingCount()
            Log.d(TAG, "Single record ${record.testId} uploaded immediately.")
        } catch (e: Exception) {
            Log.w(TAG, "Immediate sync failed for ${record.testId} (will sync on network reconnect): ${e.localizedMessage}")
        }
    }

    /**
     * Starts a real-time Firestore listener to receive new test reports uploaded
     * by any officer immediately while the app is active.
     */
    fun startRealtimeSync() {
        stopRealtimeSync()
        val fs = firestore ?: return
        realtimeListener = fs.collection(COLLECTION_RECORDS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                syncScope.launch {
                    val remoteRecords = snapshot.documents.mapNotNull { doc ->
                        documentToRecord(doc.data ?: return@mapNotNull null)
                    }
                    if (remoteRecords.isNotEmpty()) {
                        val localExisting = dbRepository.getAllTests().associateBy { it.testId }
                        val merged = remoteRecords.map { remote ->
                            val local = localExisting[remote.testId]
                            if (local != null && local.localImageUri.isNotBlank()) {
                                remote.copy(localImageUri = local.localImageUri, isSynced = true)
                            } else {
                                remote.copy(isSynced = true)
                            }
                        }
                        dbRepository.insertTests(merged)
                        withContext(Dispatchers.Main) {
                            onDataChangedCallback?.invoke()
                        }
                    }
                }
            }
    }

    fun stopRealtimeSync() {
        realtimeListener?.remove()
        realtimeListener = null
    }

    companion object {
        private const val TAG = "FirebaseSyncManager"
        const val COLLECTION_RECORDS = "field_test_records"

        @Volatile
        private var instance: FirebaseSyncManager? = null

        fun getInstance(context: Context): FirebaseSyncManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseSyncManager(context.applicationContext).also { instance = it }
            }
        }

        fun recordToFirestoreMap(record: DigitalTestRecord, uploadedByUid: String): Map<String, Any> {
            return hashMapOf(
                "testId" to record.testId,
                "operatorId" to record.operatorId,
                "timestamp" to record.timestamp,
                "latitude" to record.latitude,
                "longitude" to record.longitude,
                "result" to record.result,
                "confidence" to record.confidence.toDouble(),
                "imageHash" to record.imageHash,
                "recordHash" to record.recordHash,
                "signature" to record.signature,
                "locationAddress" to record.locationAddress,
                "isSynced" to true,
                "uploadedByUid" to uploadedByUid,
                "syncedAt" to System.currentTimeMillis(),
            )
        }

        fun documentToRecord(data: Map<String, Any>): DigitalTestRecord? {
            return try {
                DigitalTestRecord(
                    testId = data["testId"] as? String ?: return null,
                    operatorId = data["operatorId"] as? String ?: "OFFICER",
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                    result = data["result"] as? String ?: "Inconclusive",
                    confidence = (data["confidence"] as? Number)?.toFloat() ?: 0f,
                    imageHash = data["imageHash"] as? String ?: "",
                    recordHash = data["recordHash"] as? String ?: "",
                    signature = data["signature"] as? String ?: "",
                    locationAddress = data["locationAddress"] as? String ?: "",
                    localImageUri = "",
                    isSynced = true,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

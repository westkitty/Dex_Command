package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AuditEventEntity
import com.example.data.local.DexDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.domain.engine.EvidenceClassifier
import com.example.domain.engine.ProjectStateMachine
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Offline-first WorkManager background job that checks local Room records for:
 * - Projects overdue for review (>7 days)
 * - Inconsistent status or unverified completion records
 */
class ReviewAuditWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = DexDatabase.getInstance(applicationContext)
            val dao = db.dexDao()
            val prefs = UserPreferencesRepository(applicationContext)

            val now = System.currentTimeMillis()
            val projects = dao.getAllProjectsSnapshot()
            val workItems = dao.getAllWorkItemsSnapshot()
            val records = dao.getAllExecutionRecordsSnapshot()

            val needsReviewCount = projects.count { ProjectStateMachine.needsReview(it, now) }
            val inconsistencies = EvidenceClassifier.auditInconsistencies(projects, workItems, records)

            val summary = "Offline audit: ${projects.size} projects scanned · " +
                "$needsReviewCount need review · ${inconsistencies.size} status inconsistency(s)."

            prefs.recordAuditResult(now, summary)
            dao.insertAuditEvent(
                AuditEventEntity(
                    id = "aud-${UUID.randomUUID().toString().take(8)}",
                    entityType = "WORKMANAGER",
                    entityId = "review_audit",
                    action = "AUDIT_COMPLETED",
                    summary = summary,
                    timestamp = now
                )
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_NAME = "dex_command_periodic_review_audit"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReviewAuditWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PERIODIC_NAME)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReviewAuditWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}

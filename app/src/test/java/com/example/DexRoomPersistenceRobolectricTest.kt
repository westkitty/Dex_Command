package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DexDatabase
import com.example.data.local.ProjectEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WorkItemEntity
import com.example.data.repository.DexRepository
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DexRoomPersistenceRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: DexDatabase
    private lateinit var repository: DexRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DexRepository(db.dexDao(), UserPreferencesRepository(context))
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `offline Room persistence survives export-restore cycle and failed restore leaves existing data intact`() = runTest {
        val project = ProjectEntity(
            id = "proj-offline-1",
            name = "Offline Deck Verification",
            purpose = "Verify local durability without internet",
            status = ProjectStatus.ACTIVE,
            priority = 1,
            isDemo = false
        )
        val task = WorkItemEntity(
            id = "wi-offline-1",
            projectId = project.id,
            title = "Validate SQLite commit",
            status = ProjectStatus.ACTIVE,
            evidenceState = EvidenceState.PLANNED
        )

        repository.saveProject(project, isNew = true)
        repository.saveWorkItem(task, isNew = true)

        val backupJson = repository.exportFullBackupJson()

        // Corrupt JSON must NOT wipe or corrupt existing Room records
        val corruptResult = repository.restoreFromBackupJson("{ invalid json")
        assertTrue(corruptResult.isFailure)
        assertEquals(1, db.dexDao().getAllProjectsSnapshot().size)
        assertEquals("Offline Deck Verification", db.dexDao().getAllProjectsSnapshot().first().name)

        // Delete project, then restore from valid backup
        repository.deleteProject(project.id)
        assertEquals(0, db.dexDao().getAllProjectsSnapshot().size)

        val restoreResult = repository.restoreFromBackupJson(backupJson)
        assertTrue(restoreResult.isSuccess)
        val restoredProjects = db.dexDao().getAllProjectsSnapshot()
        assertEquals(1, restoredProjects.size)
        assertEquals("Offline Deck Verification", restoredProjects.first().name)
        assertEquals(1, db.dexDao().getAllWorkItemsSnapshot().size)
    }

    @Test
    fun `demo purge removes only demo records and preserves user records`() = runTest {
        val demoProj = ProjectEntity(id = "demo-1", name = "Demo Proj", isDemo = true)
        val userProj = ProjectEntity(id = "user-1", name = "Real User Proj", isDemo = false)
        repository.saveProject(demoProj, isNew = true)
        repository.saveProject(userProj, isNew = true)

        repository.purgeAllDemoRecords()

        val remaining = db.dexDao().getAllProjectsSnapshot()
        assertEquals(1, remaining.size)
        assertEquals("user-1", remaining.first().id)
    }

    @Test
    fun `app label resource matches Dex Command`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Dex//Command", appName)
    }
}

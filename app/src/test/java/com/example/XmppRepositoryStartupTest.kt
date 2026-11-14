package com.ndregil.inchat

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ndregil.inchat.data.local.AppDatabase
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.repository.XmppRepository
import com.ndregil.inchat.ui.XmppViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XmppRepositoryStartupTest {

    @Test
    fun `initializing XmppRepository repeatedly on IO scope produces no uncaught exceptions`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val caughtErrors = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            caughtErrors.add(throwable)
        }

        try {
            repeat(3) {
                val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                    caughtErrors.add(throwable)
                }
                val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
                val repo = XmppRepository(context, database, ioScope)

                assertEquals(ConnectionStatus.DISCONNECTED, repo.connectionStatus.value)
                assertTrue(repo.getMessagesForRoom("test@conference.example.com").first().isEmpty())
                Thread.sleep(150)

                repo.connectionService.release()
                ioScope.cancel()
                database.close()
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }

        assertTrue(
            "Expected no uncaught startup exceptions, got: ${caughtErrors.map { it.stackTraceToString() }}",
            caughtErrors.isEmpty()
        )
    }

    @Test
    fun `initializing XmppViewModel does not crash flow collectors`() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val caughtErrors = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            caughtErrors.add(throwable)
        }

        try {
            val viewModel = XmppViewModel(application)
            assertTrue(viewModel.activeRoomMessages.first().isEmpty())
            Thread.sleep(150)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }

        assertTrue(
            "Expected no uncaught ViewModel exceptions, got: ${caughtErrors.map { it.stackTraceToString() }}",
            caughtErrors.isEmpty()
        )
    }
}

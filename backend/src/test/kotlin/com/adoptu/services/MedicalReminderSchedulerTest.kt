package com.adoptu.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class MedicalReminderSchedulerTest {

    @Test
    fun `start runs an immediate scan on boot`() = runBlocking {
        val service = mockk<MedicalReminderService>()
        coEvery { service.sendDueReminders() } returns Unit
        val scope = CoroutineScope(Dispatchers.Default)

        MedicalReminderScheduler.start(scope, service)
        // Only the boot-time scan is asserted -- the next one is a day away (delay(1.days)), well
        // past what's worth waiting for in a unit test.
        delay(200)
        scope.coroutineContext[Job]?.cancelAndJoin()

        coVerify(exactly = 1) { service.sendDueReminders() }
    }

    @Test
    fun `an exception from sendDueReminders does not crash the loop`() = runBlocking {
        val service = mockk<MedicalReminderService>()
        coEvery { service.sendDueReminders() } throws RuntimeException("boom")
        val scope = CoroutineScope(Dispatchers.Default)

        MedicalReminderScheduler.start(scope, service)
        delay(200)
        val job = scope.coroutineContext[Job]!!
        job.cancelAndJoin()

        coVerify(atLeast = 1) { service.sendDueReminders() }
        assert(job.isCancelled) { "the loop's job should still be cancellable, not already dead from the exception" }
    }
}

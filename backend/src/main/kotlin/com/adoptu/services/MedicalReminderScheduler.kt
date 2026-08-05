package com.adoptu.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

private val logger = LoggerFactory.getLogger("MedicalReminderScheduler")

// In-process replacement for a cron job - this project has no scheduled-job infra (no Lambda,
// no EventBridge Scheduler), and doesn't need any: the backend already runs continuously on ECS
// Fargate (desired_count = 1, see infra/variables.tf), so a plain daily loop started from main()
// is a real, working scheduler with zero new infrastructure. Runs once immediately on boot (safe
// even across frequent deploys - MedicalReminderService's idempotency guards make repeat scans a
// no-op for already-sent reminders) and every 24h after. Only called from Application.kt's
// main() - never from TestServer.start(), so it never runs during the test suite.
object MedicalReminderScheduler {
    fun start(scope: CoroutineScope, service: MedicalReminderService) {
        scope.launch {
            while (isActive) {
                try {
                    service.sendDueReminders()
                } catch (e: Exception) {
                    logger.error("Medical reminder scan failed", e)
                }
                delay(1.days)
            }
        }
    }
}

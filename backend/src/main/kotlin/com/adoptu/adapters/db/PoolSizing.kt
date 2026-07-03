package com.adoptu.adapters.db

/**
 * Single source of truth for how many DB-bound worker threads/connections this
 * process runs with. Both the Hikari connection pool (DatabaseFactory) and the
 * blocking-work dispatcher (DbDispatcher) must derive their size from this same
 * function — sizing them independently just moves the bottleneck from one to
 * the other instead of removing it.
 *
 * `Runtime.availableProcessors()` can report 1 under a cgroup CPU quota (e.g. ECS
 * Fargate `--cpus=0.5`) even though the container isn't literally single-threaded
 * hardware — see the 2026-06-30 decision log entry in .wolf/cerebrum.md: an
 * *unbounded* dispatcher under that exact constraint regressed throughput from
 * 18.2 RPS to 0.1 RPS (thread-thrashing on a capped half-core), and the fix that
 * was load-tested back to 18.2 RPS was a bound of exactly 4. `cores * 4` with a
 * floor of 4 reproduces that validated number for the documented 1-core/0.5-vCPU
 * case while scaling up on larger boxes. Re-validate with the cgroup-scoped
 * benchmark script (scripts/benchmark.sh) before trusting this on a new profile.
 */
object PoolSizing {
    const val MIN_SIZE = 4
    private const val PER_CORE_MULTIPLIER = 4

    fun computeSize(cores: Int = Runtime.getRuntime().availableProcessors()): Int =
        (cores * PER_CORE_MULTIPLIER).coerceAtLeast(MIN_SIZE)
}

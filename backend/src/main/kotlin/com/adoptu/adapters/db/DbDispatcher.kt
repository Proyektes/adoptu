package com.adoptu.adapters.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared, bounded dispatcher for repository DB calls.
 *
 * This is a genuinely dedicated thread pool, not a `Dispatchers.IO.limitedParallelism(n)`
 * view. `Dispatchers.IO` is backed by its own elastic pool (up to 64 threads); a
 * `limitedParallelism` view caps *concurrent* coroutines but each still borrows a thread
 * from that shared, growing/shrinking backing pool, which is also reused by any other
 * `Dispatchers.IO` work elsewhere in the process (e.g. `SesEmailAdapter`, ad-hoc
 * `CoroutineScope(Dispatchers.IO)` launches in PetService/PhotographerService/
 * TemporalHomeService). A dedicated `Executor` guarantees exactly `PoolSizing.computeSize()`
 * threads exist for DB work and nothing else.
 *
 * IMPORTANT: the previous `Dispatchers.IO.limitedParallelism(4)` was not an arbitrary
 * default — see the 2026-06-30 decision log entry in .wolf/cerebrum.md. An *unbounded*
 * dispatcher, load-tested against the actual container resource cap (`--cpus=0.5`,
 * matching ECS Fargate), regressed throughput from 18.2 RPS to 0.1 RPS. `PoolSizing`
 * reproduces that same validated number (4) for the documented 1-core/0.5-vCPU case, so
 * this change keeps the tested floor while making the pool a real fixed-size executor
 * instead of a view over the shared elastic one. Re-run the cgroup-scoped benchmark
 * (scripts/benchmark.sh) after this change before trusting it in production — a fixed
 * executor is structurally cleaner but hasn't itself been re-measured under the same
 * `--cpus=0.5` constraint that validated the number 4.
 *
 * Size is derived from `PoolSizing`, the same formula used for Hikari's `maximumPoolSize`
 * in DatabaseFactory — a dispatcher pool bigger than the connection pool just means threads
 * queue on `Database.connect`'s pool instead of on this dispatcher, and a dispatcher pool
 * smaller than the connection pool leaves connections idle. Keep both derived from
 * `PoolSizing.computeSize()`; don't hardcode either independently.
 *
 * Defined once and reused across all repository implementations — do not construct a
 * separate executor per call site.
 */
private val dbThreadCounter = AtomicInteger(0)

private val dbExecutor = Executors.newFixedThreadPool(PoolSizing.computeSize()) { runnable ->
    Thread(runnable, "db-worker-${dbThreadCounter.incrementAndGet()}").apply { isDaemon = true }
}

val dbDispatcher: CoroutineDispatcher = dbExecutor.asCoroutineDispatcher()

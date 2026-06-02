package com.aspa.plugin.collector

import com.aspa.plugin.model.ServerMetricsRecord
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

class MetricsCache(private var maxSizeMb: Int) {
    private val cache = ConcurrentSkipListMap<Long, ServerMetricsRecord>()
    private val currentSize = AtomicInteger(0)
    private var maxRecords = calculateMaxRecords(maxSizeMb)

    fun getMaxSizeMb(): Int = maxSizeMb
    fun getMaxRecords(): Int = maxRecords

    private fun calculateMaxRecords(mb: Int): Int {
        // Assume roughly 2 KB per record. 1 MB = 1024 KB.
        // 1024 KB / 2 KB = 512 records per MB.
        return mb * 512
    }

    fun reconfigure(newMaxSizeMb: Int) {
        maxSizeMb = newMaxSizeMb
        maxRecords = calculateMaxRecords(maxSizeMb)
        if (maxRecords <= 0) {
            cache.clear()
            currentSize.set(0)
        } else {
            trim()
        }
    }

    fun add(record: ServerMetricsRecord) {
        if (maxRecords <= 0) return
        val prev = cache.put(record.timestamp, record)
        if (prev == null) {
            currentSize.incrementAndGet()
        }
        trim()
    }

    fun addAll(records: Collection<ServerMetricsRecord>) {
        if (maxRecords <= 0) return
        for (r in records) {
            val prev = cache.put(r.timestamp, r)
            if (prev == null) {
                currentSize.incrementAndGet()
            }
        }
        trim()
    }

    private fun trim() {
        while (currentSize.get() > maxRecords) {
            val evicted = cache.pollFirstEntry()
            if (evicted != null) {
                currentSize.decrementAndGet()
            } else {
                break
            }
        }
    }

    fun getRange(startMs: Long, endMs: Long): List<ServerMetricsRecord>? {
        if (maxRecords <= 0 || cache.isEmpty()) return null

        val firstKey = cache.firstKey()
        // If the requested start time is older than the oldest record in the cache,
        // the cache does not have the complete history.
        if (startMs < firstKey) {
            return null
        }

        return cache.subMap(startMs, true, endMs, true).values.toList()
    }

    fun clear() {
        cache.clear()
        currentSize.set(0)
    }

    fun size(): Int = currentSize.get()
}

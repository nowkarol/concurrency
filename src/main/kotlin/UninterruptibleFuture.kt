package net.karolnowak

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class UninterruptibleFuture<T>: Future<T> {

    @Volatile
    private var result: T? = null

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    override fun isCancelled(): Boolean = false

    override fun isDone(): Boolean = result != null

    override fun get(): T {
        while (true) {
            if (isDone) {
                return result!!
            }
            Thread.yield()
        }
    }

    fun set(result: T) {
        this.result = result
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
       throw IllegalArgumentException("Cannot be interrupted")
    }
}
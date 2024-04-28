package net.karolnowak

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors.callable
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit

/**
 * Disclaimer: I know that all nuances and clever code in FutureTask is because of performance optimization
 * I am (for now) only interested in providing similar capabilities
 * with many not discovered bugs ;)
 */
class SimpleFuture<V>(private val callable: Callable<V>) : RunnableFuture<V> {
    constructor(runnable: Runnable, result: V) : this(callable<V>(runnable, result))

    // cannot check exception and result for not null value because Callable which returns null is valid
    @Volatile
    private var executed: Boolean = false
    @Volatile
    private var canceled: Boolean = false
    @Volatile
    private var result: V? = null
    @Volatile
    private var thrownException: Exception? = null
    @Volatile
    private var runner: Thread? = null

    /**
     * Execution of callable in synchronized block assumes that there won't be many candidates for running
     * otherwise all of them (except first) will be blocked during Callable execution (possibly for long time)
     */
    override fun run() {
        if (runner != null) return // some thread is now executing Callable or has done it already
        synchronized(this) {
            try {
                runner = Thread.currentThread()
                result = callable.call()
            } catch (exception: Exception) {
                thrownException = exception
            } finally {
                executed = true
            }
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (executed) return false
        synchronized(this) {
            canceled = true
            return true
        }
    }

    override fun isCancelled(): Boolean = canceled

    override fun isDone(): Boolean = executed

    // TODO rewrite it using Condition's await and signal
    override fun get(): V? {
        while (true) {
            if (!executed) {
                Thread.yield()
            } else {
                return getResultOrRethrowException()
            }
        }
    }

    private fun getResultOrRethrowException(): V? {
        if (thrownException != null) throw ExecutionException(thrownException)
        return result
    }

    override fun get(timeout: Long, unit: TimeUnit): V {
        TODO("not implemented")
    }
}
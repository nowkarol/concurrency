import net.karolnowak.UninterruptibleFuture
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger


import static java.lang.Thread.sleep
import static java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Actually it is RunnableFutureSpec which adds run() method
 */
abstract class FutureSpec extends Specification {

    int LONG_COMPUTATION_TIME_MS = 1000
    int LESS_THAN_LONG_COMPUTATION_TIME_MS = 500
    int DELAY_TO_MAKE_SURE_THAT_FOLLOWING_CODE_RUNS_FIRST = 1000
    int MAX_CALLABLE_EXEC_TIME_MS = LONG_COMPUTATION_TIME_MS + DELAY_TO_MAKE_SURE_THAT_FOLLOWING_CODE_RUNS_FIRST

    AtomicInteger counter = new AtomicInteger(0)

   abstract <T> Future<T> getFuture(Callable<T> callable)
   abstract <T> Future<T> getFuture(Runnable callable, T result )

    def setup() {
        counter.set(0)
    }

    def "can be created from callable"() {
        given:
            Callable callable = { println("Executing Callable"); "Result" }
            def futureTask = getFuture(callable)
        when:
            futureTask.run() //implements Runnable but not Callable
        then:
            futureTask.get() == "Result"
    }

    def "can be created from callable which returns null"() {
        given:
            Callable<String> callable = { println("Executing Callable"); null }
            def futureTask = getFuture(callable)
        when:
            futureTask.run()
        then:
            futureTask.get() == null
    }

    def "can be crated from runnable and value"() {
        given:
            Runnable runnable = { println("Executing Runnable"); counter.incrementAndGet() }
            def futureTask = getFuture(runnable, "Result independent of Runnable")
        when:
            futureTask.run() //implements Runnable but not Callable
        then:
            futureTask.get() == "Result independent of Runnable"
            waitAndCheckCounterIsEqualTo(1)
    }

    def "will block on get() till end of execution"() {
        given:
            def futureTask = getFuture({
                println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
        then:
            futureTask.get() == "Computationally expensive result"
    }

    def "following get calls will not execute callable again"() {
        given:
            def futureTask = getFuture({
                println("Executing Callable")
                counter.incrementAndGet()
                return "Quick result"
            })
        when:
            executeInSeparateThread(futureTask)
        then:
            futureTask.get() == "Quick result"
            futureTask.get() == "Quick result"
            waitAndCheckCounterIsEqualTo(1)
    }

    def "will run only once even if another threads called run method"() {
        given:
            def futureTask = getFuture({
                println("Executing Callable")
                counter.incrementAndGet()
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
            executeInSeparateThread(futureTask)
            executeInSeparateThread(futureTask)
        then:
            futureTask.get() == "Computationally expensive result"
            waitAndCheckCounterIsEqualTo(1)
    }

    def "nothing will trigger second execution even if first execution threw exception"() {
        given:
            def futureTask = getFuture({
                if (counter.getAndIncrement() == 0) throw new RuntimeException("First run will fail")
                return "Second execution result"
            })
        when:
            executeInSeparateThread(futureTask)
            futureTask.get()
        then:
            thrown(ExecutionException)

        when:
            executeInSeparateThread(futureTask)
            futureTask.get()
        then:
            thrown(ExecutionException)
        // it is not true for runAndReset method but it could be used only by subclasses
    }

    def "will throw exception on get() with timeout if execution takes longer"() {
        given:
            def futureTask = getFuture({
                println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
            futureTask.get(LESS_THAN_LONG_COMPUTATION_TIME_MS, MILLISECONDS)
        then:
            thrown(TimeoutException)
            !futureTask.cancelled
            futureTask.get() == "Computationally expensive result" // despite timeout result still can be obtained
    }

    def "can be cancelled"() {
        given:
            def futureTask = getFuture({
                counter.incrementAndGet()
                println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThreadWithDelay(DELAY_TO_MAKE_SURE_THAT_FOLLOWING_CODE_RUNS_FIRST, futureTask)
            def cancelMethodResult = futureTask.cancel(false)
        then:
            cancelMethodResult
            futureTask.cancelled
            waitAndCheckCounterIsEqualTo(0)

        when:
            futureTask.get()
        then:
            thrown(CancellationException)
    }

    def "can be cancelled during execution"() {
        given:
            def futureTask = getFuture({
                counter.incrementAndGet(); println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                counter.incrementAndGet()
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
            def cancelMethodResult = futureTask.cancel(true)
        then:
            cancelMethodResult
            futureTask.cancelled
            waitAndCheckCounterIsEqualTo(1) // will be interrupted on sleep

        when:
            futureTask.get()
        then:
            thrown(CancellationException)
    }

    def "cannot be cancelled after execution"() {
        given:
            def futureTask = getFuture({
                println("Executing Callable")
                counter.incrementAndGet()
                return "Quick Result"
            })
        when:
            executeInSeparateThread(futureTask)
        then:
            waitAndCheckCounterIsEqualTo(1)
        when:
            def cancelMethodResult = futureTask.cancel(true)
        then:
            !cancelMethodResult
            !futureTask.cancelled
            futureTask.get() == "Quick Result"
    }

    def "cancellation during execution relies on voluntary interrupt flag check"() {
        given:
            def futureTask = getFuture({
                counter.incrementAndGet(); println("Executing Callable")
                blockUninterruptible(LONG_COMPUTATION_TIME_MS)
                counter.incrementAndGet()
                println("This thread interrupted state:" + Thread.interrupted())
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
            def cancelMethodResult = futureTask.cancel(true)
        then:
            cancelMethodResult
            futureTask.cancelled
            waitAndCheckCounterIsEqualTo(2)
            // interrupt() was called but there was no code looking at it like in sleep(), join() or InputStream read()

        when:
            futureTask.get()
        then:
            thrown(CancellationException)
    }

    def "done won't be set until execution ends"() {
        given:
            def futureTask = getFuture({
                println("Executing Callable")
                blockUninterruptible(LONG_COMPUTATION_TIME_MS)
                return "Computationally expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
            def doneFlag = futureTask.isDone()
        then:
            !doneFlag
    }

    def "done will be set if execution ends with result"() {
        given:
            def futureTask = getFuture({ "Quick result" })
        when:
            executeInSeparateThread(futureTask)
            def doneFlag = futureTask.isDone()
        then:
            doneFlag
    }

    def "done will be set if execution ends with exception"() {
        given:
            def exception = new RuntimeException("Task failed")
            def futureTask = getFuture({ throw exception})
        when:
            executeInSeparateThread(futureTask)
            def doneFlag = futureTask.isDone()
        then:
            doneFlag
        when:
            futureTask.get()
        then:
            def thrownException = thrown(ExecutionException)
            thrownException.cause == exception
    }

    void waitAndCheckCounterIsEqualTo(int expectedCounterValue) {
        sleep(MAX_CALLABLE_EXEC_TIME_MS)
        assert counter.get() == expectedCounterValue
    }

    static void executeInSeparateThread(Runnable runnable) {
        new Thread(runnable).start()
        sleep(100) // wait to ensure task execution started
    }

    static void executeInSeparateThreadWithDelay(Long delayInMillis, Runnable runnable) {
        new Thread({
            sleep(delayInMillis)
            runnable.run()
        }).start()
    }

    static void blockUninterruptible(Long blockTimeInMillis) {
        UninterruptibleFuture<String> future = new UninterruptibleFuture<>()
        executeInSeparateThreadWithDelay(blockTimeInMillis, { future.set("ok") })
        future.get()  // interrupt in current thread won't change anything
    }
}

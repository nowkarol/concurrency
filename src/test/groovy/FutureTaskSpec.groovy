import net.karolnowak.UninterruptibleFuture
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger


import static java.lang.Thread.sleep
import static java.util.concurrent.TimeUnit.MILLISECONDS

class FutureTaskSpec extends Specification {
    int LONG_COMPUTATION_TIME_MS = 1000
    int LESS_THAN_LONG_COMPUTATION_TIME_MS = 500
    int DELAY_TO_MAKE_SURE_THAT_FOLLOWING_CODE_RUNS_FIRST = 1000
    int MAX_CALLABLE_EXEC_TIME_MS = LONG_COMPUTATION_TIME_MS + DELAY_TO_MAKE_SURE_THAT_FOLLOWING_CODE_RUNS_FIRST

    AtomicInteger counter = new AtomicInteger(0)

    def setup() {
        counter.set(0)
    }

    def "can be crated from callable"() {
        given:
            Callable callable = { println("Executing Callable"); "Result" }
            def futureTask = new FutureTask(callable)
        when:
            futureTask.run() //implements Runnable but not Callable
        then:
            futureTask.get() == "Result"
    }

    def "can be crated from runnable and value"() {
        given:
            Runnable runnable = { println("Executing Runnable"); counter.incrementAndGet() }
            def futureTask = new FutureTask(runnable, "Result independent of Runnable")
        when:
            futureTask.run() //implements Runnable but not Callable
        then:
            futureTask.get() == "Result independent of Runnable"
            waitAndCheckCounterIsEqualTo(1)
    }

    def "will block on get() till end of execution"() {
        given:
            def futureTask = new FutureTask({
                println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computation expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
        then:
            futureTask.get() == "Computation expensive result"
    }

    def "will throw exception on get() with timeout if execution takes longer"() {
        given:
            def futureTask = new FutureTask({
                println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computation expensive result"
            })
        when:
            executeInSeparateThread(futureTask)
            futureTask.get(LESS_THAN_LONG_COMPUTATION_TIME_MS, MILLISECONDS)
        then:
            thrown(TimeoutException)
            !futureTask.cancelled
    }

    def "can be cancelled"() {
        given:
            def futureTask = new FutureTask({
                counter.incrementAndGet()
                println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                return "Computation expensive result"
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
            def futureTask = new FutureTask({
                counter.incrementAndGet(); println("Executing Callable")
                sleep(LONG_COMPUTATION_TIME_MS)
                counter.incrementAndGet()
                return "Computation expensive result"
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

    def "cancellation during execution relies on voluntary interrupt flag check"() {
        given:
            def futureTask = new FutureTask({
                counter.incrementAndGet(); println("Executing Callable")
                blockUninterruptible(LONG_COMPUTATION_TIME_MS)
                counter.incrementAndGet()
                println("This thread interrupted state:" + Thread.interrupted())
                return "Computation expensive result"
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

    def "show done status"() {

    }

    def "show exceptions"() {

    }

    def "show runAndReset"() {

    }

    void waitAndCheckCounterIsEqualTo(int expectedCounterValue) {
        sleep(MAX_CALLABLE_EXEC_TIME_MS)
        assert counter.get() == expectedCounterValue
    }

    static void executeInSeparateThread(Runnable runnable) {
        new Thread(runnable).start()
        sleep(100) // wait to ensure task execution
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

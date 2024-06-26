import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture

class FutureTaskSpec extends FutureSpec {
    @Override
    <T> RunnableFuture<T> getFuture(Callable<T> callable) {
        return new FutureTask(callable)
    }

    @Override
    <T> RunnableFuture<T> getFuture(Runnable runnable, T result) {
        return new FutureTask(runnable, result)
    }
}

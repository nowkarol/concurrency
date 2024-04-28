import net.karolnowak.SimpleFuture

import java.util.concurrent.Callable
import java.util.concurrent.RunnableFuture

class SimpleFutureSpec extends FutureSpec {
    @Override
    <T> RunnableFuture<T> getFuture(Callable<T> callable) {
        return new SimpleFuture(callable)
    }

    @Override
    <T> RunnableFuture<T> getFuture(Runnable runnable, T result) {
        return new SimpleFuture(runnable, result)
    }
}

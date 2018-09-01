package zone.gryphon.squawk;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface RequestInterceptor<T, R> {

    CompletableFuture<?> intercept(Request<Request<T>> request, Function<Request<?>, CompletableFuture<R>> chain);

}

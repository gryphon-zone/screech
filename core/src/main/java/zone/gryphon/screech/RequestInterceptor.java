package zone.gryphon.screech;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface RequestInterceptor<X, Y, Z> {

    CompletableFuture<Response<Z>> intercept(Request<X> request, Function<Request<?>, CompletableFuture<Response<Y>>> chain);

}

package zone.gryphon.squawk;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AsyncInvocationHandler<T, R> implements InvocationHandler {

    public static <T, R> AsyncInvocationHandler<T, R> from(Method method) {
        return new AsyncInvocationHandler<>(method);
    }

    private final Class<T> returnType;

    private final String httpMethod;

    private final String path;

    private final RequestEncoder encoder = null;

    private final List<RequestInterceptor<?, ?, ?>> requestInterceptors = Collections.emptyList();

    private final ResponseDecoder responseDecoder = null;

    private final ErrorDecoder errorDecoder = null;

    private final Function<Object[], Map<String, Object>> function = (objects) -> Collections.emptyMap();

    private final Client client = null;

    private AsyncInvocationHandler(Method method) {
        RequestLine requestLine = method.getAnnotation(RequestLine.class);

        if (requestLine == null) {
            throw new IllegalArgumentException("Method " + method.toGenericString() + " is not annotated with " + RequestLine.class.getSimpleName());
        }

        String line = requestLine.value();

        String[] parts = line.split(" ", 2);

        this.httpMethod = parts[0].trim().toUpperCase();

        this.path = parts[1].trim();

        this.returnType = (Class<T>) method.getReturnType();
    }

    @Override
    public T invoke(Object proxy, Method method, Object[] args) throws Throwable {
        CompletableFuture<Response<R>> response = setUpInterceptors(0, buildRequest(args));

        CompletableFuture<R> responseUnwrapped = response.thenApply(Response::getEntity);

        if (returnType.isAssignableFrom(CompletableFuture.class) ||returnType.isAssignableFrom(CompletionStage.class)) {
            log.info("yoasync: {}, {}", responseUnwrapped, returnType);
            return (T) responseUnwrapped;
        } else {
            log.info("yo: {}, {}", responseUnwrapped, returnType);
            return (T) responseUnwrapped.get();
        }
    }

    private <X> Request<X> buildRequest(Object[] args) {
        Map<String, Object> parameters = function.apply(args);

        return Request.<X>builder()

                .build();


    }

    private <X, Y, Z> CompletableFuture<Response<Z>> setUpInterceptors(int index, Request<X> request) {

        if (index >= requestInterceptors.size()) {
            return setUpclientCall(request);
        }

        @SuppressWarnings("unchecked")
        RequestInterceptor<X, Y, Z> requestInterceptor = (RequestInterceptor<X, Y, Z>) requestInterceptors.get(index);

        return requestInterceptor.intercept(request, request1 -> setUpInterceptors(index + 1, request1));
    }

    private <R> CompletableFuture<Response<R>> setUpclientCall(Request requestFuture) {
        return CompletableFuture.completedFuture((Response<R>) Response.builder().entity("asdf").build());
    }
}

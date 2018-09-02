package zone.gryphon.squawk;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AsyncInvocationHandler<T, R> implements InvocationHandler {

    public static <T, R> AsyncInvocationHandler<T, R> from(
            Method method,
            RequestEncoder requestEncoder,
            List<RequestInterceptor> requestInterceptors,
            ResponseDecoder responseDecoder,
            ErrorDecoder errorDecoder,
            Client client
            ) {
        return new AsyncInvocationHandler<>(method, requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client);
    }

    private final Class<T> returnType;

    private final String httpMethod;

    private final String path;

    private final Function<Object[], Map<String, Object>> function = (objects) -> Collections.emptyMap();

    // passed in //

    private final RequestEncoder encoder = null;

    private final List<RequestInterceptor<?, ?, ?>> requestInterceptors = Collections.emptyList();

    private final ResponseDecoder responseDecoder = null;

    private final ErrorDecoder errorDecoder = null;

    private final Client client = null;

    private AsyncInvocationHandler(
            Method method,
            RequestEncoder encoder,
            List<RequestInterceptor> requestInterceptors,
            ResponseDecoder responseDecoder,
            ErrorDecoder errorDecoder,
            Client client) {
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

        if (returnType.isAssignableFrom(CompletableFuture.class) || returnType.isAssignableFrom(CompletionStage.class)) {
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
            return setUpClientCall(request);
        }

        @SuppressWarnings("unchecked")
        RequestInterceptor<X, Y, Z> requestInterceptor = (RequestInterceptor<X, Y, Z>) requestInterceptors.get(index);

        return requestInterceptor.intercept(request, request1 -> setUpInterceptors(index + 1, request1));
    }

    private <X> CompletableFuture<Response<X>> setUpClientCall(Request request) {
        return encoder.encode(request.getEntity())
                .thenApply(buffer -> convert(buffer, request))
                .thenCompose(client::request)
                .thenCompose(this::decode);
    }

    private SerializedRequest convert(ByteBuffer buffer, Request<?> request) {
        return null;
    }

    private <X> CompletableFuture<Response<X>> decode(SerializedResponse response) {
        return null;
    }
}

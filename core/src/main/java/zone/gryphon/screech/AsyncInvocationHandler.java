package zone.gryphon.screech;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class AsyncInvocationHandler implements InvocationHandler {

    static AsyncInvocationHandler from(
            Method method,
            RequestEncoder requestEncoder,
            List<RequestInterceptor> requestInterceptors,
            ResponseDecoder responseDecoder,
            ErrorDecoder errorDecoder,
            Client client,
            Target target) {
        return new AsyncInvocationHandler(method, requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
    }

    private static final CompletableFuture<?> NULL_FUTURE = CompletableFuture.completedFuture(null);

    @Getter(AccessLevel.PROTECTED)
    private final Class<?> returnType;

    @Getter(AccessLevel.PROTECTED)
    private final String httpMethod;

    @Getter(AccessLevel.PROTECTED)
    private final String path;

    @Getter(AccessLevel.PROTECTED)
    private final List<HttpParam> queryParams;

    @Getter(AccessLevel.PROTECTED)
    private final List<HttpParam> headerParams;

    private final Function<Object[], Map<String, String>> parameterFunction;

    private final Function<Object[], Object> bodyFunction;

    private final String methodKey;

    // passed in //

    private final RequestEncoder encoder;

    private final List<RequestInterceptor> requestInterceptors;

    private final ResponseDecoder responseDecoder;

    private final ErrorDecoder errorDecoder;

    private final Client client;

    private final Target target;

    private AsyncInvocationHandler(
            @NonNull Method method,
            @NonNull RequestEncoder encoder,
            @NonNull List<RequestInterceptor> requestInterceptors,
            @NonNull ResponseDecoder responseDecoder,
            @NonNull ErrorDecoder errorDecoder,
            @NonNull Client client,
            @NonNull Target target) {

        this.target = target;

        this.encoder = encoder;

        this.requestInterceptors = Collections.unmodifiableList(new ArrayList<>(requestInterceptors));

        this.responseDecoder = responseDecoder;

        this.errorDecoder = errorDecoder;

        this.client = client;

        this.returnType = method.getReturnType();

        this.methodKey = Util.toString(method);

        RequestLine requestLine = method.getAnnotation(RequestLine.class);

        if (requestLine == null) {
            throw new IllegalArgumentException(String.format("Error building client for %s, method is not annotated with %s",
                    methodKey, RequestLine.class.getSimpleName()));
        }

        String[] parts = requestLine.value().split(" ", 2);

        this.httpMethod = parseHttpMethod(parts);

        this.path = parsePath(parts);

        this.queryParams = Collections.unmodifiableList(parseQueryParams(parts));

        this.headerParams = Collections.unmodifiableList(parseHeaderParams(method));

        this.parameterFunction = setupParameterExtractor(method);

        this.bodyFunction = setupBodyFunction(method);

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        CompletableFuture<Response<?>> response = setUpInterceptors(0, buildRequest(args));

        CompletableFuture<?> responseUnwrapped = response.thenApply(Response::getEntity);

        if (returnType.isAssignableFrom(CompletableFuture.class)) {
            return responseUnwrapped;
        } else {
            try {
                return responseUnwrapped.get();
            } catch (Throwable e) {
                throw Util.unwrap(e);
            }
        }
    }

    private Function<Object[], Object> setupBodyFunction(Method method) {

        int parametersWithoutAnnotations = (int) Arrays.stream(method.getParameterAnnotations())
                .mapToInt(a -> a.length)
                .filter(a -> a == 0)
                .count();

        if (parametersWithoutAnnotations > 1) {
            throw new IllegalArgumentException(String.format("Error building client for %s, cannot have more than one body param", methodKey));
        }

        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            final int index = i;
            if (parameters[i].getAnnotations().length == 0) {
                return objects -> objects[index];
            }
        }

        // no body param
        return ignored -> null;
    }

    private List<HttpParam> parseHeaderParams(Method method) {
        Set<String> headersDefinedAtMethodLevel = new HashSet<>();

        List<HttpParam> headers = new ArrayList<>();

        for (Header methodHeader : method.getAnnotationsByType(Header.class)) {
            List<String> parts = Arrays.stream(methodHeader.value().split(":", 2))
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (parts.size() != 2) {
                throw new IllegalArgumentException(String.format("Failed to parse valid header from value \"%s\" on method %s", methodHeader.value(), methodKey));
            }

            headersDefinedAtMethodLevel.add(parts.get(0).toLowerCase());

            headers.add(new HttpParam(parts.get(0), parts.get(1)));
        }

        List<HttpParam> classHeaders = new ArrayList<>();

        for (Header methodHeader : method.getDeclaringClass().getAnnotationsByType(Header.class)) {
            List<String> parts = Arrays.stream(methodHeader.value().split(":", 2))
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (parts.size() != 2) {
                throw new IllegalArgumentException(String.format("Failed to parse valid header from value \"%s\" on method %s", methodHeader.value(), methodKey));
            }

            // ignore headers defined at method level
            if (headersDefinedAtMethodLevel.contains(parts.get(0).toLowerCase())) {
                continue;
            }

            classHeaders.add(new HttpParam(parts.get(0), parts.get(1)));
        }

        headers.addAll(0, classHeaders);


        return headers;
    }

    private String parseHttpMethod(String[] parts) {

        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException(String.format("Error building client for %s, no HTTP method defined", methodKey));
        }

        if (parts[0].contains("/") || parts[0].contains("?") || parts[0].contains("=") || parts[0].contains("&")) {
            throw new IllegalArgumentException(String.format("Error building client for %s, no HTTP method defined", methodKey));
        }

        return parts[0].trim();
    }

    private String parsePath(String[] parts) {

        if (parts.length < 2) {
            throw new IllegalArgumentException(String.format("Error building client for %s, no URL path defined", methodKey));
        }

        String pathAndQueryParams = parts[1].trim();

        int index = pathAndQueryParams.indexOf('?');

        if (index >= 0) {
            return pathAndQueryParams.substring(0, index);
        }

        return pathAndQueryParams;
    }

    private List<HttpParam> parseQueryParams(String[] parts) {

        String pathAndQueryParams = parts[1].trim();

        int index = pathAndQueryParams.indexOf('?');

        if (index == -1) {
            return Collections.emptyList();
        }

        List<HttpParam> output = new ArrayList<>();

        String queryString = pathAndQueryParams.substring(index + 1);

        while ((index = queryString.indexOf('&')) != -1) {
            parseSingleParam(queryString.substring(0, index)).ifPresent(output::add);
            queryString = queryString.substring(index + 1);
        }

        parseSingleParam(queryString).ifPresent(output::add);

        return output;
    }

    private Optional<HttpParam> parseSingleParam(String string) {
        int idx;

        if ((idx = string.indexOf('=')) != -1) {
            String key = string.substring(0, idx);

            if (!key.isEmpty()) {
                return Optional.of(new HttpParam(key, string.substring(idx + 1)));
            }

            return Optional.empty();
        }

        if (!string.isEmpty()) {
            return Optional.of(new HttpParam(string, null));
        }

        return Optional.empty();
    }

    private Function<Object[], Map<String, String>> setupParameterExtractor(Method method) {

        Supplier[] nameSuppliers = new Supplier[method.getParameterCount()];
        Param.Expander[] expanders = new Param.Expander[method.getParameterCount()];

        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        for (int i = 0; i < parameterAnnotations.length; i++) {

            Set<Param> params = Arrays.stream(parameterAnnotations[i])
                    .filter(annotation -> annotation instanceof Param)
                    .map(annotation -> (Param) annotation)
                    .collect(Collectors.toSet());

            if (params.isEmpty()) {
                continue;
            }

            // already make sure the collection wasn't empty, so don't need to do an "isPresent" check.
            // Also, `Param` isn't repeatable, so there should only ever be exactly 1
            Param param = params.stream().findAny().get();

            nameSuppliers[i] = param::value;

            try {
                expanders[i] = param.expander().newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to create expander", e);
            }
        }

        return objects -> {
            Map<String, String> output = new HashMap<>();

            for (int i = 0; i < nameSuppliers.length; i++) {

                if (nameSuppliers[i] == null) {
                    continue;
                }

                output.put((String) nameSuppliers[i].get(), expanders[i].expand(objects[i]));
            }

            return output;
        };
    }

    private <X> Request<X> buildRequest(Object[] args) {
        //noinspection unchecked
        return Request.<X>builder()
                .method(httpMethod)
                .uri(target.getTarget() + this.path)
                .templateParameters(parameterFunction.apply(args))
                .queryParams(this.queryParams)
                .headers(headerParams)
                .entity((X) bodyFunction.apply(args))
                .build();
    }

    private SerializedRequest convertRequestIntoSerializedRequest(ByteBuffer buffer, Request<?> request) {
        RequestBody body;

        if (buffer != null) {
            body = RequestBody.builder()
                    .body(buffer)
                    .contentType(parseContentType(request.getHeaders()))
                    .build();
        } else {
            body = null;
        }

        return SerializedRequest.builder()
                .method(request.getMethod())
                .uri(interpolateUri(request.getUri(), request.getTemplateParameters()))
                .headers(interpolateHttpParams(request.getHeaders(), request.getTemplateParameters()))
                .queryParams(interpolateHttpParams(request.getQueryParams(), request.getTemplateParameters()))
                .requestBody(body)
                .build();
    }

    private URI interpolateUri(String uri, Map<String, String> templateParams) {
        for (Map.Entry<String, String> stringStringEntry : templateParams.entrySet()) {
            uri = uri.replace("{" + stringStringEntry.getKey() + "}", stringStringEntry.getValue());
        }

        return URI.create(uri);
    }

    private List<HttpParam> interpolateHttpParams(List<HttpParam> params, Map<String, String> templateParams) {
        return params.stream()
                .map(param -> interpolateSimgleHttpParam(param, templateParams))
                .collect(Collectors.toList());
    }

    private HttpParam interpolateSimgleHttpParam(HttpParam queryParam, Map<String, String> templateParams) {
        for (Map.Entry<String, String> stringStringEntry : templateParams.entrySet()) {
            String key = '{' + stringStringEntry.getKey() + '}';

            queryParam = HttpParam.builder()
                    .key(queryParam.getKey().replace(key, stringStringEntry.getValue()))
                    .value(queryParam.getValue().replace(key, stringStringEntry.getValue()))
                    .build();
        }

        return queryParam;
    }

    private String parseContentType(List<HttpParam> headers) {
        headers = Optional.ofNullable(headers).orElseGet(Collections::emptyList);

        return headers.stream()
                .filter(header -> "content-type".equalsIgnoreCase(header.getKey()))
                .findAny()
                .map(HttpParam::getValue)
                .orElse("application/octet-stream");
    }

    private <T> CompletableFuture<T> requireNonNull(CompletableFuture<T> future, String type, Object o) {
        return Objects.requireNonNull(future, () -> String.format("%s \"%s\" returned null value instead of CompletableFuture", type, o.getClass().getSimpleName()));
    }

    private CompletableFuture<Response<?>> setUpInterceptors(int index, Request<?> request) {

        if (index >= requestInterceptors.size()) {
            return performClientCall(request);
        }

        RequestInterceptor requestInterceptor = requestInterceptors.get(index);

        //noinspection unchecked
        return requestInterceptor.intercept(request, modifiedRequest -> (CompletableFuture) setUpInterceptors(index + 1, modifiedRequest));
    }

    private CompletableFuture<Response<?>> performClientCall(Request request) {

        CompletableFuture<ByteBuffer> bufferFuture;

        if (request.getEntity() != null) {
            bufferFuture = requireNonNull(encoder.encode(request.getEntity()), "Encoder", encoder);
        } else {
            //noinspection unchecked
            bufferFuture = (CompletableFuture) NULL_FUTURE;
        }

        return decode(bufferFuture
                .thenApply(buffer -> convertRequestIntoSerializedRequest(buffer, request))
                .thenCompose(client::request));
    }

    private CompletableFuture<Response<?>> decode(CompletableFuture<SerializedResponse> clientResponse) {
        CompletableFuture<Response<?>> out = new CompletableFuture<>();

        clientResponse.whenComplete((result, throwable) -> {
            try {
                if (throwable != null) {
                    out.completeExceptionally(throwable);

                } else if (result == null) {
                    String message = String.format("Client \"%s\" returned null SerializedResponse", client.getClass().getSimpleName());
                    out.completeExceptionally(new NullPointerException(message));

                } else if (result.getStatus() >= 300) {
                    handleNonSuccessStatus(out, result);

                } else {
                    handleSuccessStatus(out, result);
                }
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });

        return out;
    }

    private void handleNonSuccessStatus(CompletableFuture<Response<?>> out, SerializedResponse serializedResponse) {
        requireNonNull(errorDecoder.decode(serializedResponse), "Error Decoder", errorDecoder).whenComplete((result, throwable) -> {
            if (throwable != null) {
                out.completeExceptionally(throwable);
            } else if (result != null) {
                out.complete(result);
            } else {
                String message = String.format("Error Decoder \"%s\" returned null response", errorDecoder.getClass().getSimpleName());
                out.completeExceptionally(new NullPointerException(message));
            }
        });
    }

    private void handleSuccessStatus(CompletableFuture<Response<?>> out, SerializedResponse serializedResponse) {
        requireNonNull(responseDecoder.decode(serializedResponse, returnType), "Decoder", responseDecoder).whenComplete((result, throwable) -> {
            if (throwable != null) {
                out.completeExceptionally(throwable);
            } else {
                Response<?> response = Response.builder()
                        .entity(result)
                        .build();

                // noinspection unchecked
                out.complete(response);
            }
        });
    }

}

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
public class AsyncInvocationHandler<R> implements InvocationHandler {

    public static <X> AsyncInvocationHandler<X> from(
            Method method,
            RequestEncoder requestEncoder,
            List<RequestInterceptor<?, ?, ?>> requestInterceptors,
            ResponseDecoder responseDecoder,
            ErrorDecoder errorDecoder,
            Client client,
            Target target) {
        return new AsyncInvocationHandler<>(method, requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
    }

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

    private final List<RequestInterceptor<?, ?, ?>> requestInterceptors;

    private final ResponseDecoder responseDecoder;

    private final ErrorDecoder errorDecoder;

    private final Client client;

    private final Target target;

    private AsyncInvocationHandler(
            @NonNull Method method,
            @NonNull RequestEncoder encoder,
            @NonNull List<RequestInterceptor<?, ?, ?>> requestInterceptors,
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
        CompletableFuture<Response<R>> response = setUpInterceptors(0, buildRequest(args));

        CompletableFuture<R> responseUnwrapped = response.thenApply(Response::getEntity);

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
        return Request.<X>builder()
                .method(httpMethod)
                .uri(target.getTarget() + this.path)
                .templateParameters(parameterFunction.apply(args))
                .queryParams(this.queryParams)
                .headers(headerParams)
                .entity((X) bodyFunction.apply(args))
                .build();
    }

    private <X, Y, Z> CompletableFuture<Response<Z>> setUpInterceptors(int index, Request<X> request) {

        if (index >= requestInterceptors.size()) {
            return performClientCall(request);
        }

        @SuppressWarnings("unchecked")
        RequestInterceptor<X, Y, Z> requestInterceptor = (RequestInterceptor<X, Y, Z>) requestInterceptors.get(index);

        return requestInterceptor.intercept(request, modifiedRequest -> setUpInterceptors(index + 1, modifiedRequest));
    }

    private <X> CompletableFuture<Response<X>> performClientCall(Request request) {

        CompletableFuture<ByteBuffer> bufferFuture;

        if (request.getEntity() != null) {
            bufferFuture = encoder.encode(request.getEntity());
        } else {
            bufferFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture<SerializedResponse> response = bufferFuture
                .thenApply(buffer -> convertRequestIntoSerializedRequest(buffer, request))
                .thenCompose(client::request);

        return decode(response);
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

    private <X> CompletableFuture<Response<X>> decode(CompletableFuture<SerializedResponse> asyncResponse) {
        CompletableFuture<Response<X>> out = new CompletableFuture<>();

        asyncResponse.whenComplete((serializedResponse, throwable) -> {
            try {

                if (throwable != null) {
                    out.completeExceptionally(throwable);
                    return;
                }

                if (serializedResponse == null) {
                    out.completeExceptionally(new NullPointerException(String.format("Client \"%s\" returned null response", client.getClass().getSimpleName())));
                    return;
                }

                if (serializedResponse.getStatus() >= 300) {
                    errorDecoder.decode(serializedResponse).whenComplete((response, exception) -> {
                        if (response != null) {
                            // noinspection unchecked
                            out.complete((Response<X>) response);
                        } else {
                            out.completeExceptionally(exception);
                        }
                    });

                    return;
                }

                Objects.requireNonNull(responseDecoder.decode(serializedResponse, returnType), String.format("Decoder \"%s\" returned null response", responseDecoder.getClass().getSimpleName()))
                        .whenComplete((result, exception) -> {

                            if (exception != null) {
                                out.completeExceptionally(exception);
                                return;
                            }

                            Response<?> response = Response.builder()
                                    .entity(result)
                                    .build();

                            // noinspection unchecked
                            out.complete((Response<X>) response);
                        });
            } catch (Throwable e) {
                out.completeExceptionally(e);
            }
        });

        return out;
    }

    private String parseContentType(List<HttpParam> headers) {
        headers = Optional.ofNullable(headers).orElseGet(Collections::emptyList);

        return headers.stream()
                .filter(header -> "content-type".equalsIgnoreCase(header.getKey()))
                .findAny()
                .map(HttpParam::getValue)
                .orElse("application/octet-stream");
    }

}

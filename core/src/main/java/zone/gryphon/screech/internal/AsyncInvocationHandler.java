/*
 * Copyright 2018-2018 Gryphon Zone
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package zone.gryphon.screech.internal;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.Header;
import zone.gryphon.screech.Param;
import zone.gryphon.screech.RequestEncoder;
import zone.gryphon.screech.RequestInterceptor;
import zone.gryphon.screech.RequestLine;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.Target;
import zone.gryphon.screech.exception.ScreechException;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.Request;
import zone.gryphon.screech.model.RequestBody;
import zone.gryphon.screech.model.Response;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;
import zone.gryphon.screech.util.SimpleStringInterpolator;
import zone.gryphon.screech.util.StringInterpolator;
import zone.gryphon.screech.util.Util;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class AsyncInvocationHandler implements InvocationHandler {

    private static abstract class TransformingCallback<T, R> implements Callback<T> {

        private final Callback<R> proxy;

        public TransformingCallback(Callback<R> proxy) {
            this.proxy = Objects.requireNonNull(proxy, "proxy callback may not be null");
        }

        @Override
        public void onSuccess(T result) {
            proxy.onSuccess(convert(result));
        }

        @Override
        public void onFailure(Throwable e) {
            proxy.onFailure(e);
        }

        protected abstract R convert(T entity);
    }

    private static class ConditionallyProxyingCallabck<T> implements Callback<T> {

        // set to the name of the first method that was called
        private final AtomicReference<String> terminalMethodName = new AtomicReference<>();

        private final Callback<T> proxy;

        private final boolean throwException;

        public ConditionallyProxyingCallabck(Callback<T> proxy, boolean throwException) {
            this.proxy = Objects.requireNonNull(proxy, "proxy callback may not be null");
            this.throwException = throwException;
        }

        @Override
        public void onSuccess(T result) {
            if (terminalMethodName.compareAndSet(null, "onSuccess")) {
                proxy.onSuccess(result);
            } else {
                invokedAfterMethodCalled("onSuccess", null);
            }
        }

        @Override
        public void onFailure(Throwable e) {
            if (terminalMethodName.compareAndSet(null, "onFailure")) {
                proxy.onFailure(e);
            } else {
                invokedAfterMethodCalled("onFailure", e);
            }
        }

        private void invokedAfterMethodCalled(String method, Throwable e) {
            if (throwException) {
                String message = String.format("Cannot invoke %s(), %s() already invoked", method, terminalMethodName.get());
                if (e != null) {
                    throw new IllegalStateException(message, e);
                } else {
                    throw new IllegalStateException(message);
                }
            }
        }
    }

    private static final Set<Type> WRAPPER_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            CompletableFuture.class,
            Optional.class
    )));

    public static AsyncInvocationHandler from(
            Method method,
            RequestEncoder requestEncoder,
            List<RequestInterceptor> requestInterceptors,
            ResponseDecoderFactory responseDecoder,
            ResponseDecoderFactory errorDecoder,
            Client client,
            Target target) {
        return new AsyncInvocationHandler(method, requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
    }

    @Getter(AccessLevel.PROTECTED)
    private final Type effectiveReturnType;

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

    private final Map<String, StringInterpolator> interpolatorCache;

    private final String methodKey;

    private final boolean isAsyncResponseType;

    private final boolean isOptionalResponseType;

    // passed in //

    private final RequestEncoder encoder;

    private final List<RequestInterceptor> requestInterceptors;

    private final ResponseDecoderFactory responseDecoder;

    private final ResponseDecoderFactory errorDecoder;

    private final Client client;

    private final Target target;

    private AsyncInvocationHandler(
            @NonNull Method method,
            @NonNull RequestEncoder encoder,
            @NonNull List<RequestInterceptor> requestInterceptors,
            @NonNull ResponseDecoderFactory responseDecoder,
            @NonNull ResponseDecoderFactory errorDecoder,
            @NonNull Client client,
            @NonNull Target target) {

        this.target = target;

        this.encoder = encoder;

        this.requestInterceptors = Collections.unmodifiableList(new ArrayList<>(requestInterceptors));

        this.responseDecoder = responseDecoder;

        this.errorDecoder = errorDecoder;

        this.client = client;

        this.effectiveReturnType = parseReturnType(method.getGenericReturnType());

        this.isAsyncResponseType = method.getReturnType().isAssignableFrom(CompletableFuture.class);

        this.isOptionalResponseType = isOptionalReturnType(method.getGenericReturnType());

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

        this.interpolatorCache = buildInterpolatorCache(this.path, this.queryParams, this.headerParams);

    }

    private Map<String, StringInterpolator> buildInterpolatorCache(String path, List<HttpParam> queryParams, List<HttpParam> headerParams) {
        Map<String, StringInterpolator> out = new HashMap<>();

        out.put(path, SimpleStringInterpolator.of(path));

        queryParams.forEach(param -> {
            out.put(param.getKey(), SimpleStringInterpolator.of(param.getKey()));
            out.put(param.getValue(), SimpleStringInterpolator.of(param.getValue()));
        });

        headerParams.forEach(param -> {
            out.put(param.getKey(), SimpleStringInterpolator.of(param.getKey()));
            out.put(param.getValue(), SimpleStringInterpolator.of(param.getValue()));
        });

        return out;
    }

    private boolean isOptionalReturnType(Type genericReturnType) {

        if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType type = ((ParameterizedType) genericReturnType);

            if (Optional.class.equals(type.getRawType())) {
                return true;
            }

            if (isAsyncResponseType) {
                return isOptionalReturnType(type.getActualTypeArguments()[0]);
            }
        }

        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        CompletableFuture<Object> response = new CompletableFuture<>();

        try {
            setUpInterceptors(0, buildRequest(args), wrap(new Callback<Response<?>>() {
                @Override
                public void onSuccess(Response<?> result) {
                    response.complete(result == null ? null : result.getEntity());
                }

                @Override
                public void onFailure(Throwable e) {
                    response.completeExceptionally(e);
                }
            }), response::completeExceptionally);
        } catch (Throwable e) {
            response.completeExceptionally(e);
        }

        if (isAsyncResponseType) {
            return response;
        } else {
            try {
                return response.get();
            } catch (Throwable e) {
                throw ScreechException.handle(e);
            }
        }
    }

    private Type parseReturnType(Type type) {

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            if (WRAPPER_TYPES.contains(parameterizedType.getRawType())) {
                return parseReturnType(parameterizedType.getActualTypeArguments()[0]);
            }
        }

        return type;
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

            // `Param` isn't repeatable, so there should only ever be exactly 1
            Optional<Param> params = Arrays.stream(parameterAnnotations[i])
                    .filter(annotation -> annotation instanceof Param)
                    .map(annotation -> (Param) annotation)
                    .findAny();

            if (!params.isPresent()) {
                continue;
            }

            Param param = params.get();

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

    private URI interpolateUri(String uri, Map<String, String> templateParameters) {
        return URI.create(getInterpolator(uri).interpolate(templateParameters));
    }

    private List<HttpParam> interpolateHttpParams(List<HttpParam> params, Map<String, String> templateParams) {
        return params.stream()
                .map(param -> interpolateSingleHttpParam(param, templateParams))
                .collect(Collectors.toList());
    }

    private HttpParam interpolateSingleHttpParam(HttpParam param, Map<String, String> templateParams) {

        if (SimpleStringInterpolator.requiresInterpolation(param.getKey()) || SimpleStringInterpolator.requiresInterpolation(param.getValue())) {
            return HttpParam.builder()
                    .key(getInterpolator(param.getKey()).interpolate(templateParams))
                    .value(getInterpolator(param.getValue()).interpolate(templateParams))
                    .build();
        }

        // if neither the key nor the value requires interpolation, we can return it as-is since it's immutable
        return param;
    }

    private StringInterpolator getInterpolator(String input) {
        return interpolatorCache.containsKey(input) ? interpolatorCache.get(input) : SimpleStringInterpolator.of(input);
    }

    private String parseContentType(List<HttpParam> headers) {
        headers = Optional.ofNullable(headers).orElseGet(Collections::emptyList);

        return headers.stream()
                .filter(header -> "content-type".equalsIgnoreCase(header.getKey()))
                .findAny()
                .map(HttpParam::getValue)
                .orElse("application/octet-stream");
    }

    private void setUpInterceptors(int index, Request<?> request, Callback<Response<?>> callback, Consumer<Throwable> errorHandler) {

        Callback<Response<?>> errorHandlingCallback = new Callback<Response<?>>() {
            @Override
            public void onSuccess(Response<?> result) {
                try {
                    callback.onSuccess(result);
                } catch (Throwable e) {
                    errorHandler.accept(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                try {
                    callback.onFailure(t);
                } catch (Throwable e) {
                    e.addSuppressed(t);
                    errorHandler.accept(e);
                }
            }
        };

        if (index >= requestInterceptors.size()) {
            performClientCall(request, errorHandlingCallback);
        } else {
            RequestInterceptor requestInterceptor = requestInterceptors.get(index);

            //noinspection CodeBlock2Expr
            BiConsumer<Request<?>, Callback<Response<Object>>> interceptorCallback = (modifiedRequest, responseCallback) -> {
                setUpInterceptors(index + 1, modifiedRequest, wrapResponseCallback(responseCallback), errorHandlingCallback::onFailure);
            };

            Util.runDangerousCode(errorHandlingCallback, () -> requestInterceptor.intercept(request, interceptorCallback, errorHandlingCallback));
        }

    }

    private void performClientCall(Request request, Callback<Response<?>> handler) {
        if (request.getEntity() != null) {

            final Callback<ByteBuffer> byteBufferCallback = new Callback<ByteBuffer>() {

                @Override
                public void onSuccess(ByteBuffer result) {
                    doRequest(result, request, handler);
                }

                @Override
                public void onFailure(Throwable e) {
                    handler.onFailure(e);
                }

            };

            Util.runDangerousCode(handler, () -> encoder.encode(request.getEntity(), byteBufferCallback));
        } else {
            doRequest(null, request, handler);
        }
    }

    private void doRequest(ByteBuffer buffer, Request request, Callback<Response<?>> callback) {
        SerializedRequest serializedRequest = convertRequestIntoSerializedRequest(buffer, request);

        Client.ClientCallback clientCallback = new ClientCallbackImpl(callback, this::createDecoder);

        Util.runDangerousCode(callback, () -> client.request(serializedRequest, clientCallback));
    }

    private ResponseDecoder createDecoder(ResponseHeaders clientResponse, Callback<Response<?>> callback) {
        if (clientResponse == null) {
            callback.onFailure(new NullPointerException(String.format("Client '%s' returned null ResponseHeaders", client.getClass().getSimpleName())));
            return null;
        } else if (clientResponse.getStatus() >= 300) {
            return createFailureDecoder(clientResponse, callback);
        } else {
            return createSuccessDecoder(clientResponse, callback);
        }
    }

    private ResponseDecoder createFailureDecoder(ResponseHeaders clientResponse, Callback<Response<?>> callback) {
        final Callback<Object> responseDecoderCallback = new TransformingCallback<Object, Response<?>>(callback) {

            @Override
            protected Response<?> convert(Object entity) {
                return Response.builder()
                        .entity(isOptionalResponseType ? Optional.ofNullable(entity) : entity)
                        .build();
            }
        };

        return Util.runDangerousCode(callback, () -> errorDecoder.create(clientResponse, effectiveReturnType, wrap(responseDecoderCallback)));
    }

    private ResponseDecoder createSuccessDecoder(ResponseHeaders clientResponse, Callback<Response<?>> callback) {
        final Callback<Object> responseDecoderCallback = new TransformingCallback<Object, Response<?>>(callback) {

            @Override
            protected Response<?> convert(Object entity) {
                return Response.builder()
                        .entity(isOptionalResponseType ? Optional.ofNullable(entity) : entity)
                        .build();
            }
        };

        return Util.runDangerousCode(callback, () -> responseDecoder.create(clientResponse, effectiveReturnType, wrap(responseDecoderCallback)));
    }

    private <T> Callback<T> wrap(Callback<T> callback) {
        return new ConditionallyProxyingCallabck<>(callback, true);
    }

    private Callback<Response<?>> wrapResponseCallback(Callback<Response<Object>> responseCallback) {
        return wrap(new TransformingCallback<Response<?>, Response<Object>>(responseCallback) {

            @Override
            protected Response<Object> convert(Response<?> entity) {
                // noinspection unchecked
                return (Response<Object>) entity;
            }
        });
    }

}

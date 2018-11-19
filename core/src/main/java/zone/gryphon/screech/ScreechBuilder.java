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

package zone.gryphon.screech;

import lombok.NonNull;
import lombok.ToString;
import zone.gryphon.screech.internal.AsyncInvocationHandler;
import zone.gryphon.screech.internal.ReflectiveScreech;
import zone.gryphon.screech.internal.ScreechThreadFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@ToString
public class ScreechBuilder {

    private final int numCores = Runtime.getRuntime().availableProcessors();

    private final Supplier<Executor> executorSupplier = () -> new ThreadPoolExecutor(numCores, numCores,
            Long.MAX_VALUE, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ScreechThreadFactory("ScreechClient"));

    private RequestEncoder requestEncoder = new RequestEncoder.StringRequestEncoder();

    private List<RequestInterceptor> requestInterceptors = new ArrayList<>();

    private ResponseDecoderFactory responseDecoder = new ResponseDecoderFactory.SuccessResponseDecoderFactory();

    private ResponseDecoderFactory errorDecoder = new ResponseDecoderFactory.ErrorResponseDecoderFactory();

    private Executor requestExecutor = null;

    private Executor responseExecutor = null;

    private Client client;

    public ScreechBuilder(@NonNull Client client) {
        this.client = client;
    }

    public ScreechBuilder requestEncoder(@NonNull RequestEncoder requestEncoder) {
        this.requestEncoder = requestEncoder;
        return this;
    }

    public ScreechBuilder addRequestInterceptor(@NonNull RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this;
    }

    public ScreechBuilder addRequestInterceptors(@NonNull Iterable<RequestInterceptor> requestInterceptors) {
        requestInterceptors.forEach(this::addRequestInterceptor);
        return this;
    }

    public ScreechBuilder responseDecoder(@NonNull ResponseDecoderFactory responseDecoder) {
        this.responseDecoder = responseDecoder;
        return this;
    }

    public ScreechBuilder errorDecoder(@NonNull ResponseDecoderFactory errorDecoder) {
        this.errorDecoder = errorDecoder;
        return this;
    }

    public ScreechBuilder requestExecutor(@NonNull Executor executor) {
        this.requestExecutor = executor;
        return this;
    }

    public ScreechBuilder responseExecutor(@NonNull Executor executor) {
        this.responseExecutor = executor;
        return this;
    }

    private Executor getOrDefaultRequestExecutor() {
        return requestExecutor == null ? executorSupplier.get() : requestExecutor;
    }

    private Executor getOrDefaultResponseExecutor() {
        return responseExecutor == null ? executorSupplier.get() : responseExecutor;
    }

    public <T> T build(Class<T> clazz, Target target) {
        Executor requestExecutor = getOrDefaultRequestExecutor();

        Executor responseExecutor = getOrDefaultResponseExecutor();

        ReflectiveScreech reflectiveScreech = new ReflectiveScreech(requestEncoder, requestInterceptors, responseDecoder, errorDecoder, requestExecutor, responseExecutor, client, clazz, target);

        //noinspection unchecked
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, reflectiveScreech);
    }

}

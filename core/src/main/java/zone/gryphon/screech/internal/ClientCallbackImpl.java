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

import lombok.extern.slf4j.Slf4j;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.model.Response;
import zone.gryphon.screech.model.ResponseHeaders;

import java.util.Optional;
import java.util.function.BiFunction;

@Slf4j
public class ClientCallbackImpl implements Client.ClientCallback {

    private final Callback<Response<?>> callback;

    private final BiFunction<ResponseHeaders, Callback<Response<?>>, ResponseDecoder> factory;

    private volatile boolean terminalOperationCalled = false;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private volatile Optional<ResponseDecoder> consumer = Optional.empty();

    public ClientCallbackImpl(Callback<Response<?>> callback, BiFunction<ResponseHeaders, Callback<Response<?>>, ResponseDecoder> factory) {
        this.callback = callback;
        this.factory = factory;
    }

    @Override
    public Client.ContentCallback headers(ResponseHeaders responseHeaders) {
        consumer = Optional.ofNullable(factory.apply(responseHeaders, new Callback<Response<?>>() {
            @Override
            public void onSuccess(Response<?> result) {
                runIfNoTerminalOperationCalled(true, () -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(Throwable e) {
                runIfNoTerminalOperationCalled(true, () -> callback.onFailure(e));
            }
        }));

        return content -> runIfNoTerminalOperationCalled(false, () -> consumer.ifPresent(c -> c.content(content)));
    }

    @Override
    public void abort(Throwable t) {
        runIfNoTerminalOperationCalled(true, () -> {

            try {
                consumer.ifPresent(ResponseDecoder::abort);
            } catch (Throwable ignore) {
                // ignore
            }

            callback.onFailure(t);

        });
    }

    @Override
    public void complete() {
        runIfNoTerminalOperationCalled(true, () -> consumer.ifPresent(ResponseDecoder::complete));
    }

    private void runIfNoTerminalOperationCalled(boolean markAsTerminalOperationComplete, Runnable runnable) {

        if (terminalOperationCalled) {
            return;
        }

        try {
            runnable.run();
        } finally {
            if (markAsTerminalOperationComplete) {
                terminalOperationCalled = true;
            }
        }

    }
}

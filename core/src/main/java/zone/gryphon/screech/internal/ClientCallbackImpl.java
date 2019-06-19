/*
 * Copyright 2019-2019 Gryphon Zone
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
 */

package zone.gryphon.screech.internal;

import zone.gryphon.screech.Client;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.model.ResponseHeaders;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;


public class ClientCallbackImpl implements Client.ClientCallback {

    private final Consumer<Throwable> onError;

    private final Function<ResponseHeaders, ResponseDecoder> factory;

    private volatile boolean terminalOperationCalled = false;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private volatile Optional<ResponseDecoder> maybeResponseDecoder = Optional.empty();

    public ClientCallbackImpl(Consumer<Throwable> onError, Function<ResponseHeaders, ResponseDecoder> factory) {
        this.onError = onError;
        this.factory = factory;
    }

    @Override
    public Client.ContentCallback headers(ResponseHeaders responseHeaders) {
        maybeResponseDecoder = Optional.ofNullable(factory.apply(responseHeaders));

        return content -> runIfNoTerminalOperationCalled(false, () -> {
            maybeResponseDecoder.ifPresent(c -> c.content(content));
        });
    }

    @Override
    public void abort(Throwable t) {
        runIfNoTerminalOperationCalled(true, () -> {

            try {
                maybeResponseDecoder.ifPresent(ResponseDecoder::abort);
            } catch (Throwable ignore) {
                // ignore
            }

            onError.accept(t);
        });
    }

    @Override
    public void complete() {
        runIfNoTerminalOperationCalled(true, () -> maybeResponseDecoder.ifPresent(ResponseDecoder::complete));
    }

    private void runIfNoTerminalOperationCalled(boolean markAsTerminalOperationComplete, Runnable runnable) {

        if (terminalOperationCalled) {
            return;
        }

        if (markAsTerminalOperationComplete) {
            terminalOperationCalled = true;
        }

        runnable.run();
    }
}

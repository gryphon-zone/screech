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

package zone.gryphon.screech.util;

import zone.gryphon.screech.Callback;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.model.Response;
import zone.gryphon.screech.model.ResponseHeaders;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ClientCallbackImpl implements Client.ClientCallback {

    private final Callback<Response<?>> callback;

    private final Function<ResponseHeaders, Consumer<ByteBuffer>> factory;

    public ClientCallbackImpl(Callback<Response<?>> callback, Function<ResponseHeaders, Consumer<ByteBuffer>> factory) {
        this.callback = callback;
        this.factory = factory;
    }

    @Override
    public Client.ContentCallback onHeaders(ResponseHeaders responseHeaders) {
        Consumer<ByteBuffer> consumer = factory.apply(responseHeaders);

        return null;
    }

    @Override
    public void abort(Throwable t) {

    }

    @Override
    public void complete() {

    }
}

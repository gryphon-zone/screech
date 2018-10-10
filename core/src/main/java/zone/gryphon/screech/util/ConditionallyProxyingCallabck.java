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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ConditionallyProxyingCallabck<T> implements Callback<T> {

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

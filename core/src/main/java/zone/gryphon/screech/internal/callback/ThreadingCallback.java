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

package zone.gryphon.screech.internal.callback;

import lombok.NonNull;
import zone.gryphon.screech.Callback;

import java.util.concurrent.Executor;

public class ThreadingCallback<T> implements Callback<T> {

    private final Executor service;

    private final Callback<T> proxy;

    private final Thread invokingThread;

    public ThreadingCallback(@NonNull Executor service, @NonNull Callback<T> proxy) {
        this.service = service;
        this.proxy = proxy;
        this.invokingThread = Thread.currentThread();
    }

    @Override
    public void onSuccess(T result) {
        if (Thread.currentThread().equals(invokingThread)) {
            proxy.onSuccess(result);
        } else {
            service.execute(() -> proxy.onSuccess(result));
        }
    }

    @Override
    public void onFailure(Throwable e) {
        if (Thread.currentThread().equals(invokingThread)) {
            proxy.onFailure(e);
        } else {
            service.execute(() -> proxy.onFailure(e));
        }
    }
}
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

package zone.gryphon.screech.internal.callback;

import zone.gryphon.screech.Callback;

import java.util.Objects;

public abstract class TransformingCallback<T, R> implements Callback<T> {

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
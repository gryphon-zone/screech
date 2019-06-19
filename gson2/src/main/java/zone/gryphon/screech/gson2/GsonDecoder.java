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

package zone.gryphon.screech.gson2;

import com.google.gson.Gson;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.util.BufferingResponseDecoder;
import zone.gryphon.screech.util.ExpandableByteBuffer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Objects;

public class GsonDecoder extends BufferingResponseDecoder {

    private final Gson gson;

    private final Type type;

    private final Callback<Object> callback;

    public GsonDecoder(Gson gson, ResponseHeaders responseHeaders, Type type, Callback<Object> callback) {
        super(responseHeaders);
        this.gson = Objects.requireNonNull(gson, "gson");
        this.type = Objects.requireNonNull(type, "type");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    @Override
    public void complete(ExpandableByteBuffer buffer) {

        // since backing buffer for stream is in-memory, it should never block, and therefore it should be safe to call
        try (Reader reader = new BufferedReader(new InputStreamReader(buffer.createInputStream()))) {
            callback.onSuccess(gson.fromJson(reader, type));
        } catch (Throwable t) {
            callback.onFailure(t);
        }
    }

    @Override
    public String toString() {
        return "GsonDecoder{Gson@" + gson.hashCode() + '}';
    }
}

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

package zone.gryphon.screech.benchmark.testing;

import zone.gryphon.screech.Client;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.nio.ByteBuffer;
import java.util.Collections;

import static java.nio.charset.StandardCharsets.UTF_8;

public class NoOpClient implements Client {

    private static final ResponseHeaders headers = ResponseHeaders.builder()
            .headers(Collections.singletonList(HttpParam.builder().key("Content-Type").value("application/json").build()))
            .build();

    private static final ByteBuffer CONTENT = ByteBuffer.wrap("\"foo\"".getBytes(UTF_8));

    @Override
    public void request(SerializedRequest request, ClientCallback callback) {
        ContentCallback content = callback.headers(headers);
        content.content(CONTENT);
        callback.complete();
    }
}

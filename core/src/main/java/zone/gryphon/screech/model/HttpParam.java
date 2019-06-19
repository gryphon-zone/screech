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

package zone.gryphon.screech.model;

import lombok.Builder;
import lombok.Value;

import java.beans.ConstructorProperties;
import java.util.Objects;

@Value
@Builder(toBuilder = true)
public class HttpParam {

    public static HttpParam from(String key, String value) {
        return new HttpParam(key, value);
    }

    private final String key;

    private final String value;

    @ConstructorProperties({"key", "value"})
    public HttpParam(String key, String value) {
        this.key = Objects.requireNonNull(key, "key may not be null");
        this.value = value;
    }

}

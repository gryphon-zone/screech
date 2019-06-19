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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Headers + HTTP status code for a response.
 */
@Value
@Builder(toBuilder = true)
public class ResponseHeaders {

    /**
     * The status code of the request
     */
    private final int status;

    /**
     * Headers on the request
     */
    private final List<HttpParam> headers;

    /**
     * Return the value for the given key, if one exists.
     * Note that key comparisons are case insensitive, per the HTTP spec
     * (e.g. a request for "foo" will match an entry with the key "Foo").
     * In the event that multiple headers match the requested key, which value will be returned is formally undefined.
     *
     * @param key The key to search for
     * @return empty if the key is null or there are no headers, or if there is no value for the given key.
     * Otherwise, an optional containing the value for the requested key
     */
    public Optional<String> getValue(String key) {

        if (key == null || headers == null) {
            return Optional.empty();
        }

        return headers.stream()
                .filter(Objects::nonNull)
                .filter(header -> key.equalsIgnoreCase(header.getKey()))
                .findAny()
                .map(HttpParam::getValue);
    }

    /**
     * Get the value of the "Content-Length" header, if one is present
     *
     * @return The value of the content length header, if it is present and can be parsed as a valid long
     */
    public Optional<Long> getContentLength() {
        return getValue("content-length").flatMap(this::parseLong);
    }

    private Optional<Long> parseLong(String input) {

        if (input == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.decode(input));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}

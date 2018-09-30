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

package zone.gryphon.screech.model;


import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseHeadersTest {

    @Test
    public void testGetContentLengthHappyPath() {
        assertThat(build(HttpParam.from("Content-Length", "12345")).getContentLength()).contains(12345L);
    }

    @Test
    public void testGetContentLengthCaseInsensitive() {
        assertThat(build(HttpParam.from("CONTENT-LENGTH", "12345")).getContentLength()).contains(12345L);
    }

    @Test
    public void testGetContentLengthMultipleValue() {
        assertThat(build(
                HttpParam.from("Content-Length", "1"),
                HttpParam.from("Content-Length", "2"),
                HttpParam.from("Content-Length", "3")
        ).getContentLength()).isIn(Optional.of(1L), Optional.of(2L), Optional.of(3L));
    }

    @Test
    public void testGetContentLengthInvalidValue() {
        assertThat(build(HttpParam.from("Content-Length", "this is not a long")).getContentLength()).isEmpty();
    }

    @Test
    public void testGetContentLengthAbsent() {
        assertThat(build().getContentLength()).isEmpty();
    }


    private ResponseHeaders build(HttpParam... params) {
        return ResponseHeaders.builder()
                .status(0)
                .headers(Arrays.asList(params))
                .build();
    }
}
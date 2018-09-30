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

package zone.gryphon.screech;

import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.nio.ByteBuffer;

public interface Client {

    void request(SerializedRequest request, ClientCallback callback);

    /**
     * Callback clients can use to return data about the request
     */
    interface ClientCallback {

        /**
         * Callback method invoked when the HTTP response code and headers are available.
         * <p>
         * This method can only be invoked once per request.
         * <p>
         * This method will return a callback which can be used to stream the response content, if any.
         * Once the response content has been exhausted, {@link #complete()} should be called to indicate as such.
         * <p>
         * Note that should should be invoked <b>before</b> {@link #complete()}, but need not be invoked before
         * {@link #abort(Throwable)}
         *
         * @param responseHeaders The response headers
         * @return A callback which can be used to stream the response content
         */
        ContentCallback headers(ResponseHeaders responseHeaders);

        /**
         * Callback method invoked when the request fails for any reason. This will ultimately result in an exception
         * being bubbled up to consumer code.
         * <p>
         * No other methods should be invoked on the callback after this is called (i.e. this is a terminal operation)
         *
         * @param t The exception which caused the request to fail
         */
        void abort(Throwable t);

        /**
         * Called when the request completes successfully. Note that "successful" does not mean a 2xx response code,
         * only that the client was able to successfully receive all data from the upstream server.
         * <p>
         * No other methods should be invoked on the callback after this is called (i.e. this is a terminal operation)
         */
        void complete();

    }

    /**
     * Callback used to pass response body
     */
    interface ContentCallback {

        /**
         * Callback for when clients have data from the response body available.
         * <p>
         * This method may be invoked multiple times as response content is available (i.e. the client does not
         * have to pass the entire response body in at once).
         *
         * @param content The currently available content
         */
        void content(ByteBuffer content);

    }
}

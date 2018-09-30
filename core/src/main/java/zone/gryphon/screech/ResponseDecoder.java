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

import java.nio.ByteBuffer;

public interface ResponseDecoder {

    /**
     * Called when content is available
     *
     * @param content The new content
     */
    void content(ByteBuffer content);

    /**
     * Called when no more content is available, and the decoder should decode the content it received prior to this call
     */
    void complete();

    /**
     * Called when the request failed while streaming content, and the decoder should discard any resources it had open.
     *
     * <h3>Important:</h3>
     * The decoder should <i>not</i> call any methods on the response callback in this case, and should return after
     * clearing any resources it had open.
     */
    void abort();

}

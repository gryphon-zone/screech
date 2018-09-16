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

package zone.gryphon.screech.exception;

import lombok.Getter;
import lombok.NonNull;
import zone.gryphon.screech.model.SerializedResponse;

@Getter
public class ScreechException extends RuntimeException {

    private final int status;

    protected ScreechException(String message, int status) {
        super(message);
        this.status = status;
    }

    public static ScreechException from(@NonNull SerializedResponse response) {
        return new ScreechException("Failed to read response", response.getStatus());
    }
}
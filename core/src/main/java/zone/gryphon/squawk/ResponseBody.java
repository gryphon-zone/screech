package zone.gryphon.squawk;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.nio.ByteBuffer;

@Value
@Builder(toBuilder = true)
public class ResponseBody {

    public static ResponseBody from(@NonNull ByteBuffer buffer, String contentType, String encoding) {
        return ResponseBody.builder()
                .body(buffer)
                .contentType(contentType)
                .encoding(encoding)
                .build();
    }

    private final ByteBuffer body;

    private final String contentType;

    private final String encoding;

}

package zone.gryphon.screech.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.RequestEncoder;

import java.nio.ByteBuffer;
import java.util.Objects;

public class JacksonEncoder implements RequestEncoder {

    private final ObjectMapper objectMapper;

    public JacksonEncoder() {
        this(new ObjectMapper());
    }

    public JacksonEncoder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public <T> void encode(T entity, Callback<ByteBuffer> callback) {
        try {
            callback.onSuccess(ByteBuffer.wrap(objectMapper.writeValueAsBytes(entity)));
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}

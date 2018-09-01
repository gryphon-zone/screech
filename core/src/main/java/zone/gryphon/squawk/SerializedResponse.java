package zone.gryphon.squawk;

import lombok.Builder;
import lombok.Value;

import java.util.Collection;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class SerializedResponse {

    private final ResponseBody responseBody;

    private final Map<String, Collection<String>> headers;

    private final int status;

}

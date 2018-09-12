package zone.gryphon.screech.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class SerializedResponse {

    private final ResponseBody responseBody;

    private final List<HttpParam> headers;

    private final int status;

}

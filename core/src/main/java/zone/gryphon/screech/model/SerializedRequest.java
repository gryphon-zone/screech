package zone.gryphon.screech.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.net.URI;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class SerializedRequest {

    @NonNull
    private final String method;

    /**
     * The request uri.
     * <i>Note: will not include query parameters, see {@link #getQueryParams()}</i>
     */
    @NonNull
    private final URI uri;

    /**
     * The request body. Null if the request has no body (e.g. it's a GET request)
     */
    private final RequestBody requestBody;

    /**
     * Headers for the request
     */
    private final List<HttpParam> headers;

    /**
     * Query parameters for the request
     */
    private final List<HttpParam> queryParams;

}

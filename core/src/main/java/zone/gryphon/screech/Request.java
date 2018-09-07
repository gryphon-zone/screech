package zone.gryphon.screech;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Builder
@Value
public class Request<T> {

    @NonNull
    private final String method;

    /**
     * The request uri.
     * <i>Note: will not include query parameters, see {@link #getQueryParams()}</i>
     */
    @NonNull
    private final String uri;

    /**
     * Entity
     */
    private final T entity;

    private final Map<String, String> templateParameters;

    /**
     * Headers for the request
     */
    private final List<HttpParam> headers;

    /**
     * Query parameters for the request
     */
    private final List<HttpParam> queryParams;

}

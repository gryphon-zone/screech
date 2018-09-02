package zone.gryphon.squawk;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Request<T> {

    private final T entity;

}

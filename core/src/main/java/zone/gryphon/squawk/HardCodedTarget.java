package zone.gryphon.squawk;

import lombok.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class HardCodedTarget implements Target {

    private static URI convert(URL target) {
        try {
            return target.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unable to parse " + target + " as a valid URI", e);
        }
    }

    private final URI target;

    public HardCodedTarget(URL target) {
        this(convert(target));
    }

    public HardCodedTarget(String target) {
        this(URI.create(target));
    }

    public HardCodedTarget(@NonNull URI target) {
        this.target = target;
    }

    @Override
    public URI getTarget() {
        return target;
    }
}

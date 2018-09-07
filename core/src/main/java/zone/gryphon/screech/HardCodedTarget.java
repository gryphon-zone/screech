package zone.gryphon.screech;

import lombok.NonNull;

import java.net.URI;
import java.net.URL;

public class HardCodedTarget implements Target {

    private final String target;

    public HardCodedTarget(@NonNull URL target) {
        this(target.toString());
    }

    public HardCodedTarget(String target) {
        this.target = target;
    }

    public HardCodedTarget(@NonNull URI target) {
        this(target.toString());
    }

    @Override
    public String getTarget() {
        return target;
    }
}

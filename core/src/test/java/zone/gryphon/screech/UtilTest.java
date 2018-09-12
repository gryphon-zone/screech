package zone.gryphon.screech;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import zone.gryphon.screech.util.Util;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Slf4j
public class UtilTest {

    public interface TestGenericInterface {

        void foo(String string);

        void foo(List<String> string);

        void foo(Map<String, String> string);

    }

    @Test
    public void name() {

        for (Method method : TestGenericInterface.class.getMethods()) {
            log.info(Util.toString(method));
        }

    }
}
package zone.gryphon.squawk;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

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
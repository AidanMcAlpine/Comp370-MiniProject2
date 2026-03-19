package mini_project_01;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JsonMessageSerializerTest {

    @Test
    void canCreateSerializer() {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        assertNotNull(serializer);
    }
}


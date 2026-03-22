package mini_project_01;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    @Test
    void messageClassLoads() {
        // This will pass if Message has a no-arg constructor.
        // If it doesn't, Maven will show an error and we’ll adjust.
        Message msg = new Message();
        assertNotNull(msg);
    }
}


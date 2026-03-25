package mini_project_01;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * EventLogger — Observer that logs all Monitor events to the console.
 *
 * This class demonstrates the key benefit of the Observer pattern:
 * extensibility without modifying the subject. EventLogger was added as
 * a new listener by simply implementing Observer and registering with
 * the Monitor — zero lines of Monitor code were changed.
 *
 * In a production system, this could be extended to write to a file,
 * a database, or a remote logging service like Splunk/ELK.
 *
 * @author Gurjas — Observer Pattern Developer (COMP 370 Mini Project 2)
 */
public class EventLogger implements Observer {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Called by Monitor.notifyObservers() for every event.
     * Logs the event with a timestamp to standard output.
     *
     * @param event the event string from Monitor
     */
    @Override
    public void update(String event) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "] [EVENT LOG] " + event);
    }
}

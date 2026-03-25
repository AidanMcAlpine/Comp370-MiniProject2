package mini_project_01;

/**
 * Observer interface for the Observer design pattern.
 * 
 * Any class that wants to be notified of Monitor events (such as failover,
 * server registration, server timeout, or heartbeat events) should implement
 * this interface and register itself with the Monitor via addObserver().
 * 
 * This decouples the Monitor (subject) from the classes that react to its
 * state changes, allowing new listeners to be added without modifying Monitor.
 * 
 * @author Gurjas — Observer Pattern Developer (COMP 370 Mini Project 2)
 */
public interface Observer {

    /**
     * Called by the subject (Monitor) when a noteworthy event occurs.
     *
     * @param event A string describing the event. Format examples:
     *              "SERVER_REGISTERED:1"       — server with id 1 registered
     *              "SERVER_TIMEOUT:2"          — server 2 timed out
     *              "FAILOVER_TRIGGERED"        — failover process has started
     *              "FAILOVER_COMPLETE:3"       — server 3 is the new primary
     *              "HEARTBEAT_RECEIVED:1"      — heartbeat received from server 1
     */
    void update(String event);
}

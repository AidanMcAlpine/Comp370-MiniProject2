package mini_project_01;

import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * ServerNotifier — Observer that handles notifying backup servers of failover events.
 *
 * BEFORE the Observer pattern, Monitor.triggerFailover() contained a direct socket
 * loop that opened connections to every backup server and sent NEWPRIMARY messages.
 * This tightly coupled Monitor to the network notification protocol.
 *
 * AFTER the Observer pattern, this responsibility is extracted into ServerNotifier,
 * which implements Observer and is registered with the Monitor. When Monitor fires
 * "FAILOVER_COMPLETE:id", ServerNotifier reads the server registry and sends the
 * NEWPRIMARY messages itself. Monitor no longer knows or cares how notifications
 * are delivered.
 *
 * This also means that if the notification protocol needs to change (e.g., switching
 * from TCP sockets to HTTP or a message queue), only this class needs to be modified.
 *
 * @author Gurjas — Observer Pattern Developer (COMP 370 Mini Project 2)
 */
public class ServerNotifier implements Observer {

    private final Monitor monitor;
    private final JsonMessageSerializer serializer;

    /**
     * Create a ServerNotifier bound to the given Monitor.
     *
     * @param monitor the Monitor instance to read server state from
     */
    public ServerNotifier(Monitor monitor) {
        this.monitor = monitor;
        this.serializer = new JsonMessageSerializer();
    }

    /**
     * Called by Monitor.notifyObservers() when an event occurs.
     *
     * This method only reacts to "FAILOVER_COMPLETE:id" events by sending
     * NEWPRIMARY messages to all backup servers. All other events are ignored
     * by this observer (but could be handled by other observers).
     *
     * @param event the event string from Monitor
     */
    @Override
    public void update(String event) {
        if (event != null && event.startsWith("FAILOVER_COMPLETE:")) {
            int newPrimaryId;
            try {
                newPrimaryId = Integer.parseInt(event.split(":")[1]);
            } catch (NumberFormatException e) {
                System.err.println("[ServerNotifier] Invalid failover event: " + event);
                return;
            }
            notifyBackupServers(newPrimaryId);
        }
    }

    /**
     * Send NEWPRIMARY messages to all backup servers so they know who the
     * new primary is. This is the exact logic that was previously inside
     * Monitor.triggerFailover(), now decoupled into its own class.
     *
     * @param newPrimaryId the server ID of the newly elected primary
     */
    private void notifyBackupServers(int newPrimaryId) {
        Map<Integer, Map.Entry<String, Integer>> servers = monitor.getServers();
        Map.Entry<String, Integer> primaryAddress = servers.get(newPrimaryId);

        if (primaryAddress == null) {
            System.err.println("[ServerNotifier] New primary " + newPrimaryId
                    + " not found in server registry.");
            return;
        }

        String primaryPayload = primaryAddress.getKey() + ":" + primaryAddress.getValue();

        servers.forEach((Integer serverId, Map.Entry<String, Integer> address) -> {
            if (serverId == newPrimaryId) {
                return; // Don't notify the new primary about itself
            }
            try (
                    Socket notifySocket = new Socket(address.getKey(), address.getValue());
                    DataOutputStream out = new DataOutputStream(notifySocket.getOutputStream())) {
                out.write(serializer.serialize(
                        new Message("NEWPRIMARY", 0, primaryPayload)));
                System.out.println("[ServerNotifier] Notified server " + serverId
                        + " of new primary at " + primaryPayload);
            } catch (IOException e) {
                System.err.println("[ServerNotifier] Failed to notify backup server "
                        + serverId + ": " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[ServerNotifier] Serialization error notifying server "
                        + serverId + ": " + e.getMessage());
            }
        });
    }
}

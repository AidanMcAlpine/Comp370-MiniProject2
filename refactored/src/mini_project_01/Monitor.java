package mini_project_01;

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;

/**
 * Monitor — Central hub of the Server Redundancy Management System.
 *
 * Design patterns applied:
 *   - Singleton (by Nathan): Only one Monitor instance exists at runtime.
 *   - Observer  (by Gurjas): Monitor is the Subject. It maintains a list of
 *     Observer objects and calls notifyObservers(event) whenever a significant
 *     state change occurs, instead of making direct calls to other components.
 *
 * Observer events fired:
 *   "SERVER_REGISTERED:id"    — a server has registered with the monitor
 *   "SERVER_TIMEOUT:id"       — a server failed its heartbeat check
 *   "HEARTBEAT_RECEIVED:id"   — a heartbeat was received from a server
 *   "FAILOVER_TRIGGERED"      — failover process has started
 *   "FAILOVER_COMPLETE:id"    — failover finished; id is the new primary
 *   "FAILOVER_FAILED"         — no backup was available to promote
 */
public class Monitor {

    // ── Server management ───────────────────────────────────────────────
    private HashMap<Integer, Map.Entry<String, Integer>> servers;
    private int primaryServerId = -1;
    private FailoverManager failoverManager;

    // ── Heartbeat tracking ──────────────────────────────────────────────
    private HashMap<Integer, Instant> lastHeartbeat;
    private int timeoutThreshold;
    private Thread heartbeatWatcherThread;

    // ── Message listening ───────────────────────────────────────────────
    private boolean running;
    private ServerSocket messageListener;
    private Thread messageListenerThread;
    private IMessageSerializer<Message> serializer;
    private int port;

    // ── Observer pattern (Gurjas) ───────────────────────────────────────
    private final List<Observer> observers;

    // ── Singleton pattern (Nathan) ──────────────────────────────────────
    private static Monitor instance = null;

    /**
     * Returns the single Monitor instance (Singleton).
     * Creates it with default values (15s timeout, port 9000) on first call.
     */
    public static synchronized Monitor getInstance() {
        if (instance == null) {
            instance = new Monitor(15_000, 9000);
        }
        return instance;
    }

    /**
     * Private constructor — enforces Singleton.
     */
    private Monitor(int timeoutThreshold, int port) {
        this.lastHeartbeat = new HashMap<>();
        this.servers = new HashMap<>();
        this.timeoutThreshold = timeoutThreshold;
        this.running = false;
        this.port = port;
        this.serializer = new JsonMessageSerializer();
        this.failoverManager = new FailoverManager();
        this.observers = new ArrayList<>();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Observer management (Gurjas — Observer Pattern)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Register an observer to receive event notifications from the Monitor.
     *
     * @param o the observer to add
     */
    public void addObserver(Observer o) {
        if (o != null && !observers.contains(o)) {
            observers.add(o);
        }
    }

    /**
     * Unregister an observer so it no longer receives notifications.
     *
     * @param o the observer to remove
     */
    public void removeObserver(Observer o) {
        observers.remove(o);
    }

    /**
     * Notify all registered observers of an event.
     * This replaces direct method calls from Monitor to other classes,
     * decoupling the Monitor from its dependents.
     *
     * @param event a string describing what happened
     */
    public void notifyObservers(String event) {
        for (Observer o : observers) {
            try {
                o.update(event);
            } catch (Exception e) {
                System.err.println("[Monitor] Observer threw exception on event '"
                        + event + "': " + e.getMessage());
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Networking — message listener
    // ════════════════════════════════════════════════════════════════════

    /**
     * Ensure that only the address a server connected under can send heartbeats.
     */
    boolean validateAddress(int serverId, String address, int port) {
        var requiredSender = servers.get(serverId);
        if (requiredSender == null) {
            return false;
        } else if (requiredSender.getKey() != address
                || requiredSender.getValue() != port) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * (Runs in a thread)
     * Listens for messages (HEARTBEAT, REGISTER, GET_PRIMARY, GET_STATUS,
     * MANUAL_FAILOVER) from servers, clients, and admin tools.
     */
    void listenForMessages() {
        while (running) {
            try (
                    Socket socket = messageListener.accept();
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                byte[] recieved = new byte[4096];
                int bytesRead = in.read(recieved);
                recieved = Arrays.copyOf(recieved, bytesRead);
                Message message = serializer.deserialize(recieved);
                int serverId = message.getSenderId();
                String address = socket.getInetAddress().getHostAddress();
                int port = socket.getPort();
                switch (message.getType()) {
                    case "HEARTBEAT":
                        if (validateAddress(serverId, address, port)) {
                            recieveHeartbeat(serverId);
                            out.write(serializer.serialize(new Message("HEARTBEAT_ACK", 0, "")));
                        }
                        break;

                    case "REGISTER":
                        registerServer(serverId, address, port, message.getPayload() == "PRIMARY");
                        out.write(serializer.serialize(new Message("REGISTER_ACK", 0, "")));
                        break;

                    case "GET_PRIMARY":
                        Map.Entry<String, Integer> primaryAddress = servers.get(primaryServerId);
                        if (primaryAddress != null) {
                            out.write(serializer.serialize(new Message("CURRENT_PRIMARY", 0,
                                    primaryAddress.getKey() + ":" + primaryAddress.getValue())));
                        } else {
                            out.write(serializer.serialize(new Message("NO_CURRENT_PRIMARY", 0, "")));
                        }
                        break;

                    // ── GET_STATUS for Admin Dashboard (from MonitorPatch) ──
                    case "GET_STATUS":
                        StringBuilder sb = new StringBuilder();
                        servers.forEach((sid, addr) -> {
                            String role = (sid == primaryServerId) ? "PRIMARY" : "BACKUP";
                            Instant last = lastHeartbeat.get(sid);
                            long ago = (last != null)
                                    ? Duration.between(last, Instant.now()).toMillis()
                                    : -1;
                            String status = (ago >= 0 && ago < timeoutThreshold) ? "ALIVE" : "DEAD";
                            sb.append(sid).append(",")
                              .append(addr.getKey()).append(":").append(addr.getValue()).append(",")
                              .append(role).append(",")
                              .append(status).append(",")
                              .append(ago).append("ms ago");
                            sb.append(";");
                        });
                        out.write(serializer.serialize(
                                new Message("STATUS_RESPONSE", 0, sb.toString())));
                        break;

                    // ── MANUAL_FAILOVER for Admin actions (from MonitorPatch) ──
                    case "MANUAL_FAILOVER":
                        System.out.println("Admin requested manual failover.");
                        primaryServerId = -1;
                        triggerFailover();
                        Map.Entry<String, Integer> newPrimary = servers.get(primaryServerId);
                        if (newPrimary != null) {
                            out.write(serializer.serialize(new Message("FAILOVER_OK", 0,
                                    "New primary: " + newPrimary.getKey() + ":" + newPrimary.getValue())));
                        } else {
                            out.write(serializer.serialize(new Message("FAILOVER_FAIL", 0,
                                    "No backup available to promote.")));
                        }
                        break;

                    default:
                        System.out.println("Unknown monitor message type " + message.getType());
                        break;
                }

            } catch (IOException e) {
                System.out.println("Server failed to listen to heartbeat: " + e);
            } catch (Exception e) {
                System.out.println("Server failed to deserialize heartbeat: " + e);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Heartbeat checking
    // ════════════════════════════════════════════════════════════════════

    /**
     * Repeatedly checks whether connected servers have stopped sending heartbeats.
     * Removes them and performs failover logic when necessary.
     * Now fires Observer notifications on timeout events.
     */
    void checkHeartbeats() {
        while (running) {
            try {
                Thread.sleep(timeoutThreshold / 2);
                Instant now = Instant.now();
                lastHeartbeat.forEach((serverId, lastBeat) -> {
                    if (Duration.between(lastBeat, now).toMillis() > timeoutThreshold) {
                        servers.remove(serverId);
                        lastHeartbeat.remove(serverId);
                        System.out.println("Server " + serverId + " failed to send heartbeat");

                        // ── Observer notification: server timed out ──
                        notifyObservers("SERVER_TIMEOUT:" + serverId);

                        if (serverId == primaryServerId) {
                            primaryServerId = -1;
                        }
                    }
                });
                if (primaryServerId == -1) {
                    triggerFailover();
                }
            } catch (InterruptedException e) {
                System.out.println("Heartbeat check interrupted");
                break;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════════════

    /**
     * Starts the monitor (non-blocking).
     */
    public void start() throws IOException {
        messageListener = new ServerSocket(port);
        messageListenerThread = new Thread(() -> listenForMessages());
        messageListenerThread.start();
        heartbeatWatcherThread = new Thread(() -> checkHeartbeats());
        heartbeatWatcherThread.start();
        running = true;
    }

    /**
     * Stops the monitor.
     */
    public void stop() throws IOException {
        messageListener.close();
        running = false;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Server registration & heartbeats
    // ════════════════════════════════════════════════════════════════════

    /**
     * Registers a server with the monitor.
     * Now fires an Observer notification so all listeners know a server joined.
     */
    public void registerServer(int serverId, String address, int port, boolean isPrimary) {
        servers.put(serverId, new AbstractMap.SimpleImmutableEntry<>(address, port));
        recieveHeartbeat(serverId);
        if (isPrimary) {
            primaryServerId = serverId;
        }

        // ── Observer notification: new server registered ──
        notifyObservers("SERVER_REGISTERED:" + serverId);
    }

    /**
     * Updates last received heartbeat from a server.
     * Now fires an Observer notification so listeners can track liveness.
     */
    public void recieveHeartbeat(Integer serverId) {
        lastHeartbeat.put(serverId, Instant.now());

        // ── Observer notification: heartbeat received ──
        notifyObservers("HEARTBEAT_RECEIVED:" + serverId);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Failover — now decoupled via Observer
    // ════════════════════════════════════════════════════════════════════

    /**
     * Activates failover when the primary is lost.
     *
     * BEFORE Observer pattern:
     *   Monitor directly opened sockets to every backup server and sent
     *   NEWPRIMARY messages. This tightly coupled Monitor to the server
     *   notification protocol. Adding any new listener (e.g., a logging
     *   service) required modifying this method.
     *
     * AFTER Observer pattern:
     *   Monitor calls notifyObservers("FAILOVER_COMPLETE:id") and each
     *   registered Observer decides how to react. The backup notification
     *   logic is now handled by ServerNotifier (an Observer), and new
     *   listeners can be added by simply implementing Observer and calling
     *   monitor.addObserver() — zero changes to Monitor needed.
     */
    public void triggerFailover() {
        // ── Observer notification: failover starting ──
        notifyObservers("FAILOVER_TRIGGERED");

        primaryServerId = failoverManager.initiateFailover(servers);

        if (primaryServerId == -1) {
            // No available backups — notify observers of failure
            notifyObservers("FAILOVER_FAILED");
            return;
        }

        // ── Observer notification: failover complete ──
        // Observers (like ServerNotifier) handle notifying backup servers.
        // This replaces the direct socket loop that was previously here.
        notifyObservers("FAILOVER_COMPLETE:" + primaryServerId);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Getters (used by Launcher, ServerNotifier, etc.)
    // ════════════════════════════════════════════════════════════════════

    public int getPort() {
        return port;
    }

    /**
     * Returns an unmodifiable view of the current server registry.
     * Used by ServerNotifier to send NEWPRIMARY messages to backups.
     */
    public Map<Integer, Map.Entry<String, Integer>> getServers() {
        return Collections.unmodifiableMap(servers);
    }

    /**
     * Returns the current primary server ID.
     */
    public int getPrimaryServerId() {
        return primaryServerId;
    }
}

package mini_project_01;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client component for the Server Redundancy Management System.
 * 
 * Responsibilities (Step 6):
 *   - Query the Monitor to discover the current primary server.
 *   - Send PROCESS requests to the primary and display responses.
 *   - Automatically re-query the Monitor and reconnect when the primary fails.
 * 
 * The client uses the same JSON message protocol (Message + JsonMessageSerializer)
 * as the rest of the system, communicating over TCP sockets.
 */
public class Client {

    // ── Monitor connection details ──────────────────────────────────────
    private final String monitorHost;
    private final int monitorPort;

    // ── Current primary server connection info ──────────────────────────
    private String primaryHost;
    private int primaryPort;
    private boolean connected;

    // ── Serialization ───────────────────────────────────────────────────
    private final JsonMessageSerializer serializer;

    // ── Reconnection tuning ─────────────────────────────────────────────
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int RECONNECT_DELAY_MS     = 1500;
    private static final int SOCKET_TIMEOUT_MS      = 5000;

    // ── Logging ─────────────────────────────────────────────────────────
    private final List<String> eventLog;
    private Consumer<String> logListener;  // optional callback for the GUI

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    /**
     * Create a new Client that will discover the primary through the given Monitor.
     *
     * @param monitorHost hostname / IP of the Monitor
     * @param monitorPort port the Monitor is listening on
     */
    public Client(String monitorHost, int monitorPort) {
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.serializer  = new JsonMessageSerializer();
        this.eventLog    = new ArrayList<>();
        this.connected   = false;
    }

    // ────────────────────────────────────────────────────────────────────
    // Service Discovery (Step 6 – query monitor for primary)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Ask the Monitor which server is the current primary.
     *
     * @return true if a primary was discovered, false otherwise
     */
    public boolean discoverPrimary() {
        log("Querying monitor at " + monitorHost + ":" + monitorPort + " for current primary...");

        try (
            Socket socket = new Socket();
        ) {
            socket.connect(new InetSocketAddress(monitorHost, monitorPort), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            // Send GET_PRIMARY request
            Message request = new Message("GET_PRIMARY", 0, "");
            byte[] data = serializer.serialize(request);
            out.write(data);
            out.flush();

            // Read response
            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                byte[] responseData = new byte[bytesRead];
                System.arraycopy(buffer, 0, responseData, 0, bytesRead);
                Message response = serializer.deserialize(responseData);

                if (response != null && "CURRENT_PRIMARY".equals(response.getType())) {
                    // Payload format: "host:port"
                    String[] parts = response.getPayload().split(":");
                    if (parts.length == 2) {
                        primaryHost = parts[0];
                        primaryPort = Integer.parseInt(parts[1]);
                        connected = true;
                        log("Primary discovered → " + primaryHost + ":" + primaryPort);
                        return true;
                    }
                } else if (response != null && "NO_CURRENT_PRIMARY".equals(response.getType())) {
                    log("Monitor reports no primary is currently available.");
                } else {
                    log("Unexpected response from monitor: "
                            + (response != null ? response.getType() : "null"));
                }
            }
        } catch (SocketTimeoutException e) {
            log("Timeout while contacting monitor: " + e.getMessage());
        } catch (ConnectException e) {
            log("Could not connect to monitor: " + e.getMessage());
        } catch (IOException e) {
            log("IO error during discovery: " + e.getMessage());
        } catch (Exception e) {
            log("Error during discovery: " + e.getMessage());
        }

        connected = false;
        return false;
    }

    // ────────────────────────────────────────────────────────────────────
    // Send requests to the primary (Step 6)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Send a PROCESS request to the current primary and return the response.
     * If the connection fails, the client will automatically attempt to
     * reconnect via the Monitor (reconnection loop).
     *
     * @param payload the job payload to send (e.g. "job-42")
     * @return the response Message, or null if delivery ultimately failed
     */
    public Message sendRequest(String payload) {
        if (!connected || primaryHost == null) {
            log("Not connected to any primary. Attempting discovery...");
            if (!reconnect()) {
                log("Failed to find a primary after reconnection attempts.");
                return null;
            }
        }

        // Attempt to send; on failure trigger reconnection loop
        try {
            return doSend(payload);
        } catch (Exception e) {
            log("Request failed (" + e.getMessage() + "). Entering reconnection loop...");
            connected = false;
            if (reconnect()) {
                try {
                    return doSend(payload);
                } catch (Exception e2) {
                    log("Request failed again after reconnect: " + e2.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Internal helper – opens a fresh TCP connection to the primary,
     * sends the PROCESS message, and reads back the response.
     */
    private Message doSend(String payload) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(primaryHost, primaryPort), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            Message request = new Message("PROCESS", 0, payload);
            byte[] data = serializer.serialize(request);
            out.write(data);
            out.flush();

            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                byte[] responseBytes = new byte[bytesRead];
                System.arraycopy(buffer, 0, responseBytes, 0, bytesRead);
                Message response = serializer.deserialize(responseBytes);
                if (response != null) {
                    log("Response from primary [" + response.getSenderId() + "]: " + response.getPayload());

                    // If the primary itself says it's unhealthy, trigger re-discovery
                    if ("PRIMARY_ERROR".equals(response.getType())) {
                        log("Primary reported an error. Will re-discover on next request.");
                        connected = false;
                    }
                    return response;
                }
            }
        }
        throw new IOException("No valid response from primary at " + primaryHost + ":" + primaryPort);
    }

    // ────────────────────────────────────────────────────────────────────
    // Reconnection loop (Step 6)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Repeatedly re-query the Monitor to discover the new primary,
     * up to MAX_RECONNECT_ATTEMPTS with a delay between retries.
     *
     * @return true once a primary has been found
     */
    public boolean reconnect() {
        log("Starting reconnection loop (max " + MAX_RECONNECT_ATTEMPTS + " attempts)...");
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            log("Reconnection attempt " + attempt + "/" + MAX_RECONNECT_ATTEMPTS);
            if (discoverPrimary()) {
                log("Reconnected successfully to primary at " + primaryHost + ":" + primaryPort);
                return true;
            }
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Reconnection interrupted.");
                return false;
            }
        }
        log("All reconnection attempts exhausted. No primary available.");
        return false;
    }

    // ────────────────────────────────────────────────────────────────────
    // Ping (simple health-check shortcut)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Send a lightweight PING to the current primary and return the response.
     */
    public Message ping() {
        if (!connected || primaryHost == null) {
            log("Not connected. Attempting discovery before ping...");
            if (!discoverPrimary()) return null;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(primaryHost, primaryPort), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            Message ping = new Message("PING", 0, "ping");
            out.write(serializer.serialize(ping));
            out.flush();

            byte[] buf = new byte[4096];
            int n = in.read(buf);
            if (n > 0) {
                byte[] resp = new byte[n];
                System.arraycopy(buf, 0, resp, 0, n);
                Message response = serializer.deserialize(resp);
                log("Ping response: " + (response != null ? response.getPayload() : "null"));
                return response;
            }
        } catch (Exception e) {
            log("Ping failed: " + e.getMessage());
            connected = false;
        }
        return null;
    }

    // ────────────────────────────────────────────────────────────────────
    // Admin / Monitor queries (used by AdminInterface – Step 7)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Send a GET_STATUS message to the Monitor and return the response.
     * This can be used by the Admin Interface to display cluster health.
     */
    public Message getClusterStatus() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(monitorHost, monitorPort), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            Message req = new Message("GET_STATUS", 0, "");
            out.write(serializer.serialize(req));
            out.flush();

            byte[] buf = new byte[8192];
            int n = in.read(buf);
            if (n > 0) {
                byte[] resp = new byte[n];
                System.arraycopy(buf, 0, resp, 0, n);
                return serializer.deserialize(resp);
            }
        } catch (Exception e) {
            log("Failed to get cluster status: " + e.getMessage());
        }
        return null;
    }

    /**
     * Ask the Monitor to trigger a manual failover (admin action).
     */
    public Message triggerManualFailover() {
        log("Requesting manual failover from monitor...");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(monitorHost, monitorPort), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            Message req = new Message("MANUAL_FAILOVER", 0, "admin-request");
            out.write(serializer.serialize(req));
            out.flush();

            byte[] buf = new byte[4096];
            int n = in.read(buf);
            if (n > 0) {
                byte[] resp = new byte[n];
                System.arraycopy(buf, 0, resp, 0, n);
                Message response = serializer.deserialize(resp);
                log("Failover response: " + (response != null ? response.getPayload() : "null"));
                connected = false; // primary will change
                return response;
            }
        } catch (Exception e) {
            log("Manual failover request failed: " + e.getMessage());
        }
        return null;
    }

    // ────────────────────────────────────────────────────────────────────
    // Logging
    // ────────────────────────────────────────────────────────────────────

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String entry = "[" + timestamp + "] [CLIENT] " + message;
        eventLog.add(entry);
        System.out.println(entry);
        if (logListener != null) {
            logListener.accept(entry);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────────────────

    public String getMonitorHost()  { return monitorHost; }
    public int    getMonitorPort()  { return monitorPort; }
    public String getPrimaryHost()  { return primaryHost; }
    public int    getPrimaryPort()  { return primaryPort; }
    public boolean isConnected()    { return connected; }
    public List<String> getEventLog() { return new ArrayList<>(eventLog); }

    /**
     * Register an optional listener that receives every log entry as it is created.
     * Used by the Admin GUI to stream logs in real time.
     */
    public void setLogListener(Consumer<String> listener) {
        this.logListener = listener;
    }

    // ────────────────────────────────────────────────────────────────────
    // CLI entry-point (standalone usage)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Run the client from the command line.
     * Usage:  java mini_project_01.Client <monitorHost> <monitorPort>
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java mini_project_01.Client <monitorHost> <monitorPort>");
            System.exit(1);
        }

        String host = args[0];
        int port    = Integer.parseInt(args[1]);

        Client client = new Client(host, port);

        // Discover primary
        if (!client.discoverPrimary()) {
            System.out.println("Could not discover primary. Exiting.");
            System.exit(1);
        }

        // Interactive loop
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("\nClient ready.  Type a message to send as a PROCESS request.");
        System.out.println("Commands:  ping | status | failover | logs | quit\n");

        while (true) {
            System.out.print("client> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            switch (line.toLowerCase()) {
                case "quit":
                case "exit":
                    System.out.println("Goodbye.");
                    scanner.close();
                    return;
                case "ping":
                    client.ping();
                    break;
                case "status":
                    Message status = client.getClusterStatus();
                    if (status != null) {
                        System.out.println("Cluster status: " + status.getPayload());
                    } else {
                        System.out.println("Could not retrieve cluster status.");
                    }
                    break;
                case "failover":
                    client.triggerManualFailover();
                    break;
                case "logs":
                    client.getEventLog().forEach(System.out::println);
                    break;
                default:
                    Message resp = client.sendRequest(line);
                    if (resp != null) {
                        System.out.println("→ " + resp.getPayload());
                    } else {
                        System.out.println("No response received.");
                    }
                    break;
            }
        }
    }
}

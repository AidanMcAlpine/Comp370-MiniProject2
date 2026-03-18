package mini_project_01;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PrimaryServer extends ServerProcess {
    private Set<Integer> activeClients; // Track active client connections
    private HeartbeatSender heartbeatSender;
    private String monitorHost;
    private int monitorPort;
    private JsonMessageSerializer serializer;

    public PrimaryServer(int serverId, int port, String monitorHost, int monitorPort) {
        this.serverId = serverId;
        this.port = port;
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.activeClients = ConcurrentHashMap.newKeySet();
        this.serializer = new JsonMessageSerializer();
        this.heartbeatSender = new HeartbeatSender(monitorHost, monitorPort, serverId, this::onHeartbeatAckFail);
    }

    public static PrimaryServer promoteFromBackup(BackupServer backup) {
        Map.Entry<String, Integer> monitorDetails = backup.monitorDetails();
        PrimaryServer me = new PrimaryServer(backup.serverId, backup.port, monitorDetails.getKey(),
                monitorDetails.getValue());
        // TODO: if the primary server needs to store any data, take it from the backup
        // server's replicated version
        return me;
    }

    @Override
    public void start() throws IOException {

        // Start heartbeat sender in a separate thread
        Thread heartbeatThread = new Thread(heartbeatSender);
        heartbeatThread.start();

        System.out.println("PrimaryServer " + serverId + " started on port " + port);

        // Call parent start method to begin accepting connections
        super.start();
    }

    @Override
    public void stop() throws IOException {
        heartbeatSender.stop();
        super.stop();
        System.out.println("PrimaryServer " + serverId + " stopped");
    }

    /**
     * Handle incoming client connections.
     * Read requests from clients and respond accordingly.
     */
    @Override
    protected void handleConnection(Socket clientSocket) {
        try (
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            if (clientSocket.getInetAddress().getHostAddress() == monitorHost
                    && clientSocket.getPort() == monitorPort) {
                // The monitor is sending a message
                byte[] buffer = new byte[4096];
                int bytesRead = in.read(buffer);

                if (bytesRead > 0) {
                    byte[] data = Arrays.copyOf(buffer, bytesRead);
                    Message message = serializer.deserialize(data);

                    if (message != null) {
                        switch (message.getType()) {
                            case "HEARTBEAT_ACK":
                                heartbeatSender.recieveHeartbeatAck();
                                break;
                            default:
                                break;
                        }
                    }
                }
            } else if (heartbeatSender.isActive()) {
                int clientId = clientSocket.getPort(); // Use port as temporary client identifier
                activeClients.add(clientId);

                System.out.println(
                        "Client connected from " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                // Read incoming data
                byte[] buffer = new byte[4096];
                int bytesRead = in.read(buffer);

                if (bytesRead > 0) {
                    byte[] data = Arrays.copyOf(buffer, bytesRead);
                    Message message = serializer.deserialize(data);

                    if (message != null) {
                        handleRequest(message, out);
                    }
                }

                activeClients.remove(clientId);
            } else {
                out.write(serializer.serialize(new Message("PRIMARY_ERROR", 0, "Try reconnecting to monitor")));
            }
        } catch (IOException e) {
            System.err.println("Error handling client connection: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error deserializing message: " + e.getMessage());
        }
    }

    /**
     * Process messages from other servers.
     */
    @Override
    protected void processMessage(Message message) {
        System.out.println("BackupServer processing message - Type: " + message.getType() +
                " from sender: " + message.getSenderId() +
                " payload: " + message.getPayload());
    }

    /**
     * Process and respond to client requests.
     */
    private void handleRequest(Message request, DataOutputStream out) {
        try {
            System.out.println(
                    "Processing request from client " + request.getSenderId() + " - Type: " + request.getType());

            // Create response message
            Message response = new Message(
                    "Response",
                    serverId,
                    "Processed: " + request.getPayload());

            // Serialize and send response
            byte[] responseData = serializer.serialize(response);
            if (responseData != null) {
                out.write(responseData);
                out.flush();
            }

        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
        }
    }

    /**
     * Get the list of currently active clients.
     */
    public Set<Integer> getActiveClients() {
        return new HashSet<>(activeClients);
    }

    private void onHeartbeatAckFail() {
        // TODO: heartbeat ack has failed, what do
        // May have disconnected from monitor, probably shouldn't serve requests
    }
}
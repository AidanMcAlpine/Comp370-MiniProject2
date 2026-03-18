package mini_project_01;

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;

public class Monitor {
    // Server management
    private HashMap<Integer, Map.Entry<String, Integer>> servers;
    private int primaryServerId = -1;
    // Should FailoverManager run in the same process as Monitor? I don't see a
    // reason why not, but the UML seems to imply it shouldn't
    private FailoverManager failoverManager;

    // Heartbeat tracking
    private HashMap<Integer, Instant> lastHeartbeat;
    private int timeoutThreshold;
    private Thread heartbeatWatcherThread;

    // Message listening
    private boolean running;
    private ServerSocket messageListener;
    private Thread messageListenerThread;
    private IMessageSerializer<Message> serializer;
    private int port;
    // private ILogger log; Add once log system is in place

    public Monitor(int timeoutThreshold, int port) {
        this.lastHeartbeat = new HashMap<>();
        this.servers = new HashMap<>();
        this.timeoutThreshold = timeoutThreshold;
        this.running = false;
        this.port = port;
        this.serializer = new JsonMessageSerializer();
        this.failoverManager = new FailoverManager();
    }

    // Ensure that only the address a server connected under can send heartbeats
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

    // (Runs in a thread)
    // Listens for messages (HEARTBEAT, REGISTER) from servers
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

    // Repeatedly checks whether connected servers have stopped sending heartbeats
    // Removes them and performs failover logic when necessary
    void checkHeartbeats() {
        while (running) {
            try {
                Thread.sleep(timeoutThreshold / 2);
                Instant now = Instant.now();
                lastHeartbeat.forEach((serverId, lastBeat) -> {
                    if (Duration.between(lastBeat, now).toMillis() > timeoutThreshold) {
                        // TODO: should we drop the server entirely? That's what this does now
                        // Possible alternatives:
                        // - Send the server a "DISCONNECTED" message and require a reconnect
                        // - Mark the server as offline until it sends another heartbeat
                        servers.remove(serverId);
                        lastHeartbeat.remove(serverId);
                        System.out.println("Server " + serverId + " failed to send heartbeat");
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

    // Starts the server (non-blocking)
    public void start() throws IOException {
        messageListener = new ServerSocket(port);
        messageListenerThread = new Thread(() -> listenForMessages());
        messageListenerThread.start();
        heartbeatWatcherThread = new Thread(() -> checkHeartbeats());
        heartbeatWatcherThread.start();
        running = true;
    }

    // Stops the server
    public void stop() throws IOException {
        messageListener.close();
        running = false;
    }

    // Registers a server
    // Called from listenForMessages, not sure why this is public
    // Actually a lot of these functions don't need to be public
    public void registerServer(int serverId, String address, int port, boolean isPrimary) {
        servers.put(serverId, new AbstractMap.SimpleImmutableEntry<>(address, port));
        recieveHeartbeat(serverId);
        if (isPrimary) {
            primaryServerId = serverId;
        }
    }

    // Updates last recieved heartbeat from a server
    public void recieveHeartbeat(Integer serverId) {
        lastHeartbeat.put(serverId, Instant.now());
    }

    // Activates failover when necessary
    public void triggerFailover() {
        primaryServerId = failoverManager.initiateFailover(servers);
        if (primaryServerId == -1) {
            // Only way this should be able to happen is if there are no available backups
            // Therefore we can just do nothing until one is added (as this means there
            // are no active servers at all)
            return;
        }
        Map.Entry<String, Integer> primaryAddress = servers.get(primaryServerId);
        servers.forEach((Integer serverId, Map.Entry<String, Integer> address) -> {
            if (serverId == primaryServerId) {
                return;
            }
            try (
                    Socket promoteSender = new Socket(address.getKey(), address.getValue());
                    DataOutputStream out = new DataOutputStream(promoteSender.getOutputStream());) {
                out.write(new JsonMessageSerializer().serialize(
                        new Message("NEWPRIMARY", 0, primaryAddress.getKey() + ":" + primaryAddress.getValue())));
            } catch (IOException e) {
                System.err.println("Failed to notify backup server " + serverId + " of new primary");
            } catch (Exception e) {
                System.err.println("Failed to serialize new promotion message (somehow)");
            }
        });
        // TODO: At this point, the monitor is supposed to notify the clients previously
        // connected to the primary that the primary has failed and that rediscovery is
        // needed. How does it do this? The only place where the active connections were
        // known was the (now inactive) primary.
    }
}

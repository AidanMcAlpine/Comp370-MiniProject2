package mini_project_01;

import java.io.*;
import java.net.*;
import java.util.*;

public class BackupServer extends ServerProcess {
    private HeartbeatSender heartbeatSender;
    private String monitorHost;
    private int monitorPort;
    private JsonMessageSerializer serializer;
    
    public BackupServer(int serverId, int port, String monitorHost, int monitorPort) {
        this.serverId = serverId;
        this.port = port;
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.serializer = new JsonMessageSerializer();
        this.heartbeatSender = new HeartbeatSender(monitorHost, monitorPort, serverId, ()->System.out.println("Backup server didn't recieve HEARTBEAT_ACK"));
    }
    
    @Override
    public void start() throws IOException {
    	
        // Start heart beat sender in a separate thread
        Thread heartbeatThread = new Thread(heartbeatSender);
        heartbeatThread.start();
        
        System.out.println("BackupServer " + serverId + " started on port " + port);
        
        // Call parent start method to begin accepting connections
        super.start();
    }
    
    @Override
    public void stop() throws IOException {
        heartbeatSender.stop();
        super.stop();
        System.out.println("BackupServer " + serverId + " stopped");
    }
    
    /**
     * Handle incoming connections.
     */
    @Override
    protected void handleConnection(Socket clientSocket) {
        try (
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())
        ) {
            System.out.println("Connection received from " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
            
            // Read incoming data
            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);
            
            if (bytesRead > 0) {
                byte[] data = Arrays.copyOf(buffer, bytesRead);
                Message message = serializer.deserialize(data);
                
                if (message != null) {
                    System.out.println("BackupServer received message - Type: " + message.getType() + 
                                     " from sender: " + message.getSenderId());
                    processMessage(message);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error handling connection: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error deserializing message: " + e.getMessage());
        }
    }
    
    /**
     * Process messages from other servers or clients.
     */
    @Override
    protected void processMessage(Message message) {
        System.out.println("BackupServer processing message - Type: " + message.getType() + 
                         " from sender: " + message.getSenderId() + 
                         " payload: " + message.getPayload());
    }
    
    /**
     * Monitor the primary server's status. TO BE IMPLEMENTED*****
     */
    public void monitorPrimary() {
        System.out.println("Monitoring primary server");
    }

    public Map.Entry<String, Integer> monitorDetails() {
        return new AbstractMap.SimpleImmutableEntry<>(monitorHost, monitorPort);
    }
    
    /**
     * Promote this backup server to primary role.
     */
    public void promote() throws IOException {
        System.out.println("BackupServer " + serverId + " promoted to Primary role!");

        PrimaryServer selfPrimary = PrimaryServer.promoteFromBackup(this);
        stop();
        selfPrimary.start();
    }
}
package mini_project_01;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Main launcher for the Server Redundancy Management System.
 * 
 * Starts the Monitor, 3 server instances, and the Admin GUI
 * so the entire system can be tested from a single run.
 * 
 * Usage:
 *   java mini_project_01.Launcher
 * 
 * Default ports:
 *   Monitor  → 9000
 *   Server 1 → 9001 (starts as Primary)
 *   Server 2 → 9002 (Backup)
 *   Server 3 → 9003 (Backup)
 */
public class Launcher {

    // ── Configuration ───────────────────────────────────────────────────
    private static final String HOST = "localhost";
    private static final int MONITOR_PORT   = 9000;
    private static final int SERVER1_PORT   = 9001;
    private static final int SERVER2_PORT   = 9002;
    private static final int SERVER3_PORT   = 9003;
    private static final int HEARTBEAT_TIMEOUT_MS = 15000; // 15 seconds

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   SRMS — Server Redundancy Management System    ║");
        System.out.println("║   COMP 370 Mini Project 1                       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // ── Step 1: Start the Monitor ───────────────────────────────
            System.out.println("[MAIN] Starting Monitor on port " + MONITOR_PORT + "...");
            Monitor monitor = new Monitor(HEARTBEAT_TIMEOUT_MS, MONITOR_PORT);
            monitor.start();
            System.out.println("[MAIN] Monitor started successfully.");
            Thread.sleep(1000); // Give it a moment to bind

            // ── Step 2: Register servers directly with the Monitor ──────
            // Register with actual listening ports so the Monitor knows 
            // where to reach each server
            System.out.println("[MAIN] Registering servers with monitor...");
            monitor.registerServer(1, HOST, SERVER1_PORT, true);   // Server 1 = Primary
            System.out.println("[MAIN] Server 1 registered as PRIMARY on port " + SERVER1_PORT);
            monitor.registerServer(2, HOST, SERVER2_PORT, false);  // Server 2 = Backup
            System.out.println("[MAIN] Server 2 registered as BACKUP on port " + SERVER2_PORT);
            monitor.registerServer(3, HOST, SERVER3_PORT, false);  // Server 3 = Backup
            System.out.println("[MAIN] Server 3 registered as BACKUP on port " + SERVER3_PORT);

            // ── Step 3: Start Server 1 as Primary ───────────────────────
            System.out.println("[MAIN] Starting Server 1 (Primary) on port " + SERVER1_PORT + "...");
            PrimaryServer server1 = new PrimaryServer(1, SERVER1_PORT, HOST, MONITOR_PORT);
            Thread s1Thread = new Thread(() -> {
                try {
                    server1.start();
                } catch (IOException e) {
                    System.err.println("[MAIN] Server 1 error: " + e.getMessage());
                }
            });
            s1Thread.setDaemon(true);
            s1Thread.start();
            Thread.sleep(500);

            // ── Step 4: Start Server 2 as Backup ────────────────────────
            System.out.println("[MAIN] Starting Server 2 (Backup) on port " + SERVER2_PORT + "...");
            BackupServer server2 = new BackupServer(2, SERVER2_PORT, HOST, MONITOR_PORT);
            Thread s2Thread = new Thread(() -> {
                try {
                    server2.start();
                } catch (IOException e) {
                    System.err.println("[MAIN] Server 2 error: " + e.getMessage());
                }
            });
            s2Thread.setDaemon(true);
            s2Thread.start();
            Thread.sleep(500);

            // ── Step 5: Start Server 3 as Backup ────────────────────────
            System.out.println("[MAIN] Starting Server 3 (Backup) on port " + SERVER3_PORT + "...");
            BackupServer server3 = new BackupServer(3, SERVER3_PORT, HOST, MONITOR_PORT);
            Thread s3Thread = new Thread(() -> {
                try {
                    server3.start();
                } catch (IOException e) {
                    System.err.println("[MAIN] Server 3 error: " + e.getMessage());
                }
            });
            s3Thread.setDaemon(true);
            s3Thread.start();
            Thread.sleep(500);

            System.out.println();
            System.out.println("[MAIN] All components started!");
            System.out.println("[MAIN] Monitor: " + HOST + ":" + MONITOR_PORT);
            System.out.println("[MAIN] Server 1 (Primary): " + HOST + ":" + SERVER1_PORT);
            System.out.println("[MAIN] Server 2 (Backup):   " + HOST + ":" + SERVER2_PORT);
            System.out.println("[MAIN] Server 3 (Backup):   " + HOST + ":" + SERVER3_PORT);
            System.out.println();

            // ── Step 6: Launch Admin GUI ────────────────────────────────
            System.out.println("[MAIN] Launching Admin Dashboard...");
            javax.swing.SwingUtilities.invokeLater(() -> {
                AdminInterface admin = new AdminInterface(HOST, MONITOR_PORT);
                admin.setVisible(true);
            });

            // ── Keep running + show CLI menu ────────────────────────────
            System.out.println();
            System.out.println("════════════════════════════════════════════");
            System.out.println("  System is running. Commands:");
            System.out.println("    kill1  — Kill Server 1 (simulate crash)");
            System.out.println("    kill2  — Kill Server 2");
            System.out.println("    kill3  — Kill Server 3");
            System.out.println("    status — Show running servers");
            System.out.println("    quit   — Shut down everything");
            System.out.println("════════════════════════════════════════════");
            System.out.println();

            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            while (running) {
                System.out.print("srms> ");
                if (!scanner.hasNextLine()) break;
                String cmd = scanner.nextLine().trim().toLowerCase();

                switch (cmd) {
                    case "kill1":
                        System.out.println("[MAIN] Killing Server 1...");
                        try { server1.stop(); } catch (Exception e) { /* expected */ }
                        System.out.println("[MAIN] Server 1 stopped. Monitor should detect failure.");
                        break;

                    case "kill2":
                        System.out.println("[MAIN] Killing Server 2...");
                        try { server2.stop(); } catch (Exception e) { /* expected */ }
                        System.out.println("[MAIN] Server 2 stopped.");
                        break;

                    case "kill3":
                        System.out.println("[MAIN] Killing Server 3...");
                        try { server3.stop(); } catch (Exception e) { /* expected */ }
                        System.out.println("[MAIN] Server 3 stopped.");
                        break;

                    case "status":
                        System.out.println("[MAIN] Checking server sockets...");
                        checkPort("Server 1", SERVER1_PORT);
                        checkPort("Server 2", SERVER2_PORT);
                        checkPort("Server 3", SERVER3_PORT);
                        checkPort("Monitor",  MONITOR_PORT);
                        break;

                    case "quit":
                    case "exit":
                        System.out.println("[MAIN] Shutting down...");
                        try { server1.stop(); } catch (Exception ignored) {}
                        try { server2.stop(); } catch (Exception ignored) {}
                        try { server3.stop(); } catch (Exception ignored) {}
                        try { monitor.stop(); } catch (Exception ignored) {}
                        running = false;
                        break;

                    case "":
                        break;

                    default:
                        System.out.println("Unknown command: " + cmd);
                        break;
                }
            }

            scanner.close();
            System.out.println("[MAIN] Goodbye.");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("[MAIN] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Quick check if a port is reachable (for the status command).
     */
    private static void checkPort(String name, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(HOST, port), 1000);
            System.out.println("  " + name + " (:" + port + ") → ALIVE");
        } catch (Exception e) {
            System.out.println("  " + name + " (:" + port + ") → DOWN");
        }
    }
}
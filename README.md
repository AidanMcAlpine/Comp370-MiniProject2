# Server Redundancy Management System (SRMS)

**COMP 370 — Software Engineering | Mini Project 2**

## Overview

This project refactors the Server Redundancy Management System from Mini Project 1 by applying three design patterns: **Singleton**, **Abstraction–Occurrence**, and **Observer**. The goal is to improve maintainability, extensibility, and reduce coupling while preserving all original functionality (failover, heartbeat monitoring, client request handling).

## Design Patterns Applied

- **Singleton** — Monitor class enforces a single instance via `getInstance()`, preventing conflicting monitor states.
- **Abstraction–Occurrence** — `ServerType` abstract class holds shared fields (`monitorHost`, `monitorPort`, `serializer`, `heartbeatSender`), eliminating duplication between `PrimaryServer` and `BackupServer`.
- **Observer** — Monitor acts as the Subject, notifying registered Observers (`ServerNotifier`, `EventLogger`) of events like failover, server registration, and heartbeat timeout. This replaces the direct socket calls that were previously hard-coded inside Monitor.

## Project Structure

```
Comp370-MiniProject2/
├── original/                  # Mini Project 1 source (unchanged)
│   └── src/mini_project_01/
├── refactored/                # Refactored source with all 3 patterns
│   └── src/mini_project_01/
│       ├── Observer.java          # Observer interface
│       ├── Monitor.java           # Singleton + Subject
│       ├── ServerNotifier.java    # Observer — notifies backups on failover
│       ├── EventLogger.java       # Observer — logs all events
│       ├── ServerType.java        # Abstraction–Occurrence base class
│       ├── PrimaryServer.java     # Occurrence
│       ├── BackupServer.java      # Occurrence
│       ├── FailoverManager.java   # Election algorithm
│       ├── Launcher.java          # Entry point — wires everything up
│       ├── Client.java            # Client with reconnection logic
│       ├── AdminInterface.java    # Swing GUI dashboard
│       └── ...
├── docs/                      # UML diagrams (original + refactored)
└── report/                    # Final report
```

## How to Run

```bash
git clone https://github.com/AidanMcAlpine/Comp370-MiniProject2.git
cd Comp370-MiniProject2/refactored
mvn clean package
java -cp target/classes mini_project_01.Launcher
```

This starts:
- **Monitor** on port 9000 (Singleton)
- **Server 1** (Primary) on port 9001
- **Server 2** (Backup) on port 9002
- **Server 3** (Backup) on port 9003
- **ServerNotifier** and **EventLogger** observers registered with Monitor
- **Admin Dashboard** GUI

### Terminal Commands

| Command | Description |
|---------|-------------|
| `kill1` | Simulate Server 1 crash |
| `kill2` | Simulate Server 2 crash |
| `kill3` | Simulate Server 3 crash |
| `status` | Check which servers are alive |
| `quit` | Shut down all components |

### Troubleshooting

If you see `Failed making field 'mini_project_01.Message#type' accessible`, either add `opens mini_project_01 to com.google.gson;` to `module-info.java` or delete `module-info.java` entirely.

## Team

| Name | Role |
|------|------|
| Aidan McAlpine | System Architect & UML Lead (Team Lead) |
| Ethan Sena | Abstraction–Occurrence Pattern Developer |
| Nathan Strong | Singleton Pattern Developer |
| Gurjasraj Singh | Observer Pattern Developer |
| Harmitha | Testing and Documentation |

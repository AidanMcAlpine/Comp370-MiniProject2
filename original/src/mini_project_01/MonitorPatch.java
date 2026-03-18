package mini_project_01;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  MONITOR PATCH — Required changes for Admin Interface (Step 7)  │
 * └──────────────────────────────────────────────────────────────────┘
 * 
 * The Admin Interface (AdminInterface.java) sends two new message types
 * to the Monitor that the current Monitor code does not handle:
 * 
 *   1. GET_STATUS  — returns cluster health info for the dashboard
 *   2. MANUAL_FAILOVER — triggers a failover on demand
 * 
 * Your team's Reliability & Failover Specialist (Role 3) needs to add
 * these two cases to Monitor.listenForMessages() inside the switch block.
 * 
 * ────────────────────────────────────────────────────────────────────
 * ALSO: There is a missing `break;` after the GET_PRIMARY case which
 * causes it to fall through to the default. That should be fixed too.
 * ────────────────────────────────────────────────────────────────────
 *
 * Below is the exact code to add inside the switch(message.getType())
 * block in Monitor.listenForMessages():
 *
 * <pre>
 *     // ── FIX: add break to GET_PRIMARY ────────────────────────────
 *     case "GET_PRIMARY":
 *         Map.Entry<String, Integer> primaryAddress = servers.get(primaryServerId);
 *         if (primaryAddress != null) {
 *             out.write(serializer.serialize(new Message("CURRENT_PRIMARY", 0,
 *                     primaryAddress.getKey() + ":" + primaryAddress.getValue())));
 *         } else {
 *             out.write(serializer.serialize(new Message("NO_CURRENT_PRIMARY", 0, "")));
 *         }
 *         break;   // ← THIS WAS MISSING
 *
 *     // ── NEW: GET_STATUS for Admin Dashboard ──────────────────────
 *     case "GET_STATUS":
 *         StringBuilder sb = new StringBuilder();
 *         servers.forEach((sid, addr) -> {
 *             String role = (sid == primaryServerId) ? "PRIMARY" : "BACKUP";
 *             Instant last = lastHeartbeat.get(sid);
 *             long ago = (last != null)
 *                     ? Duration.between(last, Instant.now()).toMillis()
 *                     : -1;
 *             String status = (ago >= 0 && ago < timeoutThreshold) ? "ALIVE" : "DEAD";
 *             // Format: id,host:port,role,status,lastHeartbeatAgoMs
 *             sb.append(sid).append(",")
 *               .append(addr.getKey()).append(":").append(addr.getValue()).append(",")
 *               .append(role).append(",")
 *               .append(status).append(",")
 *               .append(ago).append("ms ago");
 *             sb.append(";");
 *         });
 *         out.write(serializer.serialize(
 *                 new Message("STATUS_RESPONSE", 0, sb.toString())));
 *         break;
 *
 *     // ── NEW: MANUAL_FAILOVER for Admin actions ───────────────────
 *     case "MANUAL_FAILOVER":
 *         System.out.println("Admin requested manual failover.");
 *         primaryServerId = -1;   // force failover
 *         triggerFailover();
 *         Map.Entry<String, Integer> newPrimary = servers.get(primaryServerId);
 *         if (newPrimary != null) {
 *             out.write(serializer.serialize(new Message("FAILOVER_OK", 0,
 *                     "New primary: " + newPrimary.getKey() + ":" + newPrimary.getValue())));
 *         } else {
 *             out.write(serializer.serialize(new Message("FAILOVER_FAIL", 0,
 *                     "No backup available to promote.")));
 *         }
 *         break;
 * </pre>
 *
 * After adding these, rebuild and the Admin Dashboard will be fully functional.
 */
public class MonitorPatch {
    // This file is documentation only — no executable code.
    // Share this with your teammate who owns Monitor.java.
}

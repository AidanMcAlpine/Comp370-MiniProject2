package mini_project_01;

public abstract class ServerType extends ServerProcess {

	protected String monitorHost;
	protected int monitorPort;
	protected JsonMessageSerializer serializer;
	protected HeartbeatSender heartbeatSender;
	
	public ServerType(int serverId, int port, String monitorHost, int monitorPort) {
		this.serverId = serverId;
        this.port = port;
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.serializer = new JsonMessageSerializer();
    }
	
	public java.util.Map.Entry<String, Integer> monitorDetails() {
        return new java.util.AbstractMap.SimpleImmutableEntry<>(monitorHost, monitorPort);
    }
}

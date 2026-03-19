package mini_project_01;
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class ServerProcess {
	protected int serverId; // Unique ID of server.
	protected int port; // Port the server is listening on.
	protected ServerSocket serverSocket; // Listening socket that listens for client connections.
	protected boolean running;
	protected ExecutorService threadPool = Executors.newCachedThreadPool(); // ExecutorService object used to manage multi-threaded client connection.
	
	
	public void start() throws IOException {
		serverSocket = new ServerSocket(port); // Open server socket on some port.
		running = true;
		
		while (running) {
			try {
				Socket clientSocket = serverSocket.accept(); // Thread paused/blocked until a client connects.
				threadPool.submit(() -> handleConnection(clientSocket)); // Once client connects hand them off to the thread pool manager.
			} catch (IOException e){
				if (running) e.printStackTrace();
			}
		}
	}
	
	public void stop() throws IOException {
		running = false;
		serverSocket.close();
		threadPool.shutdownNow();
	}
	
	public int getServerId() {
		return serverId;
	}

	public int getPort() {
		return port;
	}

	protected abstract void handleConnection(Socket clientSocket); // Handle client connections.
	protected abstract void processMessage(Message message); // Process messages from other servers.
	
}
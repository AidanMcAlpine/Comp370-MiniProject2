package mini_project_01;

import java.nio.charset.StandardCharsets;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonIOException;
import com.google.gson.Gson;

public class JsonMessageSerializer implements IMessageSerializer<Message> {
	
	private final Gson gson = new Gson();
	
	
	// Serialize a Message object into JSON object, which is then converted into bytes to be sent over TCP.
	@Override
	public byte[] serialize(Message obj) throws Exception {
		try {
			String json = gson.toJson(obj);
			return json.getBytes(StandardCharsets.UTF_8);
		} catch (JsonIOException e) {
			System.err.println("Serializaton failed: " + e.getMessage());
			return null;
		}
	}

	// De-serialize bytes into a JSON object, then into a Message object. 
	@Override
	public Message deserialize(byte[] data) throws Exception {
		try {
			String json = new String(data, StandardCharsets.UTF_8);
			return gson.fromJson(json, Message.class);
		} catch(JsonSyntaxException | JsonIOException e) {
			System.err.println("Deserialization failed: " + e.getMessage());
			return null;
		}
	}

	
}

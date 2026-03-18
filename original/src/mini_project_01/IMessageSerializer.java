package mini_project_01;

/*
 * Interface to be used for inter-process message serialization and deserialization. 
 */

public interface IMessageSerializer <T>{

	byte[] serialize (T message) throws Exception; // Used by servers to send some message.
	
	T deserialize (byte[] data) throws Exception; // Used by servers to read some message.
	
}
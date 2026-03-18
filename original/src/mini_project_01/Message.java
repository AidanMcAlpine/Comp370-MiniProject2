package mini_project_01;

/*
 *  General message wrapper.
 */

public class Message {
	
	private String type; // What kind of message is being sent. e.g. a heart beat, or server promotion.
	private int senderId; // Who sent it.
	private String payload; // Actual string data.
	
	public Message() {}
	
	public Message(String type, int senderId, String payload) {
		this.type = type;
		this.senderId = senderId;
		this.payload = payload;
	}
	
	public String getType() {
        return type;
    }

    public int getSenderId() {
        return senderId;
    }

    public String getPayload() {
        return payload;
    }
}
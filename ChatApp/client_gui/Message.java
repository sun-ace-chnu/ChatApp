package client_gui;
public class Message {
    public String type;     // LOGIN, CHAT, FRIEND_LIST, ...
    public String from;
    public String to;
    public String content;
    public String data;
    public long timestamp;

    public Message() {}

    public static Message of(String type, String from, String to, String content) {
        Message m = new Message();
        m.type = type;
        m.from = from;
        m.to = to;
        m.content = content;
        m.timestamp = System.currentTimeMillis();
        return m;
    }
}

package server;
public class Message {
    public String type;     // LOGIN, LOGIN_OK, LOGIN_FAIL, CHAT, FRIEND_LIST, FRIEND_ADD, ...
    public String from;
    public String to;
    public String content;
    public String data;     // 扩展字段：好友列表/状态列表等用字符串承载（比如用 ; 分隔）
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

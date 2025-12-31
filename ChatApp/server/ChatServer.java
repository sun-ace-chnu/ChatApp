package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    public final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    public final FriendStore friendStore = new FriendStore("friends_db.txt");
    public final HistoryStore historyStore = new HistoryStore();

    public boolean isAuthed(ClientHandler h) {
        return h != null && h.getUsername() != null && online.get(h.getUsername()) == h;
    }

    public synchronized void onLoginSuccess(ClientHandler h) {
        // 同账号重复登录：踢掉旧连接
        String u = h.getUsername();
        ClientHandler old = online.put(u, h);
        if (old != null && old != h) {
            old.send(Message.of("KICK", "server", u, "账号在别处登录，你已下线"));
            kick(old);
        }
        // 广播状态变化（可选：只通知好友；这里简化为不做好友过滤）
        broadcastStatus(u, "ONLINE");
    }

    public synchronized void kick(ClientHandler h) {
        if (h == null) return;
        String u = h.getUsername();
        if (u != null) {
            ClientHandler cur = online.get(u);
            if (cur == h) {
                online.remove(u);
                broadcastStatus(u, "OFFLINE");
            }
        }
        try { h.interrupt(); } catch (Exception ignore) {}
    }

    private void broadcastStatus(String user, String status) {
        Message m = new Message();
        m.type = "STATUS_PUSH";
        m.from = "server";
        m.data = user + "=" + status;
        m.timestamp = System.currentTimeMillis();

        for (ClientHandler ch : online.values()) {
            ch.send(m);
        }
    }

    public void start(int port) throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("ChatServer started on port " + port);
            while (true) {
                Socket s = ss.accept();
                ClientHandler h = new ClientHandler(s, this);
                h.start();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 9000;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        new ChatServer().start(port);
    }
}

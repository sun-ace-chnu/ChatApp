package server;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final ChatServer server;
    private BufferedReader in;
    private BufferedWriter out;
    private String username = null;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() { return username; }

    public void send(Message m) {
        try {
            String line = SimpleJson.toJson(m);
            synchronized (out) {
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (IOException e) {
            // 发送失败就当掉线处理
            server.kick(this);
        }
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            String line;
            while ((line = in.readLine()) != null) {
                Message m = SimpleJson.fromJson(line);

                switch (m.type) {
                    case "LOGIN" -> handleLogin(m);
                    case "CHAT" -> handleChat(m);
                    case "FRIEND_LIST" -> handleFriendList();
                    case "FRIEND_ADD" -> handleFriendAdd(m);
                    case "FRIEND_REMARK" -> handleFriendRemark(m);
                    case "FRIEND_DEL" -> handleFriendDel(m);
                    case "STATUS_QUERY" -> handleStatusQuery(); // 主动刷新在线状态
                    case "HIS_LIST" -> handleHistoryList();
                    case "HIS_READ" -> handleHistoryRead(m);
                    case "HIS_DEL" -> handleHistoryDelete(m);
                    default -> {
                        // ignore
                    }
                }
            }
        } catch (IOException e) {
            // 客户端断开
        } finally {
            server.kick(this);
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    private void handleLogin(Message m) {
        // content 格式：password
        String user = m.from;
        String pass = m.content;

        if (UserStore.validate(user, pass)) {
            this.username = user;
            server.onLoginSuccess(this);

            send(Message.of("LOGIN_OK", "server", user, "登录成功"));
            // 登录成功后：下发好友列表、状态
            handleFriendList();
            handleStatusQuery();
        } else {
            send(Message.of("LOGIN_FAIL", "server", user, "账号或密码错误"));
        }
    }

    private void handleChat(Message m) {
        if (!server.isAuthed(this)) return;

        // 记录聊天
        server.historyStore.appendChat(m.from, m.to, m.content, m.timestamp);

        // 转发给对方（在线则发，不在线则只存记录）
        ClientHandler peer = server.online.get(m.to);
        if (peer != null) {
            peer.send(m);
        } else {
            // 告知发送者对方离线（可选）
            Message back = Message.of("CHAT_OFFLINE_SAVED", "server", m.from, "对方离线，已保存到聊天记录");
            send(back);
        }
    }

    private void handleFriendList() {
        if (!server.isAuthed(this)) return;
        List<String> fs = server.friendStore.listFriends(username);
        Message res = new Message();
        res.type = "FRIEND_LIST_RES";
        res.from = "server";
        res.to = username;
        // data 用 ; 分隔好友项（含备注 bob|室友）
        res.data = String.join(";", fs);
        res.timestamp = System.currentTimeMillis();
        send(res);
    }

    private void handleFriendAdd(Message m) {
        if (!server.isAuthed(this)) return;

        String friendAccount = m.content.trim();
        if (!UserStore.allUsers().contains(friendAccount)) {
            send(Message.of("FRIEND_OP_FAIL", "server", username, "好友账号不存在"));
            return;
        }

        // ⭐ 双向添加
        server.friendStore.addFriend(username, friendAccount);
        server.friendStore.addFriend(friendAccount, username);

        // 通知自己
        send(Message.of("FRIEND_OP_OK", "server", username, "添加好友成功"));
        handleFriendList();
        handleStatusQuery();

        // ⭐ 如果对方在线，也刷新对方好友列表 & 状态
        ClientHandler peer = server.online.get(friendAccount);
        if (peer != null) {
            peer.send(Message.of("FRIEND_LIST", "server", friendAccount, ""));
            peer.send(Message.of("STATUS_QUERY", "server", friendAccount, ""));
            peer.send(Message.of("SYS_NOTICE", "server", friendAccount,
                    username + " 已将你添加为好友"));
        }
    }


    private void handleFriendRemark(Message m) {
        if (!server.isAuthed(this)) return;
        // content 格式：friendAccount|newRemark
        String[] parts = m.content.split("\\|", 2);
        if (parts.length < 2) {
            send(Message.of("FRIEND_OP_FAIL", "server", username, "格式错误：friend|remark"));
            return;
        }
        server.friendStore.renameFriendRemark(username, parts[0].trim(), parts[1].trim());
        send(Message.of("FRIEND_OP_OK", "server", username, "修改备注成功"));
        handleFriendList();
    }

    private void handleFriendDel(Message m) {
        if (!server.isAuthed(this)) return;
        String friendAccount = m.content.trim();
        server.friendStore.deleteFriend(username, friendAccount);
        send(Message.of("FRIEND_OP_OK", "server", username, "删除好友成功"));
        handleFriendList();
        handleStatusQuery();
    }

    private void handleStatusQuery() {
        if (!server.isAuthed(this)) return;
        // 对好友列表中的好友返回在线/离线
        List<String> fs = server.friendStore.listFriends(username);
        List<String> statusPairs = new ArrayList<>();
        for (String item : fs) {
            String acc = item.split("\\|", 2)[0];
            boolean online = server.online.containsKey(acc);
            statusPairs.add(acc + "=" + (online ? "ONLINE" : "OFFLINE"));
        }
        Message res = new Message();
        res.type = "STATUS_RES";
        res.from = "server";
        res.to = username;
        res.data = String.join(";", statusPairs);
        res.timestamp = System.currentTimeMillis();
        send(res);
    }

    private void handleHistoryList() {
        if (!server.isAuthed(this)) return;
        List<String> files = server.historyStore.listUserHistories(username);
        Message res = new Message();
        res.type = "HIS_LIST_RES";
        res.from = "server";
        res.to = username;
        res.data = String.join(";", files);
        res.timestamp = System.currentTimeMillis();
        send(res);
    }

    private void handleHistoryRead(Message m) {
        if (!server.isAuthed(this)) return;
        // content：对方账号
        String peer = m.content.trim();
        String text = server.historyStore.readHistory(username, peer);

        Message res = new Message();
        res.type = "HIS_READ_RES";
        res.from = "server";
        res.to = username;
        res.content = text;
        res.timestamp = System.currentTimeMillis();
        send(res);
    }

    private void handleHistoryDelete(Message m) {
        if (!server.isAuthed(this)) return;
        String peer = m.content.trim();
        boolean ok = server.historyStore.deleteHistory(username, peer);
        send(Message.of(ok ? "HIS_DEL_OK" : "HIS_DEL_FAIL", "server", username, ok ? "删除成功" : "删除失败"));
    }
}

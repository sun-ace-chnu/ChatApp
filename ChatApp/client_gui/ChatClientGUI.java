package client_gui;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class ChatClientGUI extends JFrame {
    // 网络
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private ClientReceiver receiver;

    // 登录用户
    private String me = null;

    // 好友与状态
    private final DefaultListModel<FriendItem> friendModel = new DefaultListModel<>();
    private final Map<String, String> statusMap = new HashMap<>(); // acc -> ONLINE/OFFLINE

    // UI组件
    private final JLabel topInfo = new JLabel("未登录");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendBtn = new JButton("发送");

    private final JButton addFriendBtn = new JButton("添加好友");
    private final JButton remarkBtn = new JButton("改备注");
    private final JButton delFriendBtn = new JButton("删除好友");
    private final JButton refreshStatusBtn = new JButton("刷新状态");
    private final JButton historyListBtn = new JButton("列出记录文件");
    private final JButton historyOpenBtn = new JButton("打开记录");
    private final JButton historyDelBtn = new JButton("删除记录");

    private final JList<FriendItem> friendList = new JList<>(friendModel);

    // 聊天记录窗口（可复用）
    private HistoryFrame historyFrame;

    // 当前聊天对象
    private String currentPeer = null;

    public ChatClientGUI(String host, int port) throws Exception {
        // 先连接服务器，再弹登录窗
        connect(host, port);
        buildUI();
        showLogin();
    }

    private void connect(String host, int port) throws Exception {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        receiver = new ClientReceiver(in, this);
        receiver.start();
    }

    private void buildUI() {
        setTitle("即时聊天系统 - Swing客户端");
        setSize(980, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // 顶部
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        topInfo.setFont(topInfo.getFont().deriveFont(Font.BOLD, 14f));
        top.add(topInfo, BorderLayout.WEST);

        // 左侧好友列表
        friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        friendList.setCellRenderer(new FriendCellRenderer());
        friendList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                FriendItem it = friendList.getSelectedValue();
                if (it != null) {
                    currentPeer = it.account;
                    appendSys("当前聊天对象：" + currentPeer);
                }
            }
        });

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBorder(BorderFactory.createTitledBorder("好友列表"));
        left.add(new JScrollPane(friendList), BorderLayout.CENTER);

        JPanel leftBtns = new JPanel(new GridLayout(4, 1, 6, 6));
        leftBtns.add(addFriendBtn);
        leftBtns.add(remarkBtn);
        leftBtns.add(delFriendBtn);
        leftBtns.add(refreshStatusBtn);
        left.add(leftBtns, BorderLayout.SOUTH);

        // 右侧聊天区
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(BorderFactory.createTitledBorder("聊天窗口"));
        right.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        right.add(inputPanel, BorderLayout.SOUTH);

        // 底部记录管理
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bottom.setBorder(BorderFactory.createTitledBorder("聊天记录管理"));
        bottom.add(historyListBtn);
        bottom.add(historyOpenBtn);
        bottom.add(historyDelBtn);

        // 主布局
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(320);

        Container cp = getContentPane();
        cp.setLayout(new BorderLayout());
        cp.add(top, BorderLayout.NORTH);
        cp.add(split, BorderLayout.CENTER);
        cp.add(bottom, BorderLayout.SOUTH);

        // 事件绑定
        sendBtn.addActionListener(e -> doSendChat());
        inputField.addActionListener(e -> doSendChat());

        addFriendBtn.addActionListener(e -> doAddFriend());
        remarkBtn.addActionListener(e -> doRemarkFriend());
        delFriendBtn.addActionListener(e -> doDeleteFriend());
        refreshStatusBtn.addActionListener(e -> sendSafe(Message.of("STATUS_QUERY", me, "server", "")));

        historyListBtn.addActionListener(e -> sendSafe(Message.of("HIS_LIST", me, "server", "")));
        historyOpenBtn.addActionListener(e -> doOpenHistory());
        historyDelBtn.addActionListener(e -> doDeleteHistory());

        setVisible(true);
    }

    private void showLogin() {
        LoginFrame lf = new LoginFrame((u, p) -> {
            this.me = u;
            sendSafe(Message.of("LOGIN", u, "server", p));
        });
        lf.setVisible(true);

        // 登录成功后会收到 LOGIN_OK，再关闭登录窗（在 onMessage 里处理）
        // 这里让登录窗始终浮在前
        lf.setAlwaysOnTop(true);
        lf.toFront();
        lf.requestFocus();
    }

    private void doSendChat() {
        if (me == null) {
            JOptionPane.showMessageDialog(this, "请先登录。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentPeer == null || currentPeer.isBlank()) {
            JOptionPane.showMessageDialog(this, "请先在左侧选择一个好友作为聊天对象。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        Message m = Message.of("CHAT", me, currentPeer, text);
        sendSafe(m);
        appendMe(text);
        inputField.setText("");
    }

    private void doAddFriend() {
        if (me == null) return;
        String f = JOptionPane.showInputDialog(this, "输入要添加的好友账号：", "添加好友", JOptionPane.PLAIN_MESSAGE);
        if (f == null) return;
        f = f.trim();
        if (f.isEmpty()) return;
        sendSafe(Message.of("FRIEND_ADD", me, "server", f));
    }

    private void doRemarkFriend() {
        if (me == null) return;
        FriendItem it = friendList.getSelectedValue();
        if (it == null) {
            JOptionPane.showMessageDialog(this, "请先选择要修改备注的好友。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String r = JOptionPane.showInputDialog(this, "输入新备注（可为空）：", "修改备注 - " + it.account, JOptionPane.PLAIN_MESSAGE);
        if (r == null) return;
        r = r.trim();
        sendSafe(Message.of("FRIEND_REMARK", me, "server", it.account + "|" + r));
    }

    private void doDeleteFriend() {
        if (me == null) return;
        FriendItem it = friendList.getSelectedValue();
        if (it == null) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的好友。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "确定删除好友：" + it.account + " ?", "确认删除",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            sendSafe(Message.of("FRIEND_DEL", me, "server", it.account));
        }
    }

    private void doOpenHistory() {
        if (me == null) return;

        String peer = null;
        FriendItem it = friendList.getSelectedValue();
        if (it != null) peer = it.account;

        if (peer == null) {
            peer = JOptionPane.showInputDialog(this, "输入对方账号（读取你和TA的聊天记录）：", "打开聊天记录", JOptionPane.PLAIN_MESSAGE);
            if (peer == null) return;
            peer = peer.trim();
        }
        if (peer.isEmpty()) return;

        // 打开窗口（2️⃣B）
        if (historyFrame == null) {
            historyFrame = new HistoryFrame("聊天记录 - " + me + " ↔ " + peer);
        } else {
            historyFrame.setTitle("聊天记录 - " + me + " ↔ " + peer);
        }
        historyFrame.setVisible(true);
        historyFrame.toFront();

        sendSafe(Message.of("HIS_READ", me, "server", peer));
    }

    private void doDeleteHistory() {
        if (me == null) return;
        String peer = JOptionPane.showInputDialog(this, "输入对方账号（删除你和TA的聊天记录）：", "删除聊天记录", JOptionPane.PLAIN_MESSAGE);
        if (peer == null) return;
        peer = peer.trim();
        if (peer.isEmpty()) return;

        int ok = JOptionPane.showConfirmDialog(this, "确定删除你与 " + peer + " 的聊天记录？", "确认删除",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            sendSafe(Message.of("HIS_DEL", me, "server", peer));
        }
    }

    private void sendSafe(Message m) {
        if (m == null) return;
        try {
            String line = SimpleJson.toJson(m);
            synchronized (out) {
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (Exception e) {
            onDisconnected();
        }
    }

    // ========== 服务端消息回调 ==========
    public void onMessage(Message m) {
        // Swing 线程安全：切回 EDT
        SwingUtilities.invokeLater(() -> handleMessageOnEDT(m));
    }

    private void handleMessageOnEDT(Message m) {
        switch (m.type) {
            case "LOGIN_OK" -> {
                topInfo.setText("当前用户：" + me + "    状态：在线");
                appendSys("登录成功。");
                // 登录成功后主动拉取好友列表、状态（服务端一般也会推一次，这里双保险）
                sendSafe(Message.of("FRIEND_LIST", me, "server", ""));
                sendSafe(Message.of("STATUS_QUERY", me, "server", ""));
                // 关闭所有登录窗口
                closeLoginFrames();
            }
            case "LOGIN_FAIL" -> JOptionPane.showMessageDialog(this, m.content, "登录失败", JOptionPane.ERROR_MESSAGE);

            case "KICK" -> {
                JOptionPane.showMessageDialog(this, m.content, "下线通知", JOptionPane.WARNING_MESSAGE);
                System.exit(0);
            }

            case "CHAT" -> {
                // 收到消息
                appendPeer(m.from, m.content);
            }

            case "CHAT_OFFLINE_SAVED" -> appendSys(m.content);

            case "FRIEND_LIST_RES" -> {
                rebuildFriendList(m.data);
                appendSys("好友列表已更新。");
            }

            case "FRIEND_OP_OK" -> appendSys(m.content);
            case "FRIEND_OP_FAIL" -> JOptionPane.showMessageDialog(this, m.content, "好友操作失败", JOptionPane.ERROR_MESSAGE);

            case "STATUS_RES" -> {
                applyStatusPairs(m.data);
                friendList.repaint();
                appendSys("在线状态已刷新。");
            }

            case "STATUS_PUSH" -> {
                applyStatusPairs(m.data);
                friendList.repaint();
                appendSys("状态变化：" + m.data);
            }

            case "HIS_LIST_RES" -> {
                List<String> files = parseList(m.data);
                if (files.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "暂无聊天记录文件。", "聊天记录", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JTextArea a = new JTextArea(String.join("\n", files));
                    a.setEditable(false);
                    a.setLineWrap(true);
                    JOptionPane.showMessageDialog(this, new JScrollPane(a), "聊天记录文件列表", JOptionPane.INFORMATION_MESSAGE);
                }
            }

            case "HIS_READ_RES" -> {
                if (historyFrame == null) {
                    historyFrame = new HistoryFrame("聊天记录");
                }
                historyFrame.setText(m.content);
                historyFrame.setVisible(true);
                historyFrame.toFront();
            }

            case "HIS_DEL_OK" -> JOptionPane.showMessageDialog(this, m.content, "删除成功", JOptionPane.INFORMATION_MESSAGE);
            case "HIS_DEL_FAIL" -> JOptionPane.showMessageDialog(this, m.content, "删除失败", JOptionPane.ERROR_MESSAGE);

            default -> {
                // ignore
            }
        }
    }

    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "与服务器连接断开。", "网络错误", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        });
    }

    private void closeLoginFrames() {
        for (Frame f : Frame.getFrames()) {
            if (f instanceof LoginFrame) {
                f.dispose();
            }
        }
    }

    // ========== 好友/状态处理 ==========
    private void rebuildFriendList(String data) {
        friendModel.clear();
        List<String> items = parseList(data); // "bob|室友; cathy"
        for (String item : items) {
            String[] parts = item.split("\\|", 2);
            String acc = parts[0].trim();
            if (acc.isEmpty()) continue;
            String remark = parts.length > 1 ? parts[1].trim() : "";
            friendModel.addElement(new FriendItem(acc, remark));
        }
        if (!friendModel.isEmpty() && friendList.getSelectedIndex() < 0) {
            friendList.setSelectedIndex(0);
        }
    }

    private void applyStatusPairs(String data) {
        // data: alice=ONLINE;bob=OFFLINE
        if (data == null || data.isBlank()) return;
        for (String p : data.split(";")) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            String[] kv = t.split("=", 2);
            if (kv.length == 2) statusMap.put(kv[0], kv[1]);
        }
    }

    private List<String> parseList(String data) {
        List<String> out = new ArrayList<>();
        if (data == null || data.isBlank()) return out;
        for (String s : data.split(";")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ========== 聊天区输出 ==========
    private void appendSys(String msg) {
        chatArea.append("[系统] " + msg + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void appendMe(String msg) {
        chatArea.append("[我] " + msg + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void appendPeer(String from, String msg) {
        chatArea.append("[" + from + "] " + msg + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // ========== 好友列表渲染 ==========
    private class FriendCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            FriendItem it = (FriendItem) value;
            String st = statusMap.getOrDefault(it.account, "UNKNOWN");
            String stCn = "ONLINE".equals(st) ? "在线" : ("OFFLINE".equals(st) ? "离线" : "未知");
            String name = it.account + (it.remark.isEmpty() ? "" : ("（" + it.remark + "）"));
            setText(name + "  [" + stCn + "]");
            return this;
        }
    }

    private static class FriendItem {
        final String account;
        final String remark;
        FriendItem(String account, String remark) {
            this.account = account;
            this.remark = remark == null ? "" : remark;
        }
        @Override public String toString() { return account; }
    }

    // ========== main ==========
    public static void main(String[] args) throws Exception {
        String host = args.length >= 1 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 9000;

        SwingUtilities.invokeLater(() -> {
            try {
                new ChatClientGUI(host, port);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "连接服务器失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        });
    }
}

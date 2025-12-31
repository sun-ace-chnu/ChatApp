package server;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FriendStore {
    private final Path dbPath;

    public FriendStore(String file) {
        this.dbPath = Paths.get(file);
        initIfMissing();
    }

    private void initIfMissing() {
        try {
            if (!Files.exists(dbPath)) {
                Files.createFile(dbPath);
                // 默认给每个用户初始化空好友列表
                for (String u : UserStore.allUsers()) {
                    Files.writeString(dbPath, u + ":\n", StandardOpenOption.APPEND);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized List<String> listFriends(String user) {
        Map<String, List<String>> all = readAll();
        return new ArrayList<>(all.getOrDefault(user, new ArrayList<>()));
    }

    public synchronized void addFriend(String user, String friend) {
        Map<String, List<String>> all = readAll();
        all.putIfAbsent(user, new ArrayList<>());
        if (!all.get(user).contains(friend)) all.get(user).add(friend);
        writeAll(all);
    }

    // “修改好友”：这里按作业常见理解：重命名/备注。
    // 由于我们是固定账号体系，不允许真正改用户名，所以用“备注名”实现：
    // 约定 friend 以 "账号|备注" 形式保存，例如 bob|室友
    public synchronized void renameFriendRemark(String user, String friendAccount, String newRemark) {
        Map<String, List<String>> all = readAll();
        List<String> fs = all.getOrDefault(user, new ArrayList<>());
        for (int i = 0; i < fs.size(); i++) {
            String item = fs.get(i);
            String acc = item.split("\\|", 2)[0];
            if (acc.equals(friendAccount)) {
                fs.set(i, friendAccount + "|" + newRemark);
            }
        }
        all.put(user, fs);
        writeAll(all);
    }

    public synchronized void deleteFriend(String user, String friendAccount) {
        Map<String, List<String>> all = readAll();
        List<String> fs = all.getOrDefault(user, new ArrayList<>());
        fs.removeIf(item -> item.split("\\|", 2)[0].equals(friendAccount));
        all.put(user, fs);
        writeAll(all);
    }

    private Map<String, List<String>> readAll() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(dbPath);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int idx = line.indexOf(":");
                if (idx < 0) continue;
                String user = line.substring(0, idx);
                String rest = line.substring(idx + 1);
                List<String> fs = new ArrayList<>();
                if (!rest.isBlank()) {
                    for (String f : rest.split(",")) {
                        String t = f.trim();
                        if (!t.isEmpty()) fs.add(t);
                    }
                }
                map.put(user, fs);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 确保所有内置用户存在
        for (String u : UserStore.allUsers()) {
            map.putIfAbsent(u, new ArrayList<>());
        }
        return map;
    }

    private void writeAll(Map<String, List<String>> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            sb.append(e.getKey()).append(":");
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(e.getValue().get(i));
            }
            sb.append("\n");
        }
        try {
            Files.writeString(dbPath, sb.toString(), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

package server;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryStore {
    private final Path baseDir = Paths.get("history");
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public HistoryStore() {
        try {
            if (!Files.exists(baseDir)) Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String pairKey(String a, String b) {
        if (a.compareTo(b) <= 0) return a + "__" + b;
        return b + "__" + a;
    }

    private Path fileOf(String a, String b) {
        return baseDir.resolve(pairKey(a, b) + ".txt");
    }

    public synchronized void appendChat(String from, String to, String content, long ts) {
        Path f = fileOf(from, to);
        String line = String.format("[%s] %s -> %s: %s%n", fmt.format(new Date(ts)), from, to, content);
        try {
            Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized String readHistory(String a, String b) {
        Path f = fileOf(a, b);
        if (!Files.exists(f)) return "(暂无聊天记录)";
        try {
            return Files.readString(f);
        } catch (IOException e) {
            return "(读取失败: " + e.getMessage() + ")";
        }
    }

    public synchronized boolean deleteHistory(String a, String b) {
        Path f = fileOf(a, b);
        try {
            return Files.deleteIfExists(f);
        } catch (IOException e) {
            return false;
        }
    }

    // 列出某用户拥有的历史文件（用于“打开”选择）
    public synchronized List<String> listUserHistories(String user) {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(baseDir, "*.txt")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.startsWith(user + "__") || name.contains("__" + user + ".txt")) {
                    out.add(name);
                }
            }
        } catch (IOException ignore) {}
        return out;
    }
}

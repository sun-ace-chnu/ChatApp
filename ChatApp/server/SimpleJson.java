package server;
import java.util.*;


public class SimpleJson {
    // 将 Message -> JSON(单行)
    public static String toJson(Message m) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("type", safe(m.type));
        map.put("from", safe(m.from));
        map.put("to", safe(m.to));
        map.put("content", safe(m.content));
        map.put("data", safe(m.data));
        map.put("timestamp", String.valueOf(m.timestamp));
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            if ("timestamp".equals(e.getKey())) {
                sb.append(e.getValue());
            } else {
                sb.append("\"").append(escape(e.getValue())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // JSON(单行) -> Message（仅解析本项目字段）
    public static Message fromJson(String json) {
        Map<String, String> kv = parseFlatJson(json);
        Message m = new Message();
        m.type = kv.getOrDefault("type", "");
        m.from = kv.getOrDefault("from", "");
        m.to = kv.getOrDefault("to", "");
        m.content = kv.getOrDefault("content", "");
        m.data = kv.getOrDefault("data", "");
        String ts = kv.getOrDefault("timestamp", "0");
        try { m.timestamp = Long.parseLong(ts); } catch (Exception ignore) { m.timestamp = 0; }
        return m;
    }

    // --------- helpers ----------
    private static String safe(String s) { return s == null ? "" : s; }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    // 非通用 JSON，仅支持 {"k":"v","k2":123,"k3":"v3"} 这种扁平结构
    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        // 简单按逗号分割（要求 content/data 不包含未转义的逗号）
        // 本项目中 toJson 已保证会转义
        List<String> parts = splitTopLevel(json);

        for (String p : parts) {
            int idx = p.indexOf(":");
            if (idx < 0) continue;
            String k = stripQuotes(p.substring(0, idx).trim());
            String vRaw = p.substring(idx + 1).trim();
            String v;
            if (vRaw.startsWith("\"")) v = unescape(stripQuotes(vRaw));
            else v = vRaw; // number
            map.put(k, v);
        }
        return map;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            if (c == ',' && !inStr) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    private static String stripQuotes(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

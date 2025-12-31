package server;
import java.util.*;

public class UserStore {
    // 内置账号：你可以按作业要求改成更多
    private static final Map<String, String> USERS = new HashMap<>();
    static {
        USERS.put("alice", "123");
        USERS.put("bob", "123");
        USERS.put("cathy", "123");
        USERS.put("david", "123");
    }

    public static boolean validate(String username, String password) {
        return USERS.containsKey(username) && USERS.get(username).equals(password);
    }

    public static Set<String> allUsers() {
        return USERS.keySet();
    }
}

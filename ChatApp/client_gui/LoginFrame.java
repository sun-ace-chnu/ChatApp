package client_gui;
import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    public interface LoginCallback {
        void onLoginSubmit(String user, String pass);
    }

    public LoginFrame(LoginCallback cb) {
        setTitle("即时聊天系统 - 登录");
        setSize(360, 220);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        JLabel uLab = new JLabel("账号：");
        JLabel pLab = new JLabel("密码：");
        JTextField uField = new JTextField();
        JPasswordField pField = new JPasswordField();

        form.add(uLab); form.add(uField);
        form.add(pLab); form.add(pField);

        JButton btn = new JButton("登录");
        btn.addActionListener(e -> {
            String u = uField.getText().trim();
            String p = new String(pField.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "账号或密码不能为空。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cb.onLoginSubmit(u, p);
        });

        root.add(form, BorderLayout.CENTER);
        root.add(btn, BorderLayout.SOUTH);
        setContentPane(root);
    }
}

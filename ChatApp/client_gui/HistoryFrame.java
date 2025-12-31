package client_gui;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class HistoryFrame extends JFrame {
    private final JTextArea area = new JTextArea();

    public HistoryFrame(String title) {
        setTitle(title);
        setSize(700, 520);
        setLocationRelativeTo(null);

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        setLayout(new BorderLayout(8, 8));
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    public void setText(String text) {
        area.setText(text == null ? "" : text);
        area.setCaretPosition(0);
    }
}

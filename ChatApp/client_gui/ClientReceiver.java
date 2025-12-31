package client_gui;
import java.io.BufferedReader;

public class ClientReceiver extends Thread {
    private final BufferedReader in;
    private final ChatClientGUI gui;

    public ClientReceiver(BufferedReader in, ChatClientGUI gui) {
        this.in = in;
        this.gui = gui;
        setName("ClientReceiver");
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Message m = SimpleJson.fromJson(line);
                gui.onMessage(m);
            }
        } catch (Exception e) {
            gui.onDisconnected();
        }
    }
}

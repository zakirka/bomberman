package ru.kpfu.itis.ui;

import ru.kpfu.itis.net.GameClient;
import ru.kpfu.itis.net.GameServer;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.net.InetAddress;

public class LauncherFrame extends JFrame {
    private final JTextField nameField = new JTextField("Player");
    private final JTextField hostField = new JTextField("localhost");
    private final JTextField portField = new JTextField("5555");
    private final JLabel statusLabel = new JLabel(" ");

    public LauncherFrame() {
        setTitle("Bomberman Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(340, 200);
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Имя:"));
        form.add(nameField);
        form.add(new JLabel("Хост:"));
        form.add(hostField);
        form.add(new JLabel("Порт:"));
        form.add(portField);

        JButton hostButton = new JButton("Создать сервер");
        JButton joinButton = new JButton("Подключиться");
        hostButton.addActionListener(e -> hostAndPlay());
        joinButton.addActionListener(e -> join());
        form.add(hostButton);
        form.add(joinButton);

        add(form, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void hostAndPlay() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            String name = nameField.getText().trim();
            GameServer server = new GameServer(port);
            server.start();
            hostField.setText(InetAddress.getLocalHost().getHostAddress());
            Thread.sleep(100);
            connectAndOpen(name, "localhost", port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void join() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            String name = nameField.getText().trim();
            String host = hostField.getText().trim();
            connectAndOpen(name, host, port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connectAndOpen(String name, String host, int port) throws Exception {
        statusLabel.setText("Подключение...");
        GameClient client = new GameClient(host, port, name);
        client.connect();
        GameWindow window = new GameWindow(client);
        window.setVisible(true);
        setVisible(false);
    }
}


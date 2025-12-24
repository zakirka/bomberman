package ru.kpfu.itis;

import ru.kpfu.itis.ui.LauncherFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Не удалось установить системный стиль: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            LauncherFrame frame = new LauncherFrame();
            frame.setVisible(true);
        });
    }
}
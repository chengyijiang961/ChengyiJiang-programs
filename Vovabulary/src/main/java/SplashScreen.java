import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SplashScreen extends JWindow {
    private JProgressBar progressBar;
    private Timer timer;
    private int progress = 0;

    public SplashScreen() {
        // 设置窗口无边框
        setLayout(new BorderLayout());

        // 创建主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(41, 128, 185)); // 蓝色背景
        mainPanel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        // 添加标题
        JLabel titleLabel = new JLabel("单词记忆软件");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 36));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加版本信息
        JLabel versionLabel = new JLabel("Version 1.0");
        versionLabel.setFont(new Font("微软雅黑", Font.PLAIN, 18));
        versionLabel.setForeground(new Color(230, 230, 230));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加作者信息
        JLabel authorLabel = new JLabel("<html><center>Created by ChengyiJiang<br>Dec 18 2025<br>  Free for Everyone</center></html>");
        authorLabel.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        authorLabel.setForeground(Color.WHITE);
        authorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加图标（可选）
        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(createIcon());
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(39, 174, 96)); // 绿色进度条
        progressBar.setBackground(new Color(44, 62, 80));
        progressBar.setBorder(BorderFactory.createLineBorder(new Color(30, 30, 30), 1));
        progressBar.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加加载提示
        JLabel loadingLabel = new JLabel("正在加载...");
        loadingLabel.setFont(new Font("微软雅黑", Font.ITALIC, 14));
        loadingLabel.setForeground(new Color(230, 230, 230));
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 将所有组件添加到主面板
        mainPanel.add(iconLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(versionLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        mainPanel.add(authorLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        mainPanel.add(progressBar);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(loadingLabel);

        // 添加主面板到窗口
        add(mainPanel, BorderLayout.CENTER);

        // 设置窗口大小和位置
        pack();
        setLocationRelativeTo(null); // 居中显示

        // 设置窗口始终在最前面
        setAlwaysOnTop(true);
    }

    private Icon createIcon() {
        // 创建一个简单的书籍图标
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制书籍图标
                int width = 80;
                int height = 60;
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;

                // 书籍封面
                g2d.setColor(new Color(241, 196, 15));
                g2d.fillRoundRect(x, y, width, height, 10, 10);

                // 书脊
                g2d.setColor(new Color(243, 156, 18));
                g2d.fillRect(x + width - 8, y, 8, height);

                // 书页效果
                g2d.setColor(new Color(230, 230, 230));
                for (int i = 0; i < 5; i++) {
                    int offset = i * 2;
                    g2d.drawLine(x + offset, y + 10, x + offset, y + height - 10);
                }

                // 书名
                g2d.setColor(new Color(44, 62, 80));
                g2d.setFont(new Font("微软雅黑", Font.BOLD, 12));
                String text = "VOCAB";
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                g2d.drawString(text, x + (width - textWidth) / 2, y + 35);
            }
        };

        iconLabel.setPreferredSize(new Dimension(100, 80));
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                iconLabel.setBounds(x, y, 100, 80);
                iconLabel.paint(g);
            }

            @Override
            public int getIconWidth() {
                return 100;
            }

            @Override
            public int getIconHeight() {
                return 80;
            }
        };
    }

    public void showSplash(int duration) {
        // 显示窗口
        setVisible(true);

        // 启动进度条定时器
        timer = new Timer(duration / 100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                progress++;
                progressBar.setValue(progress);
                progressBar.setString("加载中... " + progress + "%");

                if (progress >= 100) {
                    timer.stop();
                    dispose(); // 关闭启动画面
                }
            }
        });

        timer.start();
    }

    public static void main(String[] args) {
        // 测试启动画面
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SplashScreen splash = new SplashScreen();
                splash.showSplash(3000); // 显示3秒

                // 模拟主程序启动
                Timer mainTimer = new Timer(3000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.out.println("主程序启动！");
                        // 这里启动你的主程序
                        // new VocabularyApp();
                    }
                });
                mainTimer.setRepeats(false);
                mainTimer.start();
            }
        });
    }
}

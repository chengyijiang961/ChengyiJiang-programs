import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VocabularyApp {
    private List<Word> wordList;
    private int currentIndex = 0;
    private int correctCount = 0;
    private int totalQuestions = 0;
    private Random random = new Random();
    private Set<Word> wrongWords = new HashSet<>();

    // 新增：用于记录当前会话中正确回答的单词（从错题本中移除用）
    private Set<Word> correctlyAnsweredInSession = new HashSet<>();

    // 优化选词用到的短期记忆队列
    private final Deque<Word> recentWords = new ArrayDeque<>();
    private static final int RECENT_LIMIT = 10;
    private static final double WRONG_PRIORITY_CHANCE = 0.65;

    // 界面组件（原样）
    private JFrame frame;
    private JLabel wordLabel;
    private JLabel meaningLabel;
    private JLabel scoreLabel;
    private JPanel optionsPanel;
    private JButton[] optionButtons;
    private JButton nextButton;
    private JButton showAnswerButton;
    private JButton reviewButton;
    private JTextField wordInput;
    private JTextField meaningInput;
    private JButton addButton;
    private JComboBox<String> modeComboBox;
    private JProgressBar progressBar;

    // 拼写模式专用组件
    private JTextField spellingInput;
    private JButton submitSpellingButton;
    private JLabel spellingHintLabel;
    private JPanel spellingPanel;

    // 当前问题信息
    private Word currentCorrectWord;
    private int currentCorrectIndex;
    private List<Word> currentOptions;
    private boolean answerChecked = false;

    // 单词类
    class Word {
        String word;
        String meaning;

        Word(String word, String meaning) {
            this.word = word;
            this.meaning = meaning;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Word other = (Word) obj;
            return word.equals(other.word) && meaning.equals(other.meaning);
        }

        @Override
        public int hashCode() {
            return Objects.hash(word, meaning);
        }
    }

    // 学习模式
    enum Mode {
        WORD_TO_MEANING,
        MEANING_TO_WORD,
        SPELLING
    }

    private Mode currentMode = Mode.WORD_TO_MEANING;
    private Timer autoNextTimer;

    public VocabularyApp() {
        wordList = new ArrayList<>();
        loadWords();
        createGUI();
        startNewSession();
    }

    private void loadWords() {
        wordList.clear();
        try {
            List<String> lines = Files.readAllLines(Paths.get("src/test/words.txt"));
            for (String line : lines) {
                if (!line.trim().isEmpty() && line.contains("##")) {
                    String[] parts = line.split("##");
                    if (parts.length >= 2) {
                        wordList.add(new Word(parts[0].trim(), parts[1].trim()));
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("单词文件未找到，已创建新文件");
            createSampleFile();
        }
    }

    private void createSampleFile() {
        try (PrintWriter writer = new PrintWriter("src/test/words.txt")) {
            String[] sampleWords = {
                    "Apple##苹果",
                    "Banana##香蕉",
                    "Computer##计算机",
                    "Programming##编程",
                    "Java##Java语言",
                    "Hello##你好",
                    "World##世界",
                    "Learning##学习",
                    "Memory##记忆",
                    "Practice##练习"
            };
            for (String word : sampleWords) {
                writer.println(word);
            }
            loadWords();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createGUI() {
        frame = new JFrame("单词记忆软件");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 650);
        frame.setLayout(new BorderLayout());

        // 顶部面板
        JPanel topPanel = new JPanel(new BorderLayout());

        // 控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeComboBox = new JComboBox<>(new String[]{"单词选意思", "意思选单词", "拼写练习"});
        modeComboBox.addActionListener(e -> changeMode());
        controlPanel.add(new JLabel("学习模式:"));
        controlPanel.add(modeComboBox);

        scoreLabel = new JLabel("得分: 0/0 (0.0%)");
        scoreLabel.setFont(new Font("宋体", Font.BOLD, 14));
        controlPanel.add(scoreLabel);

        reviewButton = new JButton("复习错误单词(" + wrongWords.size() + ")");
        reviewButton.addActionListener(e -> reviewWrongWords());
        controlPanel.add(reviewButton);

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 20));
        controlPanel.add(new JLabel("进度:"));
        controlPanel.add(progressBar);

        topPanel.add(controlPanel, BorderLayout.NORTH);

        // 主内容面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 问题显示区域
        JPanel questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        questionPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        wordLabel = new JLabel("", SwingConstants.CENTER);
        wordLabel.setFont(new Font("Arial", Font.BOLD, 36));
        wordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        spellingHintLabel = new JLabel("");
        spellingHintLabel.setFont(new Font("微软雅黑", Font.ITALIC, 14));
        spellingHintLabel.setForeground(Color.GRAY);
        spellingHintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        spellingHintLabel.setVisible(false);

        meaningLabel = new JLabel("", SwingConstants.CENTER);
        meaningLabel.setFont(new Font("微软雅黑", Font.PLAIN, 24));
        meaningLabel.setForeground(new Color(0, 100, 0));
        meaningLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        meaningLabel.setVisible(false);

        questionPanel.add(wordLabel);
        questionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        questionPanel.add(spellingHintLabel);
        questionPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        questionPanel.add(meaningLabel);

        // 拼写输入面板
        spellingPanel = new JPanel();
        spellingPanel.setLayout(new BoxLayout(spellingPanel, BoxLayout.Y_AXIS));
        spellingPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        spellingPanel.setVisible(false);

        spellingInput = new JTextField();
        spellingInput.setFont(new Font("Arial", Font.PLAIN, 24));
        spellingInput.setMaximumSize(new Dimension(400, 50));
        spellingInput.setHorizontalAlignment(JTextField.CENTER);

        submitSpellingButton = new JButton("提交拼写 (Enter)");
        submitSpellingButton.setFont(new Font("微软雅黑", Font.PLAIN, 18));
        submitSpellingButton.setPreferredSize(new Dimension(200, 40));
        submitSpellingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitSpellingButton.addActionListener(e -> checkSpelling());

        spellingInput.addActionListener(e -> checkSpelling());

        spellingPanel.add(spellingInput);
        spellingPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        spellingPanel.add(submitSpellingButton);

        // 选项面板
        optionsPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        optionsPanel.setMaximumSize(new Dimension(600, 300));
        optionButtons = new JButton[4];

        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JButton();
            optionButtons[i].setFont(new Font("微软雅黑", Font.PLAIN, 20));
            optionButtons[i].setPreferredSize(new Dimension(250, 60));
            optionButtons[i].setFocusPainted(false);
            final int index = i;
            optionButtons[i].addActionListener(e -> checkAnswer(index));
            optionsPanel.add(optionButtons[i]);

            int keyCode = KeyEvent.VK_1 + i;
            optionButtons[i].getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke((char)keyCode), "selectOption" + i);
            optionButtons[i].getActionMap().put("selectOption" + i, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (optionButtons[index].isEnabled()) {
                        checkAnswer(index);
                    }
                }
            });
        }

        // 控制按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        nextButton = new JButton("下一题 (Ctrl+N)");
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> nextQuestion());
        nextButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "next");
        nextButton.getActionMap().put("next", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (nextButton.isEnabled()) {
                    nextQuestion();
                }
            }
        });

        showAnswerButton = new JButton("显示答案 (Ctrl+S)");
        showAnswerButton.addActionListener(e -> showAnswer());
        showAnswerButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "show");
        showAnswerButton.getActionMap().put("show", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAnswer();
            }
        });

        buttonPanel.add(showAnswerButton);
        buttonPanel.add(nextButton);

        mainPanel.add(questionPanel);
        mainPanel.add(spellingPanel);
        mainPanel.add(optionsPanel);
        mainPanel.add(buttonPanel);

        // 添加单词面板
        JPanel addPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        addPanel.setBorder(BorderFactory.createTitledBorder("添加新单词"));
        addPanel.setMaximumSize(new Dimension(600, 100));

        addPanel.add(new JLabel("单词:"));
        wordInput = new JTextField();
        addPanel.add(wordInput);

        addPanel.add(new JLabel("意思:"));
        meaningInput = new JTextField();
        addPanel.add(meaningInput);

        addButton = new JButton("添加单词 (Ctrl+A)");
        addButton.addActionListener(e -> addNewWord());
        addButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "add");
        addButton.getActionMap().put("add", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addNewWord();
            }
        });
        addPanel.add(addButton);

        // 窗口级键盘监听
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), "select1");
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), "select2");
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), "select3");
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_4, 0), "select4");

        frame.getRootPane().getActionMap().put("select1", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[0].isEnabled()) checkAnswer(0);
            }
        });
        frame.getRootPane().getActionMap().put("select2", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[1].isEnabled()) checkAnswer(1);
            }
        });
        frame.getRootPane().getActionMap().put("select3", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[2].isEnabled()) checkAnswer(2);
            }
        });
        frame.getRootPane().getActionMap().put("select4", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[3].isEnabled()) checkAnswer(3);
            }
        });

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, 0), "numpad1");
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD2, 0), "numpad2");
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD3, 0), "numpad3");
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD4, 0), "numpad4");

        frame.getRootPane().getActionMap().put("numpad1", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[0].isEnabled()) checkAnswer(0);
            }
        });
        frame.getRootPane().getActionMap().put("numpad2", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[1].isEnabled()) checkAnswer(1);
            }
        });
        frame.getRootPane().getActionMap().put("numpad3", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[2].isEnabled()) checkAnswer(2);
            }
        });
        frame.getRootPane().getActionMap().put("numpad4", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (optionButtons[3].isEnabled()) checkAnswer(3);
            }
        });

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.add(addPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void changeMode() {
        int selected = modeComboBox.getSelectedIndex();
        switch (selected) {
            case 0: currentMode = Mode.WORD_TO_MEANING; break;
            case 1: currentMode = Mode.MEANING_TO_WORD; break;
            case 2: currentMode = Mode.SPELLING; break;
        }
        updateUIForCurrentMode();
        startNewSession();
    }

    private void updateUIForCurrentMode() {
        if (currentMode == Mode.SPELLING) {
            optionsPanel.setVisible(false);
            spellingPanel.setVisible(true);
            spellingHintLabel.setVisible(true);
            showAnswerButton.setEnabled(true);
        } else {
            optionsPanel.setVisible(true);
            spellingPanel.setVisible(false);
            spellingHintLabel.setVisible(false);
            showAnswerButton.setEnabled(true);
        }
    }

    private void startNewSession() {
        currentIndex = 0;
        correctCount = 0;
        totalQuestions = 0;
        recentWords.clear();
        correctlyAnsweredInSession.clear();
        updateScore();
        updateProgress();
        updateUIForCurrentMode();
        nextQuestion();
    }

    private Word selectNextWord() {
        if (wordList.isEmpty()) return null;

        if (!wrongWords.isEmpty() && random.nextDouble() < WRONG_PRIORITY_CHANCE) {
            List<Word> wrongs = new ArrayList<>(wrongWords);
            Word selected = wrongs.get(random.nextInt(wrongs.size()));
            addToRecent(selected);
            return selected;
        }

        int attempts = 0;
        final int MAX_ATTEMPTS = 12;
        Word candidate;

        do {
            candidate = wordList.get(random.nextInt(wordList.size()));
            attempts++;
            if (attempts >= MAX_ATTEMPTS) break;
        } while (recentWords.contains(candidate));

        addToRecent(candidate);
        return candidate;
    }

    private void addToRecent(Word word) {
        recentWords.addLast(word);
        if (recentWords.size() > RECENT_LIMIT) {
            recentWords.removeFirst();
        }
    }

    private void nextQuestion() {
        resetButtonsToDefault();

        if (spellingInput != null) {
            spellingInput.setText("");
            spellingInput.setBackground(Color.WHITE);
            spellingInput.setEnabled(true);
            submitSpellingButton.setEnabled(true);
        }

        meaningLabel.setVisible(false);
        spellingHintLabel.setText("");

        if (wordList.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先添加单词！");
            return;
        }

        answerChecked = false;
        nextButton.setEnabled(false);

        currentCorrectWord = selectNextWord();

        if (currentCorrectWord == null) {
            JOptionPane.showMessageDialog(frame, "无法选择单词，请检查词库！");
            return;
        }

        if (currentMode != Mode.SPELLING) {
            if (wordList.size() < 4) {
                JOptionPane.showMessageDialog(frame, "至少需要4个单词才能开始选择题模式！");
                return;
            }

            List<Word> wrongOptions = new ArrayList<>();
            while (wrongOptions.size() < 3) {
                Word randomWord = wordList.get(random.nextInt(wordList.size()));
                if (!randomWord.equals(currentCorrectWord) && !wrongOptions.contains(randomWord)) {
                    wrongOptions.add(randomWord);
                }
            }

            currentOptions = new ArrayList<>(wrongOptions);
            currentOptions.add(currentCorrectWord);
            Collections.shuffle(currentOptions);

            currentCorrectIndex = currentOptions.indexOf(currentCorrectWord);

            switch (currentMode) {
                case WORD_TO_MEANING:
                    wordLabel.setText(currentCorrectWord.word);
                    wordLabel.setForeground(Color.BLACK);
                    for (int i = 0; i < 4; i++) {
                        optionButtons[i].setText(currentOptions.get(i).meaning);
                        optionButtons[i].setToolTipText("选项 " + (i + 1));
                    }
                    break;

                case MEANING_TO_WORD:
                    wordLabel.setText(currentCorrectWord.meaning);
                    wordLabel.setForeground(Color.BLUE);
                    for (int i = 0; i < 4; i++) {
                        optionButtons[i].setText(currentOptions.get(i).word);
                        optionButtons[i].setToolTipText("选项 " + (i + 1));
                    }
                    break;
            }
        } else {
            wordLabel.setText("拼写单词:");
            wordLabel.setForeground(new Color(128, 0, 128));

            meaningLabel.setText("意思: " + currentCorrectWord.meaning);
            meaningLabel.setVisible(true);

            spellingHintLabel.setText("提示: " + getSpellingHint(currentCorrectWord.word));
            spellingHintLabel.setVisible(true);

            spellingInput.requestFocusInWindow();
        }

        currentIndex++;
        updateProgress();

        if (currentMode != Mode.SPELLING && optionButtons != null && optionButtons.length > 0) {
            optionButtons[0].requestFocusInWindow();
        }

        if (autoNextTimer != null) {
            autoNextTimer.cancel();
            autoNextTimer = null;
        }
    }

    private String getSpellingHint(String word) {
        if (word.length() <= 3) {
            return "请输入完整的单词";
        }

        String hint = "单词长度: " + word.length() + "个字母";
        hint += "，首字母: " + word.charAt(0);

        if (word.length() > 5) {
            hint += "，尾字母: " + word.charAt(word.length() - 1);
        }

        return hint;
    }

    private void checkSpelling() {
        if (answerChecked || currentMode != Mode.SPELLING) {
            return;
        }

        answerChecked = true;
        String userInput = spellingInput.getText().trim();

        if (userInput.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入单词！", "提示", JOptionPane.WARNING_MESSAGE);
            spellingInput.requestFocus();
            answerChecked = false;
            return;
        }

        spellingInput.setEnabled(false);
        submitSpellingButton.setEnabled(false);

        boolean isCorrect = userInput.equalsIgnoreCase(currentCorrectWord.word);

        if (isCorrect) {
            correctCount++;
            spellingInput.setBackground(new Color(144, 238, 144));
            spellingInput.setForeground(Color.BLACK);

            JOptionPane.showMessageDialog(frame,
                    "✓ 拼写正确！\n单词: " + currentCorrectWord.word + "\n意思: " + currentCorrectWord.meaning,
                    "正确",
                    JOptionPane.INFORMATION_MESSAGE);

            // 记录这个单词在本次会话中回答正确
            correctlyAnsweredInSession.add(currentCorrectWord);

            // 不从这里移除，保留在wrongWords中供复习
            // wrongWords.remove(currentCorrectWord);

            reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");

            if (autoNextTimer != null) {
                autoNextTimer.cancel();
            }

            autoNextTimer = new Timer();
            autoNextTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        nextQuestion();
                    });
                }
            }, 1500);

        } else {
            spellingInput.setBackground(new Color(255, 182, 193));
            spellingInput.setForeground(Color.RED);

            meaningLabel.setText("正确答案: " + currentCorrectWord.word);
            meaningLabel.setForeground(Color.RED);

            wrongWords.add(currentCorrectWord);
            reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");

            StringBuilder message = new StringBuilder();
            message.append("✗ 拼写错误！\n");
            message.append("你的输入: ").append(userInput).append("\n");
            message.append("正确答案: ").append(currentCorrectWord.word).append("\n");
            message.append("意思: ").append(currentCorrectWord.meaning).append("\n\n");

            message.append("差异分析:\n");
            String correct = currentCorrectWord.word.toLowerCase();
            String input = userInput.toLowerCase();

            for (int i = 0; i < Math.max(correct.length(), input.length()); i++) {
                char correctChar = i < correct.length() ? correct.charAt(i) : ' ';
                char inputChar = i < input.length() ? input.charAt(i) : ' ';

                if (correctChar == inputChar) {
                    message.append("✓ '").append(correctChar).append("'\n");
                } else {
                    message.append("✗ 应为 '").append(correctChar).append("'，你的输入是 '")
                            .append(inputChar).append("'\n");
                }
            }

            JOptionPane.showMessageDialog(frame,
                    message.toString(),
                    "拼写错误",
                    JOptionPane.ERROR_MESSAGE);

            nextButton.setEnabled(true);
        }

        totalQuestions++;
        updateScore();
    }

    private void resetButtonsToDefault() {
        if (optionButtons != null) {
            for (JButton button : optionButtons) {
                button.setEnabled(true);
                button.setBackground(UIManager.getColor("Button.background"));
                button.setForeground(UIManager.getColor("Button.foreground"));
                button.setBorder(UIManager.getBorder("Button.border"));
                button.setOpaque(true);
            }
        }
    }

    private void checkAnswer(int selectedIndex) {
        if (answerChecked || currentMode == Mode.SPELLING) {
            return;
        }

        answerChecked = true;

        for (JButton button : optionButtons) {
            button.setEnabled(false);
        }

        optionButtons[currentCorrectIndex].setBackground(new Color(144, 238, 144));
        optionButtons[currentCorrectIndex].setForeground(Color.BLACK);
        optionButtons[currentCorrectIndex].setBorder(BorderFactory.createLineBorder(new Color(0, 100, 0), 3));

        if (selectedIndex == currentCorrectIndex) {
            correctCount++;
            optionButtons[selectedIndex].setBackground(new Color(152, 251, 152));
            JOptionPane.showMessageDialog(frame,
                    "✓ 回答正确！\n" + getAnswerExplanation(),
                    "正确",
                    JOptionPane.INFORMATION_MESSAGE);

            // 记录这个单词在本次会话中回答正确
            correctlyAnsweredInSession.add(currentCorrectWord);

            // 不从这里移除，保留在wrongWords中供复习
            // wrongWords.remove(currentCorrectWord);

            reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");

            if (autoNextTimer != null) {
                autoNextTimer.cancel();
            }

            autoNextTimer = new Timer();
            autoNextTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        nextQuestion();
                    });
                }
            }, 100);

        } else {
            optionButtons[selectedIndex].setBackground(new Color(255, 182, 193));
            optionButtons[selectedIndex].setBorder(BorderFactory.createLineBorder(Color.RED, 2));
            optionButtons[selectedIndex].setForeground(Color.RED);

            wrongWords.add(currentCorrectWord);
            reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");

            JOptionPane.showMessageDialog(frame,
                    "✗ 回答错误！\n" + getAnswerExplanation(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);

            for (int i = 0; i < 4; i++) {
                optionButtons[i].setEnabled(true);
                if (i == selectedIndex || i == currentCorrectIndex) {
                    continue;
                }
                optionButtons[i].setBackground(UIManager.getColor("Button.background"));
                optionButtons[i].setForeground(UIManager.getColor("Button.foreground"));
                optionButtons[i].setBorder(UIManager.getBorder("Button.border"));
            }

            meaningLabel.setText("正确答案: " +
                    (currentMode == Mode.WORD_TO_MEANING ?
                            currentCorrectWord.meaning : currentCorrectWord.word));
            meaningLabel.setVisible(true);

            nextButton.setEnabled(true);
        }

        totalQuestions++;
        updateScore();
    }

    private String getAnswerExplanation() {
        switch (currentMode) {
            case WORD_TO_MEANING:
                return currentCorrectWord.word + " 的意思是: " + currentCorrectWord.meaning;
            case MEANING_TO_WORD:
                return currentCorrectWord.meaning + " 对应的单词是: " + currentCorrectWord.word;
            default:
                return "";
        }
    }

    private void showAnswer() {
        if (currentCorrectWord == null) return;

        if (currentMode == Mode.SPELLING) {
            spellingInput.setText(currentCorrectWord.word);
            spellingInput.setBackground(new Color(144, 238, 144));
            spellingInput.setEnabled(false);
            submitSpellingButton.setEnabled(false);

            wrongWords.add(currentCorrectWord);
            reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");

            answerChecked = true;
            nextButton.setEnabled(true);

        } else {
            String answer;
            switch (currentMode) {
                case WORD_TO_MEANING:
                    answer = currentCorrectWord.meaning;
                    break;
                case MEANING_TO_WORD:
                    answer = currentCorrectWord.word;
                    break;
                default:
                    answer = "";
            }

            meaningLabel.setText("答案: " + answer);
            meaningLabel.setVisible(true);

            if (!answerChecked) {
                answerChecked = true;
                for (JButton button : optionButtons) {
                    button.setEnabled(false);
                }

                optionButtons[currentCorrectIndex].setBackground(new Color(144, 238, 144));
                optionButtons[currentCorrectIndex].setBorder(BorderFactory.createLineBorder(new Color(0, 100, 0), 3));

                nextButton.setEnabled(true);

                wrongWords.add(currentCorrectWord);
                reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");
            }
        }
    }

    private void reviewWrongWords() {
        if (wrongWords.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "暂时没有需要复习的错误单词！");
            return;
        }

        int option = JOptionPane.showConfirmDialog(frame,
                "当前有 " + wrongWords.size() + " 个错误单词需要复习。\n是否开始复习？",
                "复习错误单词",
                JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            List<Word> reviewList = new ArrayList<>(wrongWords);
            Collections.shuffle(reviewList);

            JFrame reviewFrame = new JFrame("复习错误单词");
            reviewFrame.setSize(400, 300);
            reviewFrame.setLayout(new BorderLayout());

            JTextArea reviewArea = new JTextArea();
            reviewArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            reviewArea.setEditable(false);

            StringBuilder sb = new StringBuilder("需要复习的单词:\n\n");
            for (Word word : reviewList) {
                sb.append(word.word).append("  -  ").append(word.meaning).append("\n");
            }
            reviewArea.setText(sb.toString());

            JButton clearButton = new JButton("清空错误记录");
            clearButton.addActionListener(e -> {
                wrongWords.clear();
                correctlyAnsweredInSession.clear();
                reviewButton.setText("复习错误单词(" + wrongWords.size() + ")");
                reviewFrame.dispose();
                JOptionPane.showMessageDialog(frame, "错误记录已清空！");
            });

            JButton practiceButton = new JButton("练习这些单词");
            practiceButton.addActionListener(e -> {
                wordList = new ArrayList<>(reviewList);
                reviewFrame.dispose();
                startNewSession();
            });

            JPanel buttonPanel = new JPanel(new FlowLayout());
            buttonPanel.add(practiceButton);
            buttonPanel.add(clearButton);

            reviewFrame.add(new JScrollPane(reviewArea), BorderLayout.CENTER);
            reviewFrame.add(buttonPanel, BorderLayout.SOUTH);
            reviewFrame.setLocationRelativeTo(frame);
            reviewFrame.setVisible(true);
        }
    }

    private void updateScore() {
        double percentage = totalQuestions > 0 ? (correctCount * 100.0 / totalQuestions) : 0;
        scoreLabel.setText(String.format("得分: %d/%d (%.1f%%)",
                correctCount, totalQuestions, percentage));
    }

    private void updateProgress() {
        if (!wordList.isEmpty()) {
            int progress = (currentIndex * 100) / Math.max(10, wordList.size());
            progressBar.setValue(progress);
            progressBar.setString(progress + "%");
        }
    }

    private void addNewWord() {
        String word = wordInput.getText().trim();
        String meaning = meaningInput.getText().trim();

        if (!word.isEmpty() && !meaning.isEmpty()) {
            try (FileWriter fw = new FileWriter("src/test/words.txt", true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(word + "##" + meaning);
                wordList.add(new Word(word, meaning));
                wordInput.setText("");
                meaningInput.setText("");

                JOptionPane.showMessageDialog(frame,
                        "单词添加成功！\n新单词: " + word + " - " + meaning,
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);

                nextQuestion();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(frame, "请输入单词和意思！", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                SplashScreen splash = new SplashScreen();
                splash.showSplash(2000);

                javax.swing.Timer mainTimer = new javax.swing.Timer(2000, e -> {
                    new VocabularyApp();
                });
                mainTimer.setRepeats(false);
                mainTimer.start();

            } catch (Exception e) {
                e.printStackTrace();
                new VocabularyApp();
            }
        });
    }
}

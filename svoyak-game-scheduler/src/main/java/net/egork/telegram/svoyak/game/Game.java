package net.egork.telegram.svoyak.game;

import net.egork.telegram.svoyak.Utils;
import net.egork.telegram.svoyak.data.Question;
import net.egork.telegram.svoyak.data.Topic;
import net.egork.telegram.svoyak.data.TopicSet;
import net.egork.telegram.svoyak.scheduler.SchedulerMain;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.User;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * @author egor@egork.net
 */
public class Game implements Runnable {
    private static final String[] PLUS = {"+"};
    private static final String[] YES_NO = {"да", "нет"};
    private static final String[] BREAK = {"да", "нет", "пауза"};
    private static final String[] PAUSED = {"да", "нет", "продолжить"};
    private static final long RATE = 100;
    private static final long INTERMISSION = 8000;
    private static final long SUCCESSIVE_QUESTION = 10000;
    private static final long FIRST_QUESTION = 15000;
    private static final long ANSWER = 30000;
    private static final String[] EMPTY = new String[0];
    private boolean paused;

    private final boolean tournamentGame;
    private int judgeId;

    public int getCurrentTopic() {
        return topicId;
    }

    public State getStatus() {
        return state;
    }

    public enum State {
        BEFORE_TOPIC,
        BEFORE_FIRST_QUESTION,
        BEFORE_QUESTION,
        QUESTION,
        ANSWER,
        AFTER_QUESTION,
        SPECIAL_SCORE,
        JUDGE_DECISION,
        REGISTRATION,
        BEFORE_GAME,
        AFTER_GAME
    }

    private TopicSet set;
    private Topic currentTopic;
    private Question currentQuestion;
    private int topicId;
    private int stopAt;

    private Map<Integer, String> users = new HashMap<Integer, String>();
    private Map<Integer, Integer> score = new HashMap<Integer, Integer>();

    private Set<Integer> presentPlayers = new HashSet<>();
    private int minutesWaited;

    private List<Integer> answers;
    private int correct;

    private long actionExpires;
    private State state;
    private net.egork.telegram.svoyak.data.User current;

    private long origChatId;
    private long chatId;
    private SchedulerMain scheduler;
    private final List<net.egork.telegram.svoyak.data.User> players;
    private List<Integer> topics;

    private volatile Executor executor = Executors.newSingleThreadExecutor();

    private Timer timer;
    private int lastQuestionId;

    public Game(SchedulerMain scheduler, long originalChatId, long chatId, TopicSet set, List<Integer> topics,
            List<net.egork.telegram.svoyak.data.User> players) {
        this.scheduler = scheduler;
        this.players = players;
        this.tournamentGame = false;
        origChatId = originalChatId;
        this.chatId = chatId;
        this.topics = topics;
        for (net.egork.telegram.svoyak.data.User user : players) {
            users.put(user.getId(), getName(user));
            add(user.getId(), 0);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Game.this.set = set;
                topicId = 0;
                stopAt = topics.size();
                if (tournamentGame) {
                    state = State.REGISTRATION;
                    actionExpires = Long.MAX_VALUE;
                    sendMessage("Регистрация открыта", null);
                } else {
                    state = State.BEFORE_GAME;
                    sendMessage("Добро пожаловать", null, 60000);
                    timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            executor.execute(Game.this);
                        }
                    }, RATE, RATE);
                }
            }
        });
    }

    private void sendMessage(String message, String[] keyboard) {
        scheduler.getGameBot().sendMessage(chatId, message, keyboard);
    }

    private void startGame() {
        StringBuilder list = new StringBuilder();
        list.append("<b>Список тем:</b>\n");
        for (int i = topicId; i < stopAt; i++) {
            list.append((topics.get(i) + 1) + ". " + set.byIndex(topics.get(i)).topicName + "\n");
        }
        sendMessage("Игра началась. " + set.shortName + "\n" + set.description + "\n" + list.toString()
                + "\n\nИгроки: " + Utils.userList(players), null, INTERMISSION);
        state = State.BEFORE_TOPIC;
    }

    private void sendMessage(String text, String[] keyboard, long delay) {
        actionExpires = Long.MAX_VALUE;
        scheduler.getGameBot().sendMessage(chatId, text, keyboard, new CallBack(delay));
    }

    private void editMessage(String text) {
        scheduler.getGameBot().editMessage(chatId, lastQuestionId, text);
    }

    @Override
    public void run() {
        if (System.currentTimeMillis() >= actionExpires) {
            switch (state) {
            case BEFORE_QUESTION:
                actionExpires = Long.MAX_VALUE;
                scheduler.getGameBot().sendMessage(chatId, getQuestionText(), EMPTY, id -> {
                    lastQuestionId = id;
                    if (!paused) {
                        actionExpires = System.currentTimeMillis() + FIRST_QUESTION;
                    }
                });
                state = State.QUESTION;
                return;
            case AFTER_GAME:
                timer.cancel();
                scheduler.kickUsers(chatId);
                return;
            case BEFORE_GAME:
                minutesWaited++;
                if (minutesWaited >= 5) {
                    startGame();
                } else {
                    sendMessage("Некоторые игроки все еще не зашли в чат. Через " + (5 - minutesWaited) + " минут" +
                            (minutesWaited == 4 ? "у" : "ы") +
                            " " +
                            "игра начнется автоматически", null, 60000);
                }
                return;
            case BEFORE_TOPIC:
                if (topicId == stopAt) {
                    endGame(false);
                    return;
                }
                currentTopic = set.byIndex(topics.get(topicId));
                int remaining = stopAt - topicId;
                sendMessage((lastTopic() ? "Последняя тема" : "Осталось " + remaining + " " +
                        getTopicWord(remaining)) + "\n" +
                        "<b>Тема " + (set.topics.indexOf(currentTopic) + 1) + ":</b> " + currentTopic.topicName, null,
                        INTERMISSION);
                state = State.BEFORE_FIRST_QUESTION;
                break;
            case BEFORE_FIRST_QUESTION:
                currentQuestion = currentTopic.first();
                askQuestion();
                break;
            case AFTER_QUESTION:
                addResults();
                currentQuestion = currentTopic.next(currentQuestion);
                if (currentQuestion == null) {
                    topicId++;
                    showScore();
                    state = State.BEFORE_TOPIC;
                } else if (lastTopic() && currentQuestion.cost == 50) {
                    showScore();
                    state = State.SPECIAL_SCORE;
                } else {
                    askQuestion();
                }
                break;
            case SPECIAL_SCORE:
                askQuestion();
                break;
            case QUESTION:
                sendMessage("<b>Ответ:</b> " + currentQuestion.authorAnswers(), BREAK, INTERMISSION);
                state = State.AFTER_QUESTION;
                break;
            case ANSWER:
                editMessage(getQuestionText());
                sendMessage("Время вышло, " + getName(current), PLUS, SUCCESSIVE_QUESTION);
                state = State.QUESTION;
                answers.add(current.getId());
                current = null;
                break;
            }
        }
    }

    @NotNull
    private String getQuestionText() {
        return "<b>Тема</b> " + currentTopic.topicName + "\n<b>" +
                currentQuestion.cost + ".</b> " + currentQuestion.question;
    }

    private void askQuestion() {
//        sendMessage("<b>Тема</b> " + currentTopic.topicName + "\n<b>" +
//                currentQuestion.cost + ".</b> " + currentQuestion.question, PLUS, FIRST_QUESTION);
        sendMessage("Внимание, вопрос", PLUS, 1000);
        state = State.BEFORE_QUESTION;
        answers = new ArrayList<>();
        correct = 0;
    }

    private boolean lastTopic() {
        return topicId + 1 == stopAt;
    }

    public static String getTopicWord(int topics) {
        if (topics % 10 == 0 || topics % 10 >= 5 || topics % 100 >= 10 && topics % 100 < 20) {
            return "тем";
        }
        if (topics % 10 == 1) {
            return "тема";
        }
        return "темы";
    }

    private static class ScoreEntry implements Comparable<ScoreEntry> {
        int score;
        String name;

        public ScoreEntry(int score, String name) {
            this.score = score;
            this.name = name;
        }

        @Override
        public int compareTo(ScoreEntry o) {
            return o.score - score;
        }
    }

    private void showScore() {
        if (score.isEmpty()) {
            sendMessage("Счет не открыт.", null, INTERMISSION);
            return;
        }
        StringBuilder score = getScores();
        sendMessage((topicId == stopAt ? "<b>Финальный" : "<b>Текущий") + " счет:</b>\n" + score.toString(), null,
                INTERMISSION);
    }

    @NotNull
    private StringBuilder getScores() {
        StringBuilder score = new StringBuilder();
        List<ScoreEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : users.entrySet()) {
            add(entry.getKey(), 0);
            entries.add(new ScoreEntry(this.score.get(entry.getKey()), entry.getValue()));
        }
        Collections.sort(entries);
        for (ScoreEntry entry : entries) {
            score.append(entry.name + " " + entry.score + "\n");
        }
        return score;
    }

    private void endGame(boolean aborted) {
        state = State.AFTER_GAME;
        paused = false;
        sendMessage("Игра окончена!", null, 3000);
        sendMessage("Вскоре бот удалит всех игроков из этой комнаты.\n" +
                    "Пожалуйста, не выходите самостоятельно!", null, 60000);
        scheduler.endGame(origChatId, set, score, users, aborted);
    }

    public void process(Message message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                User newChatMember = message.getNewChatMember();
                if (state == State.BEFORE_GAME && newChatMember != null && users.containsKey(newChatMember.getId())) {
                    presentPlayers.add(newChatMember.getId());
                    if (presentPlayers.size() == users.size()) {
                        minutesWaited = 5;
                        sendMessage("Игра скоро начнется", null, 15000);
                    }
                    return;
                }
                if (state == State.AFTER_GAME) {
                    actionExpires = Math.max(actionExpires, System.currentTimeMillis() + 60000);
                    return;
                }
                String text = message.getText();
                if (text == null) {
                    return;
                }
                text = text.trim();
                if (text.isEmpty()) {
                    return;
                }
                if (state == State.REGISTRATION) {
                    if (text.equals("судья")) {
                        judgeId = message.getFrom().getId();
                        sendMessage("Судья зарегестрирован", null);
                    } else if (text.equals("регистрация")) {
                        users.put(message.getFrom().getId(), getName(new net.egork.telegram.svoyak.data.User(message.getFrom())));
                        score.put(message.getFrom().getId(), 0);
                        sendMessage("Игрок зарегестрирован", null);
                    } else if (text.equals("старт") && message.getFrom().getId() == judgeId) {
                        startGame();
                    }
                    return;
                }
                if (state == State.JUDGE_DECISION && message.getFrom().getId() == judgeId) {
                    if (text.equals("да")) {
                        sendMessage("Это правильный ответ, " + getName(current) + "\n" +
                                "<b>Авторский ответ</b>: " + currentQuestion.authorAnswers(), null, INTERMISSION);
                        correct = current.getId();
                        current = null;
                        state = State.AFTER_QUESTION;
                    } else if (text.equals("нет")) {
                        sendMessage("Это неправильный ответ, " + getName(current), PLUS, answers.size() == users.size
                                () ? 0 : SUCCESSIVE_QUESTION);
                        current = null;
                        state = State.QUESTION;
                    }
                    return;
                }
                if (state == State.ANSWER && message.getFrom().getId() == current.getId()) {
                    if (text.equals("+")) {
                        return;
                    }
                    if (tournamentGame) {
                        state = State.JUDGE_DECISION;
                        answers.add(current.getId());
                        actionExpires = Long.MAX_VALUE;
                        sendMessage("Ответ принят: " + text + ".\nРешение судьи?", YES_NO);
                    } else if (currentQuestion.checkAnswer(text)) {
                        editMessage(getQuestionText());
                        sendMessage("Это правильный ответ, " + getName(new net.egork.telegram.svoyak.data.User(message.getFrom())) + "\n" +
                                "<b>Авторский ответ</b>: " + currentQuestion.authorAnswers(), BREAK, INTERMISSION);
                        answers.add(current.getId());
                        correct = current.getId();
                        current = null;
                        state = State.AFTER_QUESTION;
                    } else {
                        sendMessage("Это неправильный ответ, " + getName(new net.egork.telegram.svoyak.data.User(message.getFrom())), PLUS, SUCCESSIVE_QUESTION);
                        editMessage(getQuestionText());
                        answers.add(current.getId());
                        current = null;
                        state = State.QUESTION;
                    }
                    return;
                }
                String[] tokens = text.split(" ");
                String command = tokens[0].toLowerCase();
                net.egork.telegram.svoyak.data.User user = new net.egork.telegram.svoyak.data.User(message.getFrom());
                if (command.equals("/abort")) {
                    endGame(true);
                } else if (command.equals("+") && state == State.QUESTION) {
                    if (answers.indexOf(user.getId()) != -1) {
                        return;
                    }
                    if (!users.containsKey(user.getId())) {
                        return;
                    }
                    users.put(user.getId(), getName(user));
                    current = user;
                    sendMessage("Ваш ответ, " + getName(user) + "?", null, ANSWER);
                    editMessage("<b>Тема</b> " + currentTopic.topicName + "\n<b>" + currentQuestion.cost + ".</b> " +
                            "Вопрос скрыт");
                    state = State.ANSWER;
                } else if ((command.equals("/pause") || command.equals("пауза")) && state != State.QUESTION && state
                        != State.ANSWER && !paused) {
                    actionExpires = Long.MAX_VALUE;
                    sendMessage("Игра приостановлена", state == State.AFTER_QUESTION ? PAUSED : null);
                    paused = true;
                } else if ((command.equals("/continue") || command.equals("продолжить")) && paused) {
                    sendMessage("Игра возобновлена", state == State.AFTER_QUESTION ? BREAK : null, INTERMISSION);
                    paused = false;
                } else if ((command.equals("/yes") || command.equals("да")) && state == State.AFTER_QUESTION) {
                    if (!tournamentGame) {
                        fixAnswer(message, getName(user));
                    }
                } else if ((command.equals("/no") || command.equals("нет")) && state == State.AFTER_QUESTION) {
                    if (!tournamentGame) {
                        discardAnswer(message, getName(user));
                    }
                } else if ((command.equals("/adjust") || command.equals("исправить")) && paused) {
                    if (tokens.length < 2) {
                        sendMessage("Недостаточно аргументов", EMPTY);
                    } else {
                        try {
                            int by = Integer.parseInt(tokens[1]);
                            add(user.getId(), by);
                            users.put(user.getId(), getName(user));
                            sendMessage("Новое количество очков у " + getName(user) + " - " + score.get(user.getId()),
                                    EMPTY);
                        } catch (NumberFormatException e) {
                            sendMessage(tokens[1] + " не число", EMPTY);
                        }
                    }
                }
            }
        });
    }

    private void fixAnswer(Message message, String username) {
        boolean found = false;
        for (int user : answers) {
            if (user == message.getFrom().getId()) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        if (correct == message.getFrom().getId()) {
            return;
        }
        correct = message.getFrom().getId();
        if (paused) {
            sendMessage("Принято, " + username, PAUSED);
        } else {
            sendMessage("Принято, " + username, BREAK, INTERMISSION);
        }
    }

    private void discardAnswer(Message message, String username) {
        if (correct != 0 && correct == message.getFrom().getId()) {
            correct = 0;
            if (paused) {
                sendMessage("Принято, " + username, PAUSED);
            } else {
                sendMessage("Принято, " + username, BREAK, INTERMISSION);
            }
        }
    }

    @NotNull
    private String getName(net.egork.telegram.svoyak.data.User user) {
        return Utils.name(user);
    }

    public void addResults() {
        for (int user : answers) {
            if (correct == user) {
                add(user, currentQuestion.cost);
                break;
            }
            add(user, -currentQuestion.cost);
        }
    }

    private void add(int user, int cost) {
        if (!score.containsKey(user)) {
            score.put(user, 0);
        }
        score.put(user, score.get(user) + cost);
    }

    private class CallBack implements Consumer<Integer> {
        private long delay;

        public CallBack(long delay) {
            this.delay = delay;
        }

        @Override
        public void accept(Integer id) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!paused) {
                        actionExpires = System.currentTimeMillis() + delay;
                    }
                }
            });
        }
    }
}

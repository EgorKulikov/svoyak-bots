package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.*;
import net.egork.telegram.File;
import net.egork.telegram.svoyak.data.Topic;
import net.egork.telegram.svoyak.data.TopicSet;
import net.egork.telegram.svoyak.game.Game;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author egor@egork.net
 */
public class SchedulerMain {
    private TelegramBot bot;
    private TelegramBot gameBot;
    private GameChat[] gameChats = {
            new GameChat(-165421271L, "https://telegram.me/joinchat/DskhTQncINfKx_WvGXaf2Q"),
            new GameChat(-158229596L, "https://telegram.me/joinchat/DskhTQluZFzfv0-HZl4lzg"),
            new GameChat(-153565904L, "https://telegram.me/joinchat/DskhTQknOtDStaSM-7IdRg"),
            new GameChat(-171488976L, "https://telegram.me/joinchat/DskhTQo4ttAnVADN4N1qug"),
    };

    private Executor executor = Executors.newSingleThreadExecutor();
    private List<String> activePackages = new ArrayList<>();
    private List<String> allPackages = new ArrayList<>();
    private Map<String, TopicSet> sets = new HashMap<>();
    private Map<Long, ScheduleChat> chats = new HashMap<>();
    private Map<Integer, Set<TopicId>> played = new HashMap<>();

    public static void main(String[] args) {
        new SchedulerMain().run();
    }

    private void run() {
        loadProperties();
        loadList("active.list", activePackages);
        loadList("all.list", allPackages);
        loadSets();
        loadPlayed();
        bot = new TelegramBot(System.getProperty("scheduler.token"), "Scheduler") {
            @Override
            protected void processMessage(Message message) {
                executor.execute(() -> SchedulerMain.this.processMessage(message));
            }
        };
        gameBot = new TelegramBot(System.getProperty("play.token"), "Play") {
            @Override
            protected void processMessage(Message message) {
                executor.execute(() -> processPlayMessage(message));
            }
        };
    }

    private void processPlayMessage(Message message) {
        for (GameChat chat : gameChats) {
            if (chat.chatId == message.getChat().getId()) {
                Game game = chat.getGame();
                if (game != null) {
                    game.process(message);
                }
            }
        }
    }

    public String getSetName(String set) {
        return sets.get(set).shortName;
    }

    private void loadSets() {
        for (String s : allPackages) {
            try {
                TopicSet set = TopicSet.parseReader(new BufferedReader(new FileReader(s)));
                sets.put(s, set);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void loadList(String fileName, List<String> list) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            String s;
            while ((s = reader.readLine()) != null) {
                list.add(s.trim());
            }
            reader.close();
        } catch (IOException ignored) {
        }
    }

    private void loadPlayed() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("played.list"));
            String s;
            while ((s = reader.readLine()) != null) {
                int userId = Integer.parseInt(s);
                s = reader.readLine();
                int count = Integer.parseInt(s);
                Set<TopicId> set = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    String id = reader.readLine();
                    int topic = Integer.parseInt(reader.readLine());
                    set.add(new TopicId(id, topic));
                }
                played.put(userId, set);
            }
            reader.close();
        } catch (IOException ignored) {
        }
    }

    private void savePlayed() {
        try {
            PrintWriter out = new PrintWriter("played.list");
            for (Map.Entry<Integer, Set<TopicId>> entry : played.entrySet()) {
                out.println(entry.getKey());
                out.println(entry.getValue().size());
                for (TopicId topicId : entry.getValue()) {
                    out.println(topicId.setId);
                    out.println(topicId.topic);
                }
            }
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveList(String fileName, List<String> list) {
        try {
            PrintWriter out = new PrintWriter(fileName);
            for (String s : list) {
                out.println(s);
            }
            out.close();
        } catch (IOException ignored) {
        }
    }

    private void loadProperties() {
        Properties properties = new Properties();
        try {
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Enumeration propertyName = properties.propertyNames(); propertyName.hasMoreElements();) {
            String key = (String) propertyName.nextElement();
            System.getProperties().put(key, properties.getProperty(key));
        }
    }

    private void processMessage(Message message) {
        Chat chat = message.getChat();
        if (chat.getType() == ChatType.PRIVATE) {
            processPrivateMessage(message);
        } else {
            processGroupMessage(message);
        }
    }

    private void processGroupMessage(Message message) {
        long chatId = message.getChat().getId();
        for (GameChat chat : gameChats) {
            if (chat.chatId == chatId) {
                if (message.getNewChatMember() != null) {
                    kickIfNeeded(chat, message.getNewChatMember());
                }
                break;
            }
        }
        ScheduleChat chat = chats.get(chatId);
        if (chat == null) {
            chats.put(chatId, chat = new ScheduleChat(chatId, this));
        }
        chat.processMessage(message);
    }

    private void kickIfNeeded(GameChat chat, User user) {
        if (chat.isFree() || (!chat.getGameData().getPlayers().contains(user) &&
                !chat.getGameData().getSpectators().contains(user)))
        {
            kickPlayer(chat.chatId, user);
        }
    }

    private void kickPlayer(long chatId, User user) {
        bot.kickPlayer(chatId, user.getId());
    }

    private void processPrivateMessage(Message message) {
        if (!isAuthorized(message.getFrom())) {
            bot.sendMessage(message.getChat().getId(),
                    "Вы не авторизованы добавлять новые пакеты. Обращайтесь к @Kroge");
            return;
        }
        long chatId = message.getChat().getId();
        Document document = message.getDocument();
        if (document != null) {
            File file = bot.getFile(document.getFileId());
            if (file == null || file.getFilePath() == null) {
                bot.sendMessage(chatId, "Не удалось загрузить файл");
                return;
            }
            try {
                URL url = new URL("https://api.telegram.org/file/bot" + System.getProperty("scheduler.token") +
                        "/" + file.getFilePath());
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openConnection()
                        .getInputStream()));
                TopicSet newSet = TopicSet.parseReader(reader);
                if (newSet == null) {
                    bot.sendMessage(chatId, "Пакет из файла загрузить не удалось");
                } else {
                    TopicSet set = newSet;
                    String id = document.getFileName();
                    sets.put(id, set);
                    allPackages.remove(id);
                    activePackages.remove(id);
                    allPackages.add(id);
                    saveList("all.list", allPackages);
                    saveSet(id, set);
                    bot.sendMessage(chatId, "Пакет " + set.shortName + " загружен. Всего " + set.topics.size() +
                            " " + Topic.getTopicWord(set.topics.size()));
                }
            } catch (IOException e) {
                bot.sendMessage(chatId, "Не удалось загрузить файл");
            }
        } else {
            String text = message.getText();
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            text = text.trim();
            String[] tokens = text.split(" ");
            String command = tokens[0].toLowerCase();
            String set = tokens.length < 2 ? null : tokens[1];
            switch (command) {
            case "/activate":
            case "включить":
                if (set == null || !allPackages.contains(set)) {
                    bot.sendMessage(chatId, "Неизвестный пакет - " + set);
                } else if (activePackages.contains(set)) {
                    bot.sendMessage(chatId, "Пакет уже включен");
                } else {
                    activePackages.add(set);
                    saveList("active.list", activePackages);
                    bot.sendMessage(chatId, "Пакет включен - " + set);
                }
                break;
            case "/deactivate":
            case "выключить":
                if (set == null || !allPackages.contains(set)) {
                    bot.sendMessage(chatId, "Неизвестный пакет - " + set);
                } else if (!activePackages.contains(set)) {
                    bot.sendMessage(chatId, "Пакет уже выключен");
                } else {
                    activePackages.remove(set);
                    saveList("active.list", activePackages);
                    bot.sendMessage(chatId, "Пакет выключен - " + set);
                }
                break;
            case "/alltopics":
            case "темы":
                if (set == null || !allPackages.contains(set)) {
                    bot.sendMessage(chatId, "Неизвестный пакет - " + set);
                } else {
                    TopicSet topicSet = sets.get(set);
                    StringBuilder list = new StringBuilder();
                    list.append("Список тем:\n");
                    for (int i = 0; i < topicSet.topics.size(); i++) {
                        list.append((i + 1)).append(". ").append(topicSet.byIndex(i).topicName).append("\n");
                    }
                    bot.sendMessage(chatId, list.toString());
                }
                break;
            default:
                bot.sendMessage(chatId, "Неизвестная команда - " + command);
                break;
            }
        }
    }

    private void saveSet(String id, TopicSet set) {
        try {
            PrintWriter out = new PrintWriter(id);
            set.saveSet(out);
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isAuthorized(User from) {
        return "Kroge".equals(from.getUsername());
    }

    public TelegramBot getBot() {
        return bot;
    }

    public String getLastSet() {
        if (activePackages.isEmpty()) {
            return null;
        }
        return activePackages.get(activePackages.size() - 1);
    }

    public boolean hasSet(String argument) {
        return allPackages.contains(argument);
    }

    public void tryStartNewGame(long id, GameData currentGame) {
        TopicSet topicSet = sets.get(currentGame.getSetId());
        List<Integer> topics = new ArrayList<>();
        for (int i = 1; i <= topicSet.topics.size(); i++) {
            TopicId topicId = new TopicId(currentGame.getSetId(), i);
            boolean good = true;
            for (User user : currentGame.getPlayers()) {
                Set<TopicId> set = played.get(user.getId());
                if (set != null && set.contains(topicId)) {
                    good = false;
                }
            }
            if (good) {
                topics.add(i - 1);
                if (topics.size() == currentGame.getTopicCount()) {
                    break;
                }
            }
        }
        if (topics.size() != currentGame.getTopicCount()) {
            bot.sendMessage(id, "В текущем пакете недостаточно тем, которые бы не играли все игроки");
            return;
        }
        for (int topic : topics) {
            TopicId topicId = new TopicId(currentGame.getSetId(), topic + 1);
            for (User user : currentGame.getPlayers()) {
                Set<TopicId> set = played.get(user.getId());
                if (set == null) {
                    played.put(user.getId(), set = new HashSet<>());
                }
                set.add(topicId);
            }
            for (User user : currentGame.getSpectators()) {
                Set<TopicId> set = played.get(user.getId());
                if (set == null) {
                    played.put(user.getId(), set = new HashSet<>());
                }
                set.add(topicId);
            }
        }
        savePlayed();
        for (GameChat gameChat : gameChats) {
            if (gameChat.isFree()) {
                gameChat.setFree(false);
                bot.sendMessage(id, "Для игры пройдите по ссылке: " + gameChat.inviteLink);
                gameChat.startGame(this, id, topicSet, topics, currentGame);
                break;
            }
        }
    }

    public TelegramBot getGameBot() {
        return gameBot;
    }

    public void endGame(long origChatId, long chatId, String score) {
        bot.sendMessage(origChatId, "Игра завершена.\n" + score);
        for (GameChat gameChat : gameChats) {
            if (gameChat.chatId == chatId) {
                gameChat.setFree(true);
                for (User user: gameChat.getGameData().getPlayers()) {
                    kickPlayer(chatId, user);
                }
                for (User user: gameChat.getGameData().getSpectators()) {
                    kickPlayer(chatId, user);
                }
            }
        }
    }
}

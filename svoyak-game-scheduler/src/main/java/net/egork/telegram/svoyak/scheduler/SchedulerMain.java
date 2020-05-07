package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.TelegramBot;
import net.egork.telegram.svoyak.Utils;
import net.egork.telegram.svoyak.data.Data;
import net.egork.telegram.svoyak.data.Topic;
import net.egork.telegram.svoyak.data.TopicSet;
import net.egork.telegram.svoyak.game.Game;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static net.egork.telegram.svoyak.data.Data.DATA;

/**
 * @author egor@egork.net
 */
public class SchedulerMain {
    private static TelegramBotsApi botsApi;
    private static Log log = LogFactory.getLog(SchedulerMain.class);

    private TelegramBot bot;
    private TelegramBot gameBot;
    private GameChat[] gameChats = {
            new GameChat(-228225536L, "https://t.me/joinchat/GJNnBw2acgB5jCNmxQz7Cw"), //1
            new GameChat(-329127234L, "https://t.me/joinchat/GJNnBxOeFULm1IzyWvAppA"), //2
            new GameChat(-236588404L, "https://t.me/joinchat/GJNnBw4aDXTW7Y4ID9wP4w"), //3
            new GameChat(-245541916L, "https://t.me/joinchat/GJNnBw6irByTt84YdTUsOw"), //4
            new GameChat(-226855326L, "https://t.me/joinchat/GJNnBw2FiZ79oP-ZZU24ZQ"), //5
            new GameChat(-206514530L, "https://t.me/joinchat/GJNnBwxPKWLAf4_ZbAEGbw"), //6
            new GameChat(-168291881L, "https://t.me/joinchat/GJNnBwoH7imkOdRd_7WkPw"), //7
            new GameChat(-242420856L, "https://t.me/joinchat/GJNnBw5zDHhGqmzrvwV9dg"), //8
            new GameChat(-242471117L, "https://t.me/joinchat/GJNnBw5z0M0nBWY-Gm6lnQ"), //9
            new GameChat(-392693418L, "https://t.me/joinchat/GJNnBxdoBqpAnxZ02byu2w"), //10
            new GameChat(-342143810L, "https://t.me/joinchat/GJNnBxRks0Lnj2U_b4O15A"), //11
            new GameChat(-393136901L, "https://t.me/joinchat/GJNnBxduywU6FWKMLBPFqA"), //12
            new GameChat(-180816705L, "https://t.me/joinchat/GJNnBwrHC0EFM0jXROideA"), //13
            new GameChat(-378182305L, "https://t.me/joinchat/GJNnBxaKmqHFuUmkIo9WnA"), //14
            new GameChat(-325380559L, "https://t.me/joinchat/GJNnBxNk6c--y3u1cbQ46w"), //15
            new GameChat(-491132102L, "https://t.me/joinchat/GJNnBx1GFMZPQwtpds6zVg"), //16
            new GameChat(-449854638L, "https://t.me/joinchat/GJNnBxrQPK53QYrhhauJYQ"), //17
            new GameChat(-354533468L, "https://t.me/joinchat/GJNnBxUhwFyi_6eiIPvb1g"), //18
            new GameChat(-433467808L, "https://t.me/joinchat/GJNnBxnWMaBliBFTnjeAhQ"), //19
            new GameChat(-450200834L, "https://t.me/joinchat/GJNnBxrVhQKkL4YyeM82iQ"), //20
            new GameChat(-404974100L, "https://t.me/joinchat/GJNnBxgjahTu_r5Q5UqccA"), //21
            new GameChat(-462410294L, "https://t.me/joinchat/GJNnBxuP0jaiLxBFWAOC1w"), //22
            new GameChat(-392890526L, "https://t.me/joinchat/GJNnBxdrCJ4BN4mStvCzjw"), //23
            new GameChat(-414430406L, "https://t.me/joinchat/GJNnBxiztMYfkcNn6EQhkQ"), //24
            new GameChat(-442005867L, "https://t.me/joinchat/GJNnBxpYeWs6GkFeem2Otg"), //25
    };
    private boolean shuttingDown = false;

    private Executor executor = Executors.newSingleThreadExecutor();
    private Map<Long, ScheduleChat> chats = new HashMap<>();

    public static void main(String[] args) {
        ApiContextInitializer.init();
        botsApi = new TelegramBotsApi();
        new SchedulerMain().run();
    }

    private void run() {
        loadProperties();
        gameBot = new TelegramBot(System.getProperty("play.token"), "SvoyakPlayBot") {
            @Override
            protected void processMessage(Message message) {
                executor.execute(() -> processPlayMessage(message));
            }
        };
        executor.execute(() -> {
            for (GameChat gameChat : gameChats) {
                gameChat.loadLastGame(this);
            }
            System.err.println("Chats reloaded");
        });
        bot = new TelegramBot(System.getProperty("scheduler.token"), "SvoyakSchedulerBot") {
            @Override
            protected void processMessage(Message message) {
                executor.execute(() -> SchedulerMain.this.processMessage(message));
            }
        };
        try {
            botsApi.registerBot(bot);
            botsApi.registerBot(gameBot);
        } catch (TelegramApiRequestException e) {
            throw new RuntimeException(e);
        }
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                executor.execute(() -> {
                    if (System.currentTimeMillis() >= Data.DATA.getNextReset()) {
                        Data.DATA.ratingDiscount();
                        for (ScheduleChat chat : chats.values()) {
                            chat.ratingUpdate();
                        }
                        Data.DATA.updateNextReset();
                    }
                });
            }
        }, 0, 60000);
    }

    private void processPlayMessage(Message message) {
        if (message != null) {
            for (GameChat chat : gameChats) {
                if (chat.chatId == message.getChat().getId()) {
                    Game game = chat.getGame();
                    if (game != null) {
                        game.process(message);
                    }
                    return;
                }
            }
            gameBot.sendMessage(message.getChat().getId(), "Для игры пройдите в официальный канал - https://telegram.me/joinchat/BNC7RD7LLZ1gSQlsQh1NPw");
            System.err.println(message.getChat().getId());
        } else {
            log.warn("Null message");
        }
    }

    private void loadProperties() {
        Properties properties = new Properties();
        try {
            properties.load(Thread.currentThread().getContextClassLoader().
                    getResourceAsStream("application.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Enumeration propertyName = properties.propertyNames(); propertyName.hasMoreElements(); ) {
            String key = (String) propertyName.nextElement();
            System.getProperties().put(key, properties.getProperty(key));
        }
    }

    private void processMessage(Message message) {
        if (message == null) {
            return;
        }
        Chat chat = message.getChat();
        if (chat.isUserChat()) {
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
                User from = message.getFrom();
                if (from != null) {
                    if (from.getUserName() != null && (from.getUserName().equals("SvoyakPlayBot") || from.getUserName().equals("SvoyakSchedulerBot"))) {
                        return;
                    }
                    if (chat.isFree() || (!chat.getGameData().getPlayers().contains(new net.egork.telegram.svoyak.data.User(from)) && !chat.getGameData().getSpectators().contains(new net.egork.telegram.svoyak.data.User(from)))) {
                        bot.kickPlayer(chatId, from.getId());
                    }
                }
                return;
            }
        }
        ScheduleChat chat = chats.get(chatId);
        if (chat == null) {
            chats.put(chatId, chat = new ScheduleChat(chatId, this));
        }
        chat.processMessage(message);
    }

    private void kickIfNeeded(GameChat chat, User user) {
        if (chat.isFree() || (!chat.getGameData().getPlayers().contains(new net.egork.telegram.svoyak.data.User(user)) &&
                !chat.getGameData().getSpectators().contains(new net.egork.telegram.svoyak.data.User(user)))) {
            kickPlayer(chat.chatId, user.getId());
        }
    }

    private void kickPlayer(long chatId, int userId) {
        bot.kickPlayer(chatId, userId);
    }

    private void processPrivateMessage(Message message) {
        if (!isAuthorized(message.getFrom())) {
            bot.sendMessage(message.getChat().getId(),
                    "Для игры пройдите в официальный канал - https://telegram.me/joinchat/BNC7RD7LLZ1gSQlsQh1NPw");
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
                    DATA.addNewSet(id, set);
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
                    if (set == null || DATA.getSet(set) == null) {
                        bot.sendMessage(chatId, "Неизвестный пакет - " + set);
                    } else if (DATA.isActive(set)) {
                        bot.sendMessage(chatId, "Пакет уже включен");
                    } else {
                        DATA.enableSet(set);
                        bot.sendMessage(chatId, "Пакет включен - " + set);
                    }
                    break;
                case "/deactivate":
                case "выключить":
                    if (set == null || DATA.getSet(set) == null) {
                        bot.sendMessage(chatId, "Неизвестный пакет - " + set);
                    } else if (!DATA.isActive(set)) {
                        bot.sendMessage(chatId, "Пакет уже выключен");
                    } else {
                        DATA.disableSet(set);
                        bot.sendMessage(chatId, "Пакет выключен - " + set);
                    }
                    break;
                case "/alltopics":
                case "темы":
                    if (set == null || DATA.getSet(set) == null) {
                        bot.sendMessage(chatId, "Неизвестный пакет - " + set);
                    } else {
                        TopicSet topicSet = DATA.getSet(set);
                        StringBuilder list = new StringBuilder();
                        list.append("Список тем:\n");
                        for (int i = 0; i < topicSet.topics.size(); i++) {
                            list.append((i + 1)).append(". ").append(topicSet.byIndex(i).topicName).append("\n");
                        }
                        bot.sendMessage(chatId, list.toString());
                    }
                    break;
                case "/shutdown":
                case "выключение":
                    shuttingDown = true;
                    for (ScheduleChat chat : chats.values()) {
                        chat.shuttingDown();
                    }
                    break;
                default:
                    bot.sendMessage(chatId, "Неизвестная команда - " + command);
                    break;
            }
        }
    }

    private boolean isAuthorized(User from) {
        return "Kroge".equals(from.getUserName());
    }

    public TelegramBot getBot() {
        return bot;
    }

    public void tryStartNewGame(long id, GameData currentGame) {
        List<String> setIds = currentGame.getSetId() == null ? DATA.getActive() : Arrays.asList(currentGame.getSetId());
        for (String setId : setIds) {
            TopicSet topicSet = DATA.getSet(setId);
            if (topicSet == null) {
                bot.sendMessage(id, "Пакет был удален");
                return;
            }
            List<Integer> topics = new ArrayList<>();
            for (int i = 1; i <= topicSet.topics.size(); i++) {
                TopicId topicId = new TopicId(setId, i);
                boolean good = true;
                for (net.egork.telegram.svoyak.data.User user : currentGame.getPlayers()) {
                    Set<TopicId> set = DATA.getPlayed(user.getId(), user);
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
                continue;
            }
            for (int topic : topics) {
                TopicId topicId = new TopicId(setId, topic + 1);
                for (net.egork.telegram.svoyak.data.User user : currentGame.getPlayers()) {
                    DATA.addPlayed(user.getId(), topicId);
                }
                for (net.egork.telegram.svoyak.data.User user : currentGame.getSpectators()) {
                    DATA.addPlayed(user.getId(), topicId);
                }
            }
            DATA.commitPlayed();
            currentGame.setSetId(setId);
            boolean found = false;
            for (GameChat gameChat : gameChats) {
                if (gameChat.isFree()) {
                    gameChat.setFree(false);
                    found = true;
                    bot.sendMessage(id, "Для игры пройдите по ссылке: " + gameChat.inviteLink);
                    gameChat.startGame(this, id, topicSet, topics, currentGame);
                    break;
                }
            }
            if (!found) {
                bot.sendMessage(id, "На текущий момент свободных комнат нет");
            }
            return;
        }
        bot.sendMessage(id, "В текущем пакете недостаточно тем, которые бы не играли все игроки");
    }

    public TelegramBot getGameBot() {
        return gameBot;
    }

    public void endGame(long origChatId, TopicSet set, Map<Integer, Integer> score, Map<Integer, String> players, boolean aborted) {
        if (aborted) {
            bot.sendMessage(origChatId, "<b>Игра завершена.</b>\nПакет: " + set.shortName + "\n" + "Игра отменена.");
            return;
        }
        Map<Integer, Integer> currentRatings = new HashMap<>();
        for (Integer id : players.keySet()) {
            currentRatings.put(id, DATA.getRating(id));
        }
        DATA.updateRatings(score, players);
        Map<Integer, Integer> updatedRatings = new HashMap<>();
        for (Integer id : players.keySet()) {
            updatedRatings.put(id, DATA.getRating(id));
        }
        List<GameResultEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : players.entrySet()) {
            Integer key = entry.getKey();
            entries.add(new GameResultEntry(entry.getValue(), score.get(key), updatedRatings.get(key),
                    updatedRatings.get(key) - currentRatings.get(key)));
        }
        Collections.sort(entries);
        StringBuilder builder = new StringBuilder();
        for (GameResultEntry entry : entries) {
            builder.append(entry.name + " " + entry.points + " " + entry.rating + " (" + entry.delta + ")\n");
        }
        bot.sendMessage(origChatId, "<b>Игра завершена.</b>\nПакет: " + set.shortName + "\n" + builder.toString());
    }

    public void kickUsers(long chatId) {
        for (GameChat gameChat : gameChats) {
            if (gameChat.chatId == chatId) {
                gameChat.setFree(true);
                for (net.egork.telegram.svoyak.data.User user : gameChat.getGameData().getPlayers()) {
                    kickPlayer(chatId, user.getId());
                }
                for (net.egork.telegram.svoyak.data.User user : gameChat.getGameData().getSpectators()) {
                    kickPlayer(chatId, user.getId());
                }
            }
        }
    }

    private static class GameResultEntry implements Comparable<GameResultEntry> {
        public final String name;
        public final int points;
        public final int rating;
        public final int delta;

        public GameResultEntry(String name, int points, int rating, int delta) {
            this.name = name;
            this.points = points;
            this.rating = rating;
            this.delta = delta;
        }

        @Override
        public int compareTo(@NotNull GameResultEntry o) {
            return o.points - points;
        }
    }

    public String getGameStatus() {
        StringBuilder builder = new StringBuilder();
        for (GameChat chat : gameChats) {
            if (!chat.isFree()) {
                builder.append("\nИгра по пакету ").append(DATA.getSet(chat.getGameData().getSetId()).shortName);
                builder.append("\nИгроки: ").append(Utils.userList(chat.getGameData().getPlayers()));
                builder.append(chat.getGame().getStatus() == Game.State.AFTER_GAME ? "\nИгра окончена\n" :
                        ("\nТема " + Math.min(chat.getGame().getCurrentTopic() + 1, chat.getGameData().
                                getTopicCount()) + "/" + chat.getGameData().getTopicCount() + "\n"));
            }
        }
        return builder.toString();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }
}

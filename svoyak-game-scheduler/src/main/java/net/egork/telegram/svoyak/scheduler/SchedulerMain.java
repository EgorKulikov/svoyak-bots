package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.*;
import net.egork.telegram.svoyak.data.Topic;
import net.egork.telegram.svoyak.data.TopicSet;
import net.egork.telegram.svoyak.game.Game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static net.egork.telegram.svoyak.data.Data.*;

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
    private Map<Long, ScheduleChat> chats = new HashMap<>();

    public static void main(String[] args) {
        new SchedulerMain().run();
    }

    private void run() {
        loadProperties();
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

    private void loadProperties() {
        Properties properties = new Properties();
        try {
            properties.load(Thread.currentThread().getContextClassLoader().
                    getResourceAsStream("application.properties"));
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
            default:
                bot.sendMessage(chatId, "Неизвестная команда - " + command);
                break;
            }
        }
    }

    private boolean isAuthorized(User from) {
        return "Kroge".equals(from.getUsername());
    }

    public TelegramBot getBot() {
        return bot;
    }

    public void tryStartNewGame(long id, GameData currentGame) {
        List<String> setIds = currentGame.getSetId() == null ? DATA.getActive() : Arrays.asList(currentGame.getSetId());
        for (String setId : setIds) {
            TopicSet topicSet = DATA.getSet(currentGame.getSetId());
            if (topicSet == null) {
                bot.sendMessage(id, "Пакет был удален");
                return;
            }
            List<Integer> topics = new ArrayList<>();
            for (int i = 1; i <= topicSet.topics.size(); i++) {
                TopicId topicId = new TopicId(currentGame.getSetId(), i);
                boolean good = true;
                for (User user : currentGame.getPlayers()) {
                    Set<TopicId> set = DATA.getPlayed(user.getId());
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
                TopicId topicId = new TopicId(currentGame.getSetId(), topic + 1);
                for (User user : currentGame.getPlayers()) {
                    DATA.addPlayed(user.getId(), topicId);
                }
                for (User user : currentGame.getSpectators()) {
                    DATA.addPlayed(user.getId(), topicId);
                }
            }
            DATA.commitPlayed();
            currentGame.setSetId(setId);
            for (GameChat gameChat : gameChats) {
                if (gameChat.isFree()) {
                    gameChat.setFree(false);
                    bot.sendMessage(id, "Для игры пройдите по ссылке: " + gameChat.inviteLink);
                    gameChat.startGame(this, id, topicSet, topics, currentGame);
                    break;
                }
            }
            return;
        }
        bot.sendMessage(id, "В текущем пакете недостаточно тем, которые бы не играли все игроки");
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

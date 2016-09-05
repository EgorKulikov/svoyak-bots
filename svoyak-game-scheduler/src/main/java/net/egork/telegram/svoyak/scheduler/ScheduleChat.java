package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.Message;
import net.egork.telegram.svoyak.Utils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static net.egork.telegram.svoyak.data.Data.DATA;

/**
 * @author egor@egork.net
 */
public class ScheduleChat {
    private final long id;
    private final SchedulerMain scheduler;
    private Executor executor = Executors.newSingleThreadExecutor();
    private GameData currentGame;

    public ScheduleChat(long id, SchedulerMain scheduler) {
        this.id = id;
        this.scheduler = scheduler;
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                executor.execute(() -> onTimer());
            }
        }, 0L, 3000L);
    }

    private void onTimer() {
        if (currentGame != null && System.currentTimeMillis() - currentGame.getLastUpdated() > 5 * 60 * 1000) {
            currentGame = null;
            sendMessage("Игра отменена из-за отсутствия активности");
        }
    }

    private void sendMessage(String message) {
        scheduler.getBot().sendMessage(id, message);
    }

    public void processMessage(Message message) {
        String text = message.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        text = text.trim();
        String[] tokens = text.split(" ");
        String command = tokens[0].toLowerCase();
        String argument = tokens.length < 2 ? null : tokens[1];
        switch (command) {
        case "/game":
        case "игра":
            if (currentGame == null) {
                String lastSet = DATA.getLastSet();
                if (lastSet == null) {
                    sendMessage("Ни один пакет не активен");
                    return;
                }
                currentGame = new GameData();
                sendMessage(currentGame.toString());
            } else {
                sendMessage("Существует активная игра");
            }
            break;
        case "/set":
        case "пакет":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else if (!DATA.hasSet(argument)) {
                sendMessage("Пакет не обнаружен - " + argument);
            } else {
                currentGame.setSetId(argument);
                sendMessage(currentGame.toString());
            }
            break;
        case "/topics":
        case "темы":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else if (argument == null || !Utils.isNumber(argument, 1, 20)) {
                sendMessage("Некорректное число - " + argument);
            } else {
                currentGame.setTopicCount(Integer.parseInt(argument));
                sendMessage(currentGame.toString());
            }
            break;
        case "/minplayers":
        case "минигроков":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else if (argument == null || !Utils.isNumber(argument, 1, currentGame.getMaxPlayers())) {
                sendMessage("Некорректное число - " + argument);
            } else {
                currentGame.setMinPlayers(Integer.parseInt(argument));
                sendMessage(currentGame.toString());
            }
            break;
        case "/maxplayers":
        case "максигроков":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else if (argument == null || !Utils.isNumber(argument, Math.max(currentGame.getMinPlayers(),
                    currentGame.getPlayers().size()), 20)) {
                sendMessage("Некорректное число - " + argument);
            } else {
                currentGame.setMaxPlayers(Integer.parseInt(argument));
                sendMessage(currentGame.toString());
            }
            break;
        case "/register":
        case "регистрация":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else if (currentGame.getPlayers().size() == currentGame.getMaxPlayers()) {
                sendMessage("Все места заняты");
            } else {
                currentGame.addPlayer(message.getFrom());
                sendMessage(currentGame.toString());
            }
            break;
        case "/spectator":
        case "зритель":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else {
                currentGame.addSpectator(message.getFrom());
                sendMessage(currentGame.toString());
            }
            break;
        case "/unregister":
        case "отмена":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else {
                currentGame.unregister(message.getFrom());
                sendMessage(currentGame.toString());
            }
            break;
        case "/start":
        case "старт":
            if (currentGame == null) {
                sendMessage("Игра не начата");
            } else if (currentGame.getPlayers().size() < currentGame.getMinPlayers()) {
                sendMessage("Недостаточно игроков");
            } else {
                scheduler.tryStartNewGame(id, currentGame);
                currentGame = null;
            }
            break;
        case "/abort":
            sendMessage("Игра отменена");
            currentGame = null;
            break;
        case "/list":
        case "список":
            List<String> active = DATA.getActive();
            StringBuilder list = new StringBuilder();
            for (String id : active) {
                list.append(id).append(" - ").append(DATA.getSet(id).shortName).append("\n");
            }
            sendMessage("Список пакетов:\n" + list);
            break;
        case "/status":
        case "статус":
            String games = scheduler.getGameStatus();
            sendMessage((currentGame != null ? "Открыта регистрация\n" : "Регистрация не открыта\n") + games);
        default:
            break;
        }
    }

}

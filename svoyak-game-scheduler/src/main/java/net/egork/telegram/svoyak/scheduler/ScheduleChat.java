package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.svoyak.Utils;
import net.egork.telegram.svoyak.data.User;
import org.telegram.telegrambots.api.objects.Message;

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

    private void createGame() {
        if (currentGame != null) {
            return;
        }
        String lastSet = DATA.getLastSet();
        if (lastSet == null) {
            sendMessage("Ни один пакет не активен");
            return;
        }
        currentGame = new GameData();
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
        if (command.endsWith("@SvoyakSchedulerBot".toLowerCase())) {
            command = command.substring(0, command.length() - "@SvoyakSchedulerBot".length());
        }
        switch (command) {
            case "/help":
            case "помощь":
                sendMessage("Бот для спортивной своей игры. Команды:\n" +
                        "/game или игра - создает новую игру\n" +
                        "/set или пакет - задает пакет на которм будет идти игра\n" +
                        "/topics или темы - устанавливает число тем\n" +
                        "/minplayers или минигроков - устанавливает минимальное число игроков\n" +
                        "/maxplayers или максигроков - устанавливает максимальное число игроков\n" +
                        "/register, + или регистрация - регистриует на текщую игру и создает игру, если она не начата\n" +
                        "/spectator или зритель - регистрирует на текущую игру зрителем\n" +
                        "/unregister, - или отмена - отменяет регистрацию\n" +
                        "/start или старт - стартует текущую игру\n" +
                        "/abort - отменяет текущую игру\n" +
                        "/status или статус - выводит список идущих игр\n" +
                        "/rating или рейтинг - выводит таблицу ретинга\n" +
                        "/block - блокирует пакет. Пожалуйста, используйте только если вы ранее играли этот пакет. Отменить действие будет невозможно.\n" +
                        "\n" +
                        "Во время игры:\n" +
                        "\"+\" - Если вы хотите ответить на вопрос\n" +
                        "\"Да\" - если вы хотите подтвердить правильность СОБСТВЕННОГО ответа, не зачтенного автоматически. Не жмите \"да\" на чужие ответы.\n" +
                        "\"Нет\" - если вы по ошибке нажали \"Да\" и вам засчитали неправильный ответ.\n" +
                        "\"Пауза\" - приостановить игру\n" +
                        "\"Продолжить\" - продолжить игру.\n" +
                        "В режиме паузы можно исправить неверно посчитанные очки. Для этого следует ввести команду \n" +
                        "\"Исправить\" с параметром \"количество очков\"\n" +
                        "Например, если вы не успели на вопрос за 50 нажать \"Да\", то следует исправить 100 очков командой: Исправить 100\n" +
                        "В случае необходимости вычесть очки, просто поставьте минус перед параметром: Исправить -100"
                );
                break;
            case "/game":
            case "игра":
                if (currentGame == null) {
                    createGame();
                    if (currentGame != null) {
                        sendMessage(currentGame.toString());
                    }
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
            case "+":
                if (currentGame == null) {
                    createGame();
                }
                if (currentGame == null) {
                    break;
                }
                if (currentGame.getPlayers().size() == currentGame.getMaxPlayers()) {
                    sendMessage("Все места заняты");
                } else {
                    currentGame.addPlayer(new User(message.getFrom()));
                    sendMessage(currentGame.toString());
                }
                break;
            case "/spectator":
            case "зритель":
                if (currentGame == null) {
                    sendMessage("Игра не начата");
                } else {
                    currentGame.addSpectator(new User(message.getFrom()));
                    sendMessage(currentGame.toString());
                }
                break;
            case "/unregister":
            case "отмена":
            case "-":
                if (currentGame == null) {
                    sendMessage("Игра не начата");
                } else {
                    currentGame.unregister(new User(message.getFrom()));
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
                break;
            case "/rating":
            case "рейтинг":
                int top;
                if (argument == null || !Utils.isNumber(argument, 1, 1000)) {
                    top = 20;
                } else {
                    top = Integer.parseInt(argument);
                }
                sendMessage("<b>Рейтинг игроков:</b>\n" + DATA.getRatingList(top));
                break;
            case "/block":
                if (argument == null || DATA.getSet(argument) == null) {
                    sendMessage("Неизвестный пакет - " + argument);
                } else {
                    DATA.blockSet(message.getFrom().getId(), argument);
                    sendMessage("Пакет " + argument + " заблокирован для пользователя " + Utils.name(new User(message.getFrom())));
                }
            default:
                break;
        }
    }
}

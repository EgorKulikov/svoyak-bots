package net.egork.telegram;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.ParseMode;
import org.telegram.telegrambots.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.api.methods.groupadministration.KickChatMember;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * @author egor@egork.net
 */
public abstract class TelegramBot extends TelegramLongPollingBot {

    private Logger logger = LogManager.getLogger(TelegramBot.class);
    private final Map<Long, Long> nextTimeSlot = new HashMap<>();
    private final String token;
    private Executor executor;
    private final String name;

    public TelegramBot(String token, String name) {
        this.token = token;
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Executor for " + name));
        this.name = name;
    }

    public File getFile(String fileId) {
        try {
            GetFile args = new GetFile();
            args.setFileId(fileId);
            return getFile(args);
        } catch (TelegramApiException e) {
            logger.error(e);
            return null;
        }
    }

    private Message sendMessageImpl(long chatId, String text, ReplyKeyboard rkm) {
        SendMessage args = new SendMessage();
        args.setChatId(chatId);
        args.setText(text);
        args.setParseMode(ParseMode.HTML);
        args.setReplyMarkup(rkm);
        int tries = 0;
        while (true) {
            if (tries == 20) {
                return null;
            }
            tries++;
            try {
                return sendMessage(args);
            } catch (TelegramApiException e) {
                logger.error(e);
            }
        }
    }

    public void sendMessage(long chatId, String text, String[] keyboard) {
        sendMessage(chatId, text, keyboard, null);
    }

    public void sendMessage(long chatId, String text, Consumer<Integer> callback) {
        sendMessage(chatId, text, null, callback);
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null, null);
    }


    public void sendMessage(long chatId, String text, String[] keyboard, Consumer<Integer> callback) {
        executor.execute(() -> sendMessageImpl(chatId, text, keyboard, callback));
    }

    private void sendMessageImpl(long chatId, String text, String[] keyboard, Consumer<Integer> callback) {
        if (!nextTimeSlot.containsKey(chatId)) {
            nextTimeSlot.put(chatId, 0L);
        }
        if (System.currentTimeMillis() < nextTimeSlot.get(chatId)) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    executor.execute(() -> sendMessageImpl(chatId, text, keyboard, callback));
                }
            }, Math.max(0, nextTimeSlot.get(chatId) - System.currentTimeMillis()) + 100);
            return;
        }
        if (text.length() > 4096) {
            int at = text.substring(0, 4096).lastIndexOf('\n');
            if (at == -1) {
                at = 4096;
            }
            sendMessageImpl(chatId, text.substring(0, at), keyboard, null);
            sendMessageImpl(chatId, text.substring(at), keyboard, callback);
            return;
        }
        ReplyKeyboard markup;
        if (keyboard == null) {
            markup = new ReplyKeyboardRemove();
        } else if (keyboard.length != 0) {
            markup = new ReplyKeyboardMarkup();
            KeyboardRow row = new KeyboardRow();
            for (String button : keyboard) {
                row.add(button);
            }
            ((ReplyKeyboardMarkup)markup).setKeyboard(Collections.singletonList(row));
        } else {
            markup = null;
        }
        Message message = sendMessageImpl(chatId, text, markup);
        nextTimeSlot.put(chatId, System.currentTimeMillis() + 1000);
        if (callback != null) {
            callback.accept(message == null ? 0 : message.getMessageId());
        }
    }

    public void kickPlayer(long chatId, int userId) {
        executor.execute(() -> {
            try {
                GetChatMember args = new GetChatMember();
                args.setChatId(chatId);
                args.setUserId(userId);
                ChatMember chatMember = getChatMember(args);
                if (chatMember.getStatus().equals(MemberStatus.MEMBER)) {
                    KickChatMember member = new KickChatMember();
                    member.setChatId(chatId);
                    member.setUserId(userId);
                    kickMember(member);
                }
            } catch (TelegramApiException e) {
                logger.error(e);
            }
        });
    }

    protected abstract void processMessage(Message message);

    public void editMessage(long chatId, int messageId, String text) {
        executor.execute(() -> {
            try {
                EditMessageText args = new EditMessageText();
                args.setChatId(chatId);
                args.setMessageId(messageId);
                args.setText(text);
                args.setParseMode(ParseMode.HTML);
                editMessageText(args);
            } catch (TelegramApiException e) {
                logger.error(e);
            }
        });
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        executor.execute(() -> processMessage(update.getMessage()));
    }

    @Override
    public String getBotUsername() {
        return name;
    }
}

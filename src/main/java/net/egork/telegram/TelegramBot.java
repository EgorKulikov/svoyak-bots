package net.egork.telegram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * @author egor@egork.net
 */
public abstract class TelegramBot {
    public static final int MAX_BACKOFF = 5000;
    private final String apiUri;
    private CloseableHttpClient client = createClient();

    private CloseableHttpClient createClient() {
        return HttpClientBuilder.create().setDefaultRequestConfig(
                RequestConfig.custom().setConnectTimeout(2000).setConnectionRequestTimeout(2000).build()
        ).build();
    }

    private final ObjectMapper mapper = new ObjectMapper()
                .registerModules(new KotlinModule())
                .setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private Integer offset;
    private Logger logger = LogManager.getLogger(TelegramBot.class);
    private final Map<Long, Long> nextTimeSlot = new HashMap<>();
    private Executor executor;

    public TelegramBot(String token, String name) {
        apiUri = "https://api.telegram.org/bot" + token + "/";
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Executor for " + name));
        new Thread(() -> {
            try {
                int delay = 100;
                int tries = 0;
                while (true) {
                    tries++;
                    Thread.sleep(delay);
                    if (tries % 10 == 0) {
                        try {
                            client.close();
                        } catch (IOException e) {
                            logger.catching(e);
                        }
                        client = createClient();
                    }
                    List<Update> updates;
                    try {
                        updates = getUpdates();
                    } catch (Throwable e) {
                        logger.error(e);
                        continue;
                    }
                    if (updates == null) {
                        delay = Math.min(2 * delay, MAX_BACKOFF);
                        continue;
                    }
                    tries = 0;
                    delay = 100;
                    for (Update update : updates) {
                        if (update.getMessage() != null) {
                            executor.execute(() -> processMessage(update.getMessage()));
                        }
                        offset = update.getUpdateId() + 1;
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, "Telegram bot " + name).start();
    }

    private List<Update> getUpdates() {
        GetUpdatesArgs args = new GetUpdatesArgs(offset, null, null);
        JsonNode result = apiRequest("getUpdates", args);
        if (result == null || !result.isArray()) {
            logger.error("update failed");
            return null;
        }
        return convertToList(result, Update.class);
    }

    private <T>List<T> convertToList(JsonNode result, Class<T> aClass) {
        List<T> list = new ArrayList<T>();
        result.forEach(x -> {
            try {
                list.add(mapper.treeToValue(x, aClass));
            } catch (JsonProcessingException e) {
                throw new RuntimeException();
            }
        });
        return list;
    }

    private JsonNode apiRequest(String method, Object args) {
        try {
            byte[] dataBytes = mapper.writeValueAsBytes(args);

            HttpPost request = new HttpPost(apiUri + method);
            request.setEntity(new ByteArrayEntity(dataBytes, ContentType.APPLICATION_JSON));

            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                return null;
            }
            JsonNode tree = mapper.readTree(response.getEntity().getContent());
            if (!tree.get("ok").asBoolean()) {
                logger.error(method + ": " + tree);
                return null;
            }
            return tree.get("result");
        } catch (JsonProcessingException | ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            return null;
        }
    }

    public File getFile(String fileId) {
        GetFileArgs args = new GetFileArgs(fileId);

        JsonNode result = apiRequest("getFile", args);
        if (result == null || !result.isObject()) {
            logger.error("no file returned");
            return null;
        }
        try {
            return mapper.treeToValue(result, File.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Message sendMessageImpl(long chatId, String text, Object rkm) {
        long time = System.currentTimeMillis();
        SendMessageArgs args = new SendMessageArgs(chatId, text, ParseMode.HTML, rkm);

        JsonNode result = apiRequest("sendMessage", args);
        int backOff = 1000;
        int tries = 0;
        while (result == null || !result.isObject()) {
            logger.error("message was not sent, retrying");
            tries++;
            if (tries == 20) {
                return null;
            }
            if (tries % 5 == 0) {
                try {
                    client.close();
                } catch (IOException e) {
                    logger.catching(e);
                }
            }
            client = createClient();
            try {
                Thread.sleep(backOff);
                backOff = Math.min(2 * backOff, MAX_BACKOFF);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            result = apiRequest("sendMessage", args);
        }

        Message message;

        try {
            message = mapper.treeToValue(result, Message.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        long elapsed = System.currentTimeMillis() - time;
        if (elapsed >= 2000) {
            logger.debug("Message %s took %.2f to send", text, elapsed / 1000d);
        }
        return message;
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
            }, nextTimeSlot.get(chatId) - System.currentTimeMillis() + 100);
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
        Message message = sendMessageImpl(chatId, text, keyboard != null && keyboard.length != 0 ?
                new ReplyKeyboardMarkup(new String[][]{keyboard}, null, true, null) :
                (keyboard == null ? new ReplyKeyboardHide(true, null) : null));
        nextTimeSlot.put(chatId, System.currentTimeMillis() + 1000);
        if (callback != null) {
            callback.accept(message == null ? 0 : message.getMessageId());
        }
    }

    public void kickPlayer(long chatId, int userId) {
        executor.execute(() -> {
            KickArgs args = new KickArgs(chatId, userId);
            apiRequest("kickChatMember", args);
        });
    }

    protected abstract void processMessage(Message message);

    public void editMessage(long chatId, int messageId, String text) {
        executor.execute(() -> {
            EditMessageArgs args = new EditMessageArgs(chatId, messageId, text, ParseMode.HTML);
            apiRequest("editMessageText", args);
        });
    }
}

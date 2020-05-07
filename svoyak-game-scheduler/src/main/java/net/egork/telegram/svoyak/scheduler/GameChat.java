package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.svoyak.data.TopicSet;
import net.egork.telegram.svoyak.data.User;
import net.egork.telegram.svoyak.game.Game;

import java.io.*;
import java.util.List;

import static net.egork.telegram.svoyak.data.Data.DATA;

/**
 * @author egor@egork.net
 */
public class GameChat {
    public final long chatId;
    public final String inviteLink;
    private boolean isFree = true;
    private Game currentGame;
    private GameData gameData;

    public GameChat(long chatId, String inviteLink) {
        this.chatId = chatId;
        this.inviteLink = inviteLink;
    }

    public boolean isFree() {
        return isFree;
    }

    public void setFree(boolean free) {
        if (free) {
            currentGame = null;
        }
        isFree = free;
    }

    public void startGame(SchedulerMain scheduler, long id, TopicSet topicSet, List<Integer> topics, GameData game) {
        gameData = game;
        currentGame = new Game(scheduler, id, this, topicSet, topics, game.getPlayers());
    }

    public Game getGame() {
        return currentGame;
    }

    public GameData getGameData() {
        return gameData;
    }

    public void saveState() {
        if (isFree) {
            System.err.println("No current game");
            return;
        }
        try {
            PrintWriter pw = new PrintWriter(getFileName());
            gameData.saveState(pw);
            currentGame.saveState(pw);
            pw.close();
        } catch (IOException e) {
            System.err.println("Problem saving state for " + chatId);
        }
    }

    private boolean loadState(SchedulerMain schedulerMain) {
        File file = new File(getFileName());
        if (!file.exists()) {
            return false;
        }
        try {
            isFree = false;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            gameData = GameData.loadState(reader);
            currentGame = new Game(schedulerMain, this, DATA.getSet(gameData.getSetId()));
            currentGame.loadState(reader, gameData.getPlayers());
            return true;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            System.err.println("Error loading " + chatId);
            gameData = null;
            currentGame = null;
            isFree = true;
            return false;
        }
    }

    public static void saveNullableData(PrintWriter pw, String label, Object data) {
        pw.println(label);
        pw.println(data == null ? 0 : 1);
        if (data != null) {
            if (data instanceof User) {
                ((User) data).saveUser(pw);
            } else {
                pw.println(data);
            }
        }
    }

    public static void saveData(PrintWriter pw, String label, Object data) {
        pw.println(label);
        if (data instanceof User) {
            ((User) data).saveUser(pw);
        } else {
            pw.println(data);
        }
    }

    public static void expectLabel(BufferedReader in, String label) throws IOException {
        String next = in.readLine();
        if (!label.equals(next)) {
            throw new IllegalStateException();
        }
    }

    public static String readData(BufferedReader in, String label) throws IOException {
        expectLabel(in, label);
        return in.readLine();
    }

    public static String readNullableData(BufferedReader in, String label) throws IOException {
        expectLabel(in, label);
        if (in.readLine().equals("0")) {
            return null;
        }
        return in.readLine();
    }

    private String getFileName() {
        return Math.abs(chatId) + ".state";
    }

    public void removeSavedState() {
        File state = new File(getFileName());
        state.delete();
    }

    public void loadLastGame(SchedulerMain schedulerMain) {
        loadState(schedulerMain);
    }
}

package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.svoyak.Utils;
import net.egork.telegram.svoyak.data.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author egor@egork.net
 */
public class GameData {
    private String setId;
    private int topicCount = 6;
    private int minPlayers = 3;
    private int maxPlayers = 4;
    private List<net.egork.telegram.svoyak.data.User> players = new ArrayList<>();
    private List<net.egork.telegram.svoyak.data.User> spectators = new ArrayList<>();
    private User judge = null;
    private long lastUpdated = System.currentTimeMillis();

    public GameData() {
        lastUpdated = System.currentTimeMillis();
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId;
        lastUpdated = System.currentTimeMillis();
    }

    public int getTopicCount() {
        return topicCount;
    }

    public void setTopicCount(int topicCount) {
        this.topicCount = topicCount;
        lastUpdated = System.currentTimeMillis();
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
        lastUpdated = System.currentTimeMillis();
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        lastUpdated = System.currentTimeMillis();
    }

    public List<net.egork.telegram.svoyak.data.User> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(net.egork.telegram.svoyak.data.User user) {
        unregister(user);
        players.add(user);
        lastUpdated = System.currentTimeMillis();
    }

    public void addSpectator(net.egork.telegram.svoyak.data.User user) {
        unregister(user);
        spectators.add(user);
        lastUpdated = System.currentTimeMillis();
    }

    public List<net.egork.telegram.svoyak.data.User> getSpectators() {
        return Collections.unmodifiableList(spectators);
    }

    public void unregister(net.egork.telegram.svoyak.data.User user) {
        spectators.remove(user);
        players.remove(user);
        lastUpdated = System.currentTimeMillis();
    }

    public User getJudge() {
        return judge;
    }

    public void setJudge(User judge) {
        this.judge = judge;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public String toString() {
        return (setId == null ? "Стандартная игра" : ("Игра по пакету " + setId)) + "\n" +
                "Тем - " + topicCount + "\n" +
                "Игроков - " + minPlayers + "-" + maxPlayers + "\n" +
                "Игроки: " + Utils.userList(players) + "\n" +
                "Зрители: " + Utils.userList(spectators);
    }

    public void saveState(PrintWriter pw) {
        pw.println("Game Data");
        GameChat.saveData(pw, "set id", setId);
        GameChat.saveData(pw, "topic count", topicCount);
        GameChat.saveData(pw, "players", players.size());
        for (User player : players) {
            GameChat.saveData(pw, "player", player);
        }
        GameChat.saveData(pw, "spectators", spectators.size());
        for (User spectator : spectators) {
            GameChat.saveData(pw, "spectator", spectator);
        }
        GameChat.saveNullableData(pw, "judge", judge);
    }

    public static GameData loadState(BufferedReader in) throws IOException {
        GameChat.expectLabel(in, "Game Data");
        GameData gameData = new GameData();
        gameData.setSetId(GameChat.readData(in, "set id"));
        gameData.setTopicCount(Integer.parseInt(GameChat.readData(in, "topic count")));
        int playerCount = Integer.parseInt(GameChat.readData(in, "players"));
        for (int i = 0; i < playerCount; i++) {
            gameData.addPlayer(User.readUser(in, "player"));
        }
        int specCount = Integer.parseInt(GameChat.readData(in, "spectators"));
        for (int i = 0; i < specCount; i++) {
            gameData.addPlayer(User.readUser(in, "spectator"));
        }
        gameData.setJudge(User.readNullableUser(in, "judge"));
        return gameData;
    }
}

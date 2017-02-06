package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.svoyak.Utils;
import org.telegram.telegrambots.api.objects.User;

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
    private List<User> players = new ArrayList<>();
    private List<User> spectators = new ArrayList<>();
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

    public List<User> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(User user) {
        unregister(user);
        players.add(user);
        lastUpdated = System.currentTimeMillis();
    }

    public void addSpectator(User user) {
        unregister(user);
        spectators.add(user);
        lastUpdated = System.currentTimeMillis();
    }

    public List<User> getSpectators() {
        return Collections.unmodifiableList(spectators);
    }

    public void unregister(User user) {
        spectators.remove(user);
        players.remove(user);
        lastUpdated = System.currentTimeMillis();
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
}

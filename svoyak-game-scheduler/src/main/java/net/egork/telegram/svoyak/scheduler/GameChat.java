package net.egork.telegram.svoyak.scheduler;

import net.egork.telegram.svoyak.data.TopicSet;
import net.egork.telegram.svoyak.game.Game;

import java.util.List;

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
        currentGame = new Game(scheduler, id, chatId, topicSet, topics, game.getPlayers());
    }

    public Game getGame() {
        return currentGame;
    }

    public GameData getGameData() {
        return gameData;
    }
}

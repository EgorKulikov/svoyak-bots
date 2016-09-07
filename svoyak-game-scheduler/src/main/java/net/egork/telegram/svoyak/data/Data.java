package net.egork.telegram.svoyak.data;

import net.egork.telegram.svoyak.scheduler.TopicId;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * @author egor@egork.net
 */
public class Data {
    public static final Data DATA = new Data();

    private List<String> activePackages = new ArrayList<>();
    private List<String> allPackages = new ArrayList<>();
    private Map<String, TopicSet> sets = new HashMap<>();
    private Map<Integer, Set<TopicId>> played = new HashMap<>();
    private Map<Integer, String> players = new HashMap<>();
    private Map<Integer, Integer> rating = new HashMap<>();

    private Data() {
        loadList("active.list", activePackages);
        loadList("all.list", allPackages);
        loadPlayers();
        loadSets();
        loadPlayed();
    }

    private void loadPlayers() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("player.list"));
            String s;
            while ((s = reader.readLine()) != null) {
                int userId = Integer.parseInt(s);
                s = reader.readLine();
                String name = s;
                s = reader.readLine();
                int rat = Integer.parseInt(s);
                players.put(userId, name);
                rating.put(userId, rat);
            }
            reader.close();
        } catch (IOException ignored) {
        }
    }

    private void savePlayers() {
        try {
            PrintWriter out = new PrintWriter("player.list");
            for (Map.Entry<Integer, String> entry : players.entrySet()) {
                out.println(entry.getKey());
                out.println(entry.getValue());
                out.println(rating.get(entry.getKey()));
            }
            out.close();
        } catch (IOException ignored) {
        }
    }

    private void loadSets() {
        for (String s : allPackages) {
            try {
                TopicSet set = TopicSet.parseReader(new BufferedReader(new FileReader(s)));
                sets.put(s, set);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void loadList(String fileName, List<String> list) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            String s;
            while ((s = reader.readLine()) != null) {
                list.add(s.trim());
            }
            reader.close();
        } catch (IOException ignored) {
        }
    }

    private void loadPlayed() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("played.list"));
            String s;
            while ((s = reader.readLine()) != null) {
                int userId = Integer.parseInt(s);
                s = reader.readLine();
                int count = Integer.parseInt(s);
                Set<TopicId> set = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    String id = reader.readLine();
                    int topic = Integer.parseInt(reader.readLine());
                    set.add(new TopicId(id, topic));
                }
                played.put(userId, set);
            }
            reader.close();
        } catch (IOException ignored) {
        }
    }

    public void addNewSet(String id, TopicSet set) {
        sets.put(id, set);
        allPackages.remove(id);
        activePackages.remove(id);
        allPackages.add(id);
        saveList("all.list", allPackages);
        saveList("active.list", activePackages);
        saveSet(id, set);
    }

    private void saveSet(String id, TopicSet set) {
        try {
            PrintWriter out = new PrintWriter(id);
            set.saveSet(out);
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveList(String fileName, List<String> list) {
        try {
            PrintWriter out = new PrintWriter(fileName);
            for (String s : list) {
                out.println(s);
            }
            out.close();
        } catch (IOException ignored) {
        }
    }

    public String getLastSet() {
        if (activePackages.isEmpty()) {
            return null;
        }
        return activePackages.get(activePackages.size() - 1);
    }

    public boolean hasSet(String argument) {
        return allPackages.contains(argument);
    }

    public TopicSet getSet(String id) {
        return sets.get(id);
    }

    public Set<TopicId> getPlayed(int id) {
        return played.get(id);
    }

    public void addPlayed(int id, TopicId topicId) {
        if (!played.containsKey(id)) {
            played.put(id, new HashSet<>());
        }
        played.get(id).add(topicId);
    }

    private void savePlayed() {
        try {
            PrintWriter out = new PrintWriter("played.list");
            for (Map.Entry<Integer, Set<TopicId>> entry : played.entrySet()) {
                out.println(entry.getKey());
                out.println(entry.getValue().size());
                for (TopicId topicId : entry.getValue()) {
                    out.println(topicId.setId);
                    out.println(topicId.topic);
                }
            }
            out.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isActive(String set) {
        return activePackages.contains(set);
    }

    public void enableSet(String set) {
        activePackages.add(set);
        saveList("active.list", activePackages);
    }

    public void disableSet(String set) {
        activePackages.remove(set);
        saveList("active.list", activePackages);
    }

    public void commitPlayed() {
        savePlayed();
    }

    public List<String> getActive() {
        return activePackages;
    }

    public int getRating(int id) {
        if (rating.containsKey(id)) {
            return rating.get(id);
        }
        return 1500;
    }

    public void updateRatings(Map<Integer, Integer> score, Map<Integer, String> names) {
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            players.put(entry.getKey(), entry.getValue());
        }
        Map<Integer, Integer> updated = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : score.entrySet()) {
            updated.put(entry.getKey(), getRating(entry.getKey()));
        }
        for (Map.Entry<Integer, Integer> entry1 : score.entrySet()) {
            for (Map.Entry<Integer, Integer> entry2 : score.entrySet()) {
                if (entry1 == entry2) {
                    break;
                }
                int ra = getRating(entry1.getKey());
                int rb = getRating(entry2.getKey());
                double ea = 1 / (1 + Math.pow(10, (rb - ra) / 400d));
                double eb = 1 - ea;
                double sa = entry1.getValue() < entry2.getValue() ? 0 : entry1.getValue() > entry2.getValue() ? 1 : 0.5;
                double sb = 1 - sa;
                int da = (int) Math.round(10 * (sa - ea));
                int db = (int) Math.round(10 * (sb - eb));
                updated.put(entry1.getKey(), updated.get(entry1.getKey()) + da);
                updated.put(entry2.getKey(), updated.get(entry2.getKey()) + db);
            }
        }
        for (Map.Entry<Integer, Integer> entry : updated.entrySet()) {
            rating.put(entry.getKey(), Math.max(1, entry.getValue()));
        }
        savePlayers();
    }

    public String getRatingList() {
        List<RatingEntry> list = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : players.entrySet()) {
            list.add(new RatingEntry(entry.getValue(), rating.get(entry.getKey())));
        }
        Collections.sort(list);
        StringBuilder builder = new StringBuilder();
        for (RatingEntry entry : list) {
            builder.append(entry.name + " " + entry.rating + "\n");
        }
        return builder.toString();
    }

    private static class RatingEntry implements Comparable<RatingEntry> {
        public final String name;
        public final int rating;

        private RatingEntry(String name, int rating) {
            this.name = name;
            this.rating = rating;
        }

        @Override
        public int compareTo(@NotNull RatingEntry o) {
            return o.rating - rating;
        }
    }
}

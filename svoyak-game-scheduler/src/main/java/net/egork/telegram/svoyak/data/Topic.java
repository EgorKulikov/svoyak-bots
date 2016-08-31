package net.egork.telegram.svoyak.data;

import java.util.List;

/**
 * @author egor@egork.net
 */
public class Topic {
    public String topicName;
    public List<Question> questions;

    public Topic(String topicName, List<Question> questions) {
        this.topicName = topicName;
        this.questions = questions;
    }

    public Question next(Question current) {
        int at = questions.indexOf(current);
        if (at == -1 || at == questions.size() - 1) {
            return null;
        }
        return questions.get(at + 1);
    }

    public Question first() {
        return questions.get(0);
    }

    public static String getTopicWord(int topics) {
        if (topics % 10 == 0 || topics % 10 >= 5 || topics % 100 >= 10 && topics % 100 < 20) {
            return "тем";
        }
        if (topics % 10 == 1) {
            return "тема";
        }
        return "темы";
    }
}

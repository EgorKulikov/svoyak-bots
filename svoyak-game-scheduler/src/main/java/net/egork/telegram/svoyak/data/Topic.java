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
        for (int i = 0; i < questions.size() - 1; i++) {
            if (questions.get(i).cost == current.cost) {
                return questions.get(i + 1);
            }
        }
        return null;
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

    public Question byCost(int questionCost) {
        for (Question question : questions) {
            if (question.cost == questionCost) {
                return question;
            }
        }
        return null;
    }
}

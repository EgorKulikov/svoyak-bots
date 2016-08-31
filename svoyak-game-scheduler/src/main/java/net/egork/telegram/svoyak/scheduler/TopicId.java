package net.egork.telegram.svoyak.scheduler;

/**
 * @author egor@egork.net
 */
public class TopicId {
    public final String setId;
    public final int topic;

    public TopicId(String setId, int topic) {
        this.setId = setId;
        this.topic = topic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TopicId topicId = (TopicId) o;

        if (topic != topicId.topic) {
            return false;
        }
        return setId.equals(topicId.setId);
    }

    @Override
    public int hashCode() {
        int result = setId.hashCode();
        result = 31 * result + topic;
        return result;
    }
}

package net.egork.telegram.svoyak.data;

//import org.apache.commons.lang.StringEscapeUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author egor@egork.net
 */
public class TopicSet {
    public String shortName;
    public String description;
    public List<Topic> topics;

    private TopicSet(String shortName, String description, List<Topic> topics) {
        this.shortName = shortName;
        this.description = description;
        this.topics = topics;
    }

    public Topic byIndex(int index) {
        if (index < 0 || index >= topics.size()) {
            return null;
        }
        return topics.get(index);
    }

    public static TopicSet parse(String url) {
        if (!url.startsWith("http")) {
            return parseText(url);
        }
        String text = readPage(url);
        if (text == null) {
            return null;
        }
        try {
            StringParser parser = new StringParser(text);
            parser.advance(true, "<h2 class=\"content-title\">");
            String description = prettify(parser.advance(false, "</h2>"));
            List<Topic> topics = new ArrayList<Topic>();
            while (parser.advanceIfPossible(true, "<div style=\"margin-top:20px;\">") != null) {
                parser.advance(true, "</a></strong>:");
                String topicTitle = prettify(parser.advance(false, "<p>").trim());
                StringBuilder s = new StringBuilder();
                boolean skip = false;
                for (int i = 0; i < topicTitle.length(); i++) {
                    if (topicTitle.charAt(i) == '<') {
                        skip = true;
                    }
                    if (!skip) {
                        s.append(topicTitle.charAt(i));
                    }
                    if (topicTitle.charAt(i) == '>') {
                        skip = false;
                    }
                }
                topicTitle = s.toString();
                List<Question> questions = new ArrayList<Question>();
                for (int i = 0; i < 5; i++) {
                    parser.advance(true, ". ");
                    String question = prettify(parser.advance(false, "</p>"));
                    parser.advance(true, "<p><i>");
                    parser.advance(true, "</i>");
                    String answer = prettify(strip(parser.advance(false, "</p>").trim()));
                    questions.add(new Question((i + 1) * 10, question, Collections.singletonList(answer)));
                }
                topics.add(new Topic(topicTitle, questions));
            }
            return new TopicSet(description, "", topics);
        } catch (Exception e) {
            return null;
        }
    }

    private static TopicSet parseText(String name) {
        try {
            return parseReader(new BufferedReader(new FileReader(name + ".si")));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static TopicSet parseReader(BufferedReader reader) {
        List<String> builder = new ArrayList<>();
        try {
            String s;
            while ((s = reader.readLine()) != null) {
                builder.add(s);
            }
        } catch (IOException e) {
            return null;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (builder.isEmpty()) {
            return null;
        }
        if (builder.get(0).trim().toLowerCase().contains("доктор")) {
            return parseDoctor(builder);
        } else if (builder.get(0).trim().toLowerCase().contains("стандарт") || builder.get(0).trim().toLowerCase()
                .contains("своя игра")) {
            return newParseStandard(builder);
        }
        return null;
    }

    public void saveSet(PrintWriter out) {
        out.println("своя игра");
        out.println();
        out.println(shortName);
        out.println(description);
        for (Topic topic : topics) {
            out.println();
            out.println("Тема " + topic.topicName);
            for (int i = 0; i < 5; i++) {
                Question question = topic.questions.get(i);
                out.println(question.cost + ". " + question.question);
                boolean first = true;
                for (String answer : question.answers) {
                    out.println((first ? "Ответ: " : "Зачет: ") + answer);
                    first = false;
                }
                if (!question.comment.isEmpty()) {
                    out.println("Комментарий: " + question.comment);
                }
            }
        }
    }

    static class StringIterator {
        int at;
        List<String> data;

        public StringIterator(List<String> data) {
            this.data = data;
        }

        public String peek() {
            while (at < data.size() && data.get(at).trim().isEmpty()) {
                at++;
            }
            if (at >= data.size()) {
                return null;
            }
            return data.get(at).trim();
        }

        public String next() {
            String result = peek();
            at++;
            return result;
        }
    }

    private static TopicSet newParseStandard(List<String> data) {
        List<Topic> topics = new ArrayList<>();
        String name = null;
        String description = null;
        List<String> lastTopic = new ArrayList<>();
        boolean first = true;
        for (String s : data) {
            if (first) {
                first = false;
                continue;
            }
            if (s.trim().isEmpty()) {
                if (lastTopic.isEmpty()) {
                    continue;
                }
                if (name == null) {
                    description = "";
                    for (String str : lastTopic) {
                        if (name == null) {
                            name = str;
                        } else {
                            if (!description.isEmpty()) {
                                description += "\n";
                            }
                            description += str;
                        }
                    }
                    if (name == null) {
                        return null;
                    }
                } else {
                    Topic topic = parse(lastTopic);
                    if (topic != null) {
                        topics.add(topic);
                    }
                }
                lastTopic.clear();
            } else {
                lastTopic.add(s.trim());
            }
        }
        Topic topic = parse(lastTopic);
        if (topic != null) {
            topics.add(topic);
        }
        return new TopicSet(name, description, topics);
    }

    private static Topic parse(List<String> data) {
        for (int multiplier = 1; multiplier <= 100; multiplier++) {
            int current = 1;
            for (String s : data) {
                String prefix = Integer.toString(multiplier * current);
                if (s.startsWith(prefix + " ") || s.startsWith(prefix + ".") || s.startsWith(prefix + ":")) {
                    current++;
                }
            }
            List<Question> questions = new ArrayList<>();
            if (current > 5) {
                current = 5;
                int last = data.size();
                for (int i = data.size() - 1; i >= 0 && current > 0; i--) {
                    String prefix = Integer.toString(multiplier * current);
                    String s = data.get(i);
                    if (s.startsWith(prefix + " ") || s.startsWith(prefix + ".") || s.startsWith(prefix + ":")) {
                        Question q = parseQuestion(data.subList(i, last), multiplier * current, 10 * current);
                        if (q.answers.isEmpty()) {
                            for (String ss : data) {
                                System.err.println(ss);
                            }
                            System.err.println();
                            return null;
                        }
                        questions.add(q);
                        last = i;
                        current--;
                    }
                }
                Collections.reverse(questions);
                String name = "";
                for (int i = 0; i < last; i++) {
                    if (!name.isEmpty()) {
                        name += "\n";
                    }
                    name += data.get(i);
                }
                name = name.trim();
                if (name.toLowerCase().startsWith("тема")) {
                    name = name.substring(4).trim();
                }
                while (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
                    name = name.substring(1).trim();
                }
                if (name.startsWith(".") || name.startsWith(":")) {
                    name = name.substring(1).trim();
                }
                return new Topic(name, questions);
            }
        }
        if (data.size() >= 5) {
            for (String ss : data) {
                System.err.println(ss);
            }
            System.err.println();
        }
        return null;
    }

    private static Question parseQuestion(List<String> data, int cost, int realCost) {
        String question = "";
        for (String s : data) {
            if (!s.isEmpty()) {
                s += "\n";
            }
            question += s;
        }
        question = question.trim();
        question = question.substring(Integer.toString(cost).length()).trim();
        if (question.startsWith(".") || question.startsWith(":")) {
            question = question.substring(1).trim();
        }
        if (question.toLowerCase().contains("источник:")) {
            question = question.substring(0, question.toLowerCase().indexOf("источник:")).trim();
        }
        if (question.toLowerCase().contains("источник.")) {
            question = question.substring(0, question.toLowerCase().indexOf("источник.")).trim();
        }
        if (question.toLowerCase().contains("запас.")) {
            question = question.substring(0, question.toLowerCase().indexOf("запас.")).trim();
        }
        if (question.toLowerCase().contains("источник для всей темы:")) {
            question = question.substring(0, question.toLowerCase().indexOf("источник для всей темы:")).trim();
        }
        if (question.toLowerCase().contains("источники:")) {
            question = question.substring(0, question.toLowerCase().indexOf("источники:")).trim();
        }
        if (question.toLowerCase().contains("источники.")) {
            question = question.substring(0, question.toLowerCase().indexOf("источники.")).trim();
        }
        if (question.toLowerCase().contains("ист.")) {
            question = question.substring(0, question.toLowerCase().indexOf("ист.")).trim();
        }
        if (question.toLowerCase().contains("ист:")) {
            question = question.substring(0, question.toLowerCase().indexOf("ист:")).trim();
        }
        String comment = "";
        if (question.toLowerCase().contains("комментарий:")) {
            int at = question.toLowerCase().indexOf("комментарий:");
            comment += question.substring(at + 12).trim();
            question = question.substring(0, at).trim();
        }
        if (question.toLowerCase().contains("комментарий.")) {
            int at = question.toLowerCase().indexOf("комментарий.");
            comment += question.substring(at + 12).trim();
            question = question.substring(0, at).trim();
        }
        if (question.toLowerCase().contains("ком:")) {
            int at = question.toLowerCase().indexOf("ком:");
            comment += question.substring(at + 12).trim();
            question = question.substring(0, at).trim();
        }
        if (question.toLowerCase().contains("ком.")) {
            int at = question.toLowerCase().indexOf("ком.");
            comment += question.substring(at + 12).trim();
            question = question.substring(0, at).trim();
        }
        while (question.toLowerCase().contains("незачет:") || question.toLowerCase().contains("незачёт:")) {
            int at = Math.max(question.toLowerCase().indexOf("незачет:"), question.toLowerCase().indexOf("незачёт:"));
            comment += " Незачёт: " + question.substring(at + 8).trim();
            question = question.substring(0, at).trim();
        }
        List<String> answers = new ArrayList<>();
        while (question.toLowerCase().contains("зачет:") || question.toLowerCase().contains("зачёт:")) {
            int at = Math.max(question.toLowerCase().lastIndexOf("зачет:"), question.toLowerCase().lastIndexOf("зачёт:"));
            answers.add(question.substring(at + 6).trim());
            question = question.substring(0, at).trim();
        }
        while (question.toLowerCase().contains("ответ:")) {
            int at = question.toLowerCase().lastIndexOf("ответ:");
            answers.add(question.substring(at + 6).trim());
            question = question.substring(0, at).trim();
        }
        if (answers.isEmpty()) {
            int level = 0;
            int lastOpen = -1;
            int lastClose = -1;
            for (int i = 0; i < question.length(); i++) {
                char c = question.charAt(i);
                if (c == '(') {
                    if (level == 0) {
                        lastOpen = i;
                    }
                    level++;
                }
                if (c == ')') {
                    level--;
                    if (level == 0) {
                        lastClose = i;
                    }
                }
            }
            if (lastOpen != -1 && lastClose != -1) {
                comment = question.substring(lastClose + 1) + comment;
                answers.add(question.substring(lastOpen + 1, lastClose - 1));
                question = question.substring(0, lastOpen);
            }
        }
        Collections.reverse(answers);
        comment = comment.trim();
        return new Question(realCost, question, answers, comment);
    }

    private static TopicSet parseStandard(List<String> data) {
        StringIterator parser = new StringIterator(data);
        parser.next();
        String description = parser.next();
        List<Topic> topics = new ArrayList<>();
        try {
            while (true) {
                String subject = parser.next();
                if (subject == null) {
                    break;
                }
                while (!isQuestion(parser.peek())) {
                    subject += "\n" + parser.next().trim();
                }
                List<Question> questions = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    String question = parser.next();
                    question = question.substring(question.indexOf(".") + 1).trim();
                    String comment = "";
                    List<String> answers = new ArrayList<>();
                    boolean hasAnswer = false;
                    while (true) {
                        if (parser.peek() == null || isQuestion(parser.peek()) || parser.peek().toLowerCase().
                                startsWith("тема")) {
                            break;
                        }
                        String next = parser.next();
                        if (next.toLowerCase().startsWith("ответ:")) {
                            while (next.toLowerCase().startsWith("ответ:")) {
                                next = next.substring(6).trim();
                            }
                            answers.add(next);
                            hasAnswer = true;
                        } else if (next.toLowerCase().startsWith("зачет:") || next.toLowerCase().startsWith("зачёт:")) {
                            while (next.toLowerCase().startsWith("зачет:") || next.toLowerCase().startsWith("зачёт:")) {
                                next = next.substring(6).trim();
                            }
                            answers.add(next);
                        } else if (next.toLowerCase().startsWith("незачет:")) {
                            comment += next + " ";
                        } else if (next.toLowerCase().startsWith("комментарий:")) {
                            comment += next.substring(12).trim() + " ";
                        } else if (!next.toLowerCase().startsWith("источник:")) {
                            question += "\n" + next;
                        }
                    }
                    int index = question.toLowerCase().indexOf("ответ:");
                    if (!hasAnswer && index != -1) {
                        answers.add(question.substring(index + 6).trim());
                        question = question.substring(0, index);
                    }
                    questions.add(new Question((i + 1) * 10, question, answers, comment.trim()));
                }
                topics.add(new Topic(subject, questions));
            }
        } catch (NullPointerException ignored) {}
        return new TopicSet(description, null, topics);
    }

    private static boolean isQuestion(String s) {
        int dCount = 0;
        for (int i = 0; i < s.length(); i++) {
            if ((s.charAt(i) == '.' || s.charAt(i) == ':') && dCount >= 1 && dCount <= 4) {
                return true;
            }
            if (Character.isDigit(s.charAt(i))) {
                dCount++;
                continue;
            }
            if (s.charAt(i) != ' ') {
                return false;
            }
        }
        return false;
    }

    private static TopicSet parseDoctor(List<String> data) {
        StringIterator parser = new StringIterator(data);
        parser.next();
        String description = parser.next();
        List<Topic> topics = new ArrayList<>();
        try {
            while (true) {
                String subject;
                do {
                    subject = parser.next();
                    if (subject == null) {
                        break;
                    }
                } while (!subject.contains(".") && ((!subject.contains(":")) || subject.startsWith("Автор:")));
                if (subject == null) {
                    break;
                }
                while (parser.peek() != null && !isQuestion(parser.peek()) && !parser.peek().toLowerCase()
                        .startsWith("ответ")) {
                    subject += "\n" + parser.next();
                }
                List<String> questions = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    String question = parser.next();
                    int dot = question.indexOf('.');
                    int semicolon = question.indexOf(':');
                    if (dot == -1 || semicolon != -1 && semicolon < dot) {
                        dot = semicolon;
                    }
                    question = question.substring(dot + 1).trim();
                    while (parser.peek() != null && !isQuestion(parser.peek()) && !parser.peek().toLowerCase()
                            .startsWith("ответ")) {
                        question += "\n" + parser.next();
                    }
                    questions.add(question);
                }
                if (parser.peek().toLowerCase().startsWith("ответ")) {
                    parser.next();
                }
                List<String> answers = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    String answer = parser.next();
                    int dot = answer.indexOf('.');
                    int semicolon = answer.indexOf(':');
                    if (dot == -1 || semicolon != -1 && semicolon < dot) {
                        dot = semicolon;
                    }
                    answer = answer.substring(dot + 1).trim();
                    answers.add(answer);
                }
                List<Question> current = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    current.add(new Question((i + 1) * 10, questions.get(i), Collections.singletonList(answers.get(i))));
                }
                topics.add(new Topic(subject, current));
            }
        } catch (NullPointerException ignored) {
        }
        return new TopicSet(description, null, topics);
    }

    private static String prettify(String s) {
//        s = StringEscapeUtils.unescapeHtml(s);
        s = s.replace("<br/>", "\n").replace("<br />", "\n");
        return s;
    }

    private static String strip(String s) {
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    static String readPage(String url) {
        URL oracle = null;
        try {
            oracle = new URL(url);
        } catch (MalformedURLException e) {
            return null;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(
                    new InputStreamReader(oracle.openStream()));
        } catch (IOException e) {
            return null;
        }

        String inputLine;
        StringBuilder builder = new StringBuilder();
        try {
            while ((inputLine = in.readLine()) != null) {
                builder.append(inputLine + " ");
            }
        } catch (IOException e) {
            return null;
        }
        try {
            in.close();
        } catch (IOException e) {
            return null;
        }
        return builder.toString().replace("&nbsp;", " ").replace("&mdash;", "-");
    }
}

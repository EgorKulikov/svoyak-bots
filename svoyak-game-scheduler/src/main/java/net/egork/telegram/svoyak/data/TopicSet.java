package net.egork.telegram.svoyak.data;

//import org.apache.commons.lang.StringEscapeUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
            out.println("Тема " + topic.topicName.trim());
            for (int i = 0; i < 5; i++) {
                Question question = topic.questions.get(i);
                out.println(question.cost + ". " + question.question.trim());
                boolean first = true;
                for (String answer : question.answers) {
                    out.println((first ? "Ответ: " : "Зачет: ") + answer.trim());
                    first = false;
                }
                if (!question.comment.isEmpty()) {
                    out.println("Комментарий: " + question.comment.trim());
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
        for (int multiplier = 1; multiplier <= 400; multiplier++) {
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
                            return tryParseAfter(data, multiplier);
                        }
                        questions.add(q);
                        last = i;
                        current--;
                    }
                }
                Collections.reverse(questions);
                String name = getTopicName(data, last);
                return new Topic(name, questions);
            }
        }
        if (data.size() >= 5) {
            cantParse(data);
        }
        return null;
    }

    @NotNull
    private static String getTopicName(List<String> data, int last) {
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
        return name;
    }

    @Nullable
    private static Topic tryParseAfter(List<String> data, int multiplier) {
        int at = data.size() - 1;
        int last = data.size();
        List<Question> answers = new ArrayList<>();
        for (int i = 5; i >= 1; i--) {
            while (true) {
                if (at < 0) {
                    cantParse(data);
                    return null;
                }
                String prefix = Integer.toString(i * multiplier);
                String current = data.get(at);
                if (current.startsWith(prefix + " ") || current.startsWith(prefix + ".") || current.startsWith(prefix +
                        ":")) {
                    answers.add(parseQuestion(data.subList(at, last), i * multiplier, i * 10));
                    last = at;
                    break;
                }
                at--;
            }
        }
        at--;
        List<Question> questions = new ArrayList<>();
        for (int i = 5; i >= 1; i--) {
            while (true) {
                if (at < 0) {
                    cantParse(data);
                    return null;
                }
                String prefix = Integer.toString(i * multiplier);
                String current = data.get(at);
                if (current.startsWith(prefix + " ") || current.startsWith(prefix + ".") || current.startsWith(prefix +
                        ":")) {
                    Question question = parseQuestion(data.subList(at, last), i * multiplier, i * 10);
                    List<String> curAnswers = new ArrayList<>();
                    Question allAnswers = answers.get(5 - i);
                    curAnswers.add(allAnswers.question);
                    curAnswers.addAll(allAnswers.answers);
                    question = new Question(question.cost, question.question, curAnswers, allAnswers.comment);
                    if (allAnswers.question.trim().isEmpty()) {
                        cantParse(data);
                        return null;
                    }
                    questions.add(question);
                    last = at;
                    break;
                }
                at--;
            }
        }
        Collections.reverse(questions);
        return new Topic(getTopicName(data, last), questions);
    }

    private static void cantParse(List<String> data) {
        for (String ss : data) {
            System.err.println(ss);
        }
        System.err.println();
    }

    private static String ridOf(String s, String sample) {
        if (s.toLowerCase().contains(sample)) {
            return s.substring(0, s.toLowerCase().indexOf(sample));
        }
        return s;
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
        question = ridOf(question, "источник:");
        question = ridOf(question, "источник.");
        question = ridOf(question, "запас.");
        question = ridOf(question, "источник для всей темы:");
        question = ridOf(question, "источники:");
        question = ridOf(question, "источники.");
        question = ridOf(question, "\nист:");
        question = ridOf(question, "\nист.");
        question = ridOf(question, " ист:");
        question = ridOf(question, " ист.");
        Question result = new Question(realCost, "", new ArrayList<>(), "");
        question = addComment(question, result, "комментарий:");
        question = addComment(question, result, "комментарий.");
        question = addComment(question, result, "\nком.");
        question = addComment(question, result, " ком.");
        question = addComment(question, result, "\nком:");
        question = addComment(question, result, " ком:");
        while (question.toLowerCase().contains("незачет:") || question.toLowerCase().contains("незачёт:")) {
            int at = Math.max(question.toLowerCase().indexOf("незачет:"), question.toLowerCase().indexOf("незачёт:"));
            result.comment += " Незачёт: " + question.substring(at + 8).trim();
            question = question.substring(0, at).trim();
        }
        while (question.toLowerCase().contains("зачет:") || question.toLowerCase().contains("зачёт:")) {
            int at = Math.max(question.toLowerCase().lastIndexOf("зачет:"), question.toLowerCase().lastIndexOf("зачёт:"));
            result.answers.add(question.substring(at + 6).trim());
            question = question.substring(0, at).trim();
        }
        while (question.toLowerCase().contains("ответ:")) {
            int at = question.toLowerCase().lastIndexOf("ответ:");
            result.answers.add(question.substring(at + 6).trim());
            question = question.substring(0, at).trim();
        }
        if (result.answers.isEmpty()) {
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
                    lastClose = i;
                }
            }
            if (lastClose < lastOpen) {
                lastClose = question.length();
            }
            if (lastOpen != -1 && lastClose != -1) {
                String comment = question.substring(Math.min(lastClose + 1, question.length()));
                if (!".".equals(comment)) {
                    result.comment = comment + result.comment;
                }
                result.answers.add(question.substring(lastOpen + 1, lastClose));
                question = question.substring(0, lastOpen);
            }
        }
        Collections.reverse(result.answers);
        result.question = question;
        result.comment = result.comment.trim();
        if (".".equals(result.comment)) {
            result.comment = "";
        }
        return result;
    }

    private static String addComment(String question, Question result, String s) {
        if (question.toLowerCase().contains(s)) {
            int at = question.toLowerCase().indexOf(s);
            result.comment += question.substring(at + s.length()).trim();
            return question.substring(0, at);
        }
        return question;
    }

    public static void main(String[] args) {
        TopicSet.parse(Arrays.asList(("Тема: Древняя Греция. (Автор Байрам Кулиев)\n" +
                "10. Гиппократ отзывался о нем так. \"Вещь удивительно соответствующая человеку, как в здоровье, так и в хворях. Его предписывают по необходимости и в определенных количествах в соответствии с индивидуальным телосложением\". (Вино)\n" +
                "20. Эту музу серьезного, молитвенного пения, было принято изображать плотно закутанной в одежду и облокотившейся на скалу. (Полигимния.)\n" +
                "30. Зевс дал Афродите ЭТО, что и дало Афродите большой плюс в борьбе за право считаться самой красивой богиней. (Золотистые волосы (она - блондинка).\n" +
                "40. Мая, Электра, Тайгета, Астеропа, Меропа, Алкиона и Келена. По отцу их звали Атлантидами, а со стороны матери - именно таким образом. (Геспериды)\n" +
                "50. Считается, что Пифагор первым ввел в обращение этот термин, который определил как \"тот, кто пытается найти, выяснить\". (Философ.)\n" +
                "Источник. http://nauka.relis.ru/cgi/nauka.pl?50+9805+50805138+HTML\n").split("\n")));
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

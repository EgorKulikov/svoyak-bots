package net.egork.telegram.svoyak.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author egor@egork.net
 */
public class SIQParser {
    public static void main(String[] args) throws IOException, ParseException {
        List<Topic> topics = new ArrayList<>();
        File directory = new File(".");
        for (File file : directory.listFiles()) {
            if (!file.getName().endsWith(".siq")) {
                continue;
            }
            System.err.println(file.getName());
            ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                String filename = entry.getName();
                if (!"content.xml".equals(filename)) {
                    continue;
                }
                StringParser parser = new StringParser(TopicSet.readStream(zis));
                parser = new StringParser(parser.advance(false, "type=\"final\""));
                parser.advance(true, "date=\"");
                String date = parser.advance(false, "\"");
                String author = "";
                while (parser.advanceIfPossible(true, "<author>") != null) {
                    if (!author.isEmpty()) {
                        author += ", ";
                    }
                    author += parser.advance(false, "</author>");
                }
                while (parser.advanceIfPossible(true, "<theme name=\"") != null) {
                    String name = parser.advance(false, "\">") + " (автор(ы) - " + author + ", дата - " +
                            date + ")";
                    List<Question> questions = new ArrayList<>();
                    for (int i = 0; i < 5; i++) {
                        parser.advance(false, "<type name=\"bagcat\">", "<scenario>");
                        String text = "";
                        if (parser.startsWith("<type name=\"bagcat\">")) {
                            parser.advance(true, "<param name=\"theme\">");
                            text = "(Кот в мешке. Тема - " + parser.advance(false, "</param>") + ") ";
                            parser.advance(false, "<scenario>");
                        }
                        parser.advance(true, "<atom>");
                        text += parser.advance(false, "</atom>");
                        parser.advance(true, "<right>");
                        List<String> answers = new ArrayList<>();
                        while (parser.startsWith("<answer>")) {
                            parser.advance(8);
                            answers.add(parser.advance(true, "</answer>"));
                        }
                        questions.add(new Question((i + 1) * 10, text, answers));
                    }
                    topics.add(new Topic(name, questions));
                }
            }
        }
        TopicSet set = new TopicSet("Placeholder", "Placeholder", topics);
        PrintWriter out = new PrintWriter("topics");
        set.saveSet(out);
        out.close();
    }
}

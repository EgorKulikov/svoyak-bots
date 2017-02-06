package net.egork.telegram.svoyak;

import org.telegram.telegrambots.api.objects.User;

import java.util.List;

import static net.egork.telegram.svoyak.data.Data.DATA;

/**
 * @author egor@egork.net
 */
public class Utils {
    public static String userList(List<User> users) {
        StringBuilder builder = new StringBuilder();
        for (User user : users) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(name(user) + " (" + DATA.getRating(user.getId()) + ")");
        }
        return builder.toString();
    }

    public static String name(User user) {
//        if (user.getUsername() != null) {
//            return "@" + user.getUsername();
//        }
        if (user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        }
        return user.getFirstName();
    }

    public static boolean isNumber(String s, int min, int max) {
        try {
            int number = Integer.parseInt(s);
            return number >= min && number <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

package net.egork.telegram.svoyak;

import net.egork.telegram.User;

import java.util.List;

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
            builder.append(name(user));
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

package net.egork.telegram.svoyak.data;

import net.egork.telegram.svoyak.scheduler.GameChat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author egor@egork.net
 */
public class User {
    private final int id;
    private final String firstName;
    private final String lastName;
    private final String userName;

    public User(org.telegram.telegrambots.api.objects.User user) {
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        userName = user.getLastName();
    }

    public User(int id, String firstName, String lastName, String userName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userName = userName;
    }

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUserName() {
        return userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        return id == user.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void saveUser(PrintWriter pw) {
        GameChat.saveData(pw, "id", id);
        GameChat.saveNullableData(pw, "first name", firstName);
        GameChat.saveNullableData(pw, "last name", lastName);
        GameChat.saveNullableData(pw, "username", userName);
    }

    public static User readUser(BufferedReader in, String label) throws IOException {
        GameChat.expectLabel(in, label);
        int id = Integer.parseInt(GameChat.readData(in, "id"));
        String firstName = GameChat.readNullableData(in, "first name");
        String lastName = GameChat.readNullableData(in, "last name");
        String userName = GameChat.readNullableData(in, "username");
        return new User(id, firstName, lastName, userName);
    }

    public static User readNullableUser(BufferedReader in, String label) throws IOException {
        GameChat.expectLabel(in, label);
        if (in.readLine().equals("0")) {
            return null;
        }
        int id = Integer.parseInt(GameChat.readData(in, "id"));
        String firstName = GameChat.readNullableData(in, "first name");
        String lastName = GameChat.readNullableData(in, "last name");
        String userName = GameChat.readNullableData(in, "username");
        return new User(id, firstName, lastName, userName);
    }
}

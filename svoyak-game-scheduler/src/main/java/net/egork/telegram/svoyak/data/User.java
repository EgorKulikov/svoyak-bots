package net.egork.telegram.svoyak.data;

/**
 * @author egor@egork.net
 */
public class User {
    private final int id;
    private final String firstName;
    private final String lastName;
    private final String userName;
    private final org.telegram.telegrambots.api.objects.User user;


    public User(org.telegram.telegrambots.api.objects.User user) {
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        userName = user.getLastName();
        this.user = user;
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

    public org.telegram.telegrambots.api.objects.User getUser() {
        return user;
    }
}

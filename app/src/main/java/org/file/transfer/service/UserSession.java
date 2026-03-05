package org.file.transfer.service;

public class UserSession {
    private static UserSession instance;

    private String username;
    private String role;
    private boolean isLoggedIn;

    private UserSession() {
    }

    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void login(String username, String role) {
        this.username = username;
        this.role = role;
        this.isLoggedIn = true;
    }

    public void logout() {
        this.username = null;
        this.role = null;
        this.isLoggedIn = false;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}

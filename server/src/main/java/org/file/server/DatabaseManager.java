package org.file.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/file_transfer";
    private static final String USER = "root"; // Update with user's MySQL username
    private static final String PASSWORD = ""; // Update with user's MySQL password

    // Ensure driver is loaded
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found!");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Login Method
    public static boolean validateUser(String username, String password) {
        String query = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    // In real app use BCrypt. Here we use plain text matching.
                    return storedPassword.equals(password);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getUserRole(String username) {
        String query = "SELECT role FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "USER";
    }

    // Register Method
    public static boolean registerUser(String username, String password, String role) {
        String query = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, role != null ? role.toUpperCase() : "USER");

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            // Probably Duplicate Entry
            System.err.println("Register err: " + e.getMessage());
            return false;
        }
    }

    public static void logTransferStatus(String sender, String receiver, String filename, long size, String status) {
        // Find sender ID and receiver ID
        int senderId = getUserId(sender);
        int receiverId = getUserId(receiver);

        if (senderId == -1 || receiverId == -1)
            return;

        String query = "INSERT INTO transfer_history (sender_id, receiver_id, file_name, file_size, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setString(3, filename);
            stmt.setLong(4, size);
            stmt.setString(5, status);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static int getUserId(String username) {
        String query = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static class TransferRecord {
        public int id;
        public String sender;
        public String receiver;
        public String fileName;
        public long size;
        public String status;
        public String timestamp;
    }

    public static List<TransferRecord> getTransferHistory(String username) {
        List<TransferRecord> list = new ArrayList<>();
        String query = "SELECT t.id, s.username as sender, r.username as receiver, t.file_name, t.file_size, t.status, t.transfer_date "
                +
                "FROM transfer_history t " +
                "JOIN users s ON t.sender_id = s.id " +
                "JOIN users r ON t.receiver_id = r.id " +
                "WHERE s.username = ? OR r.username = ? " +
                "ORDER BY t.transfer_date DESC";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TransferRecord rec = new TransferRecord();
                    rec.id = rs.getInt("id");
                    rec.sender = rs.getString("sender");
                    rec.receiver = rs.getString("receiver");
                    rec.fileName = rs.getString("file_name");
                    rec.size = rs.getLong("file_size");
                    rec.status = rs.getString("status");
                    rec.timestamp = rs.getTimestamp("transfer_date").toString();
                    list.add(rec);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean deleteHistory(int id, String username) {
        String query = "DELETE t FROM transfer_history t " +
                "JOIN users s ON t.sender_id = s.id " +
                "JOIN users r ON t.receiver_id = r.id " +
                "WHERE t.id = ? AND (s.username = ? OR r.username = ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.setString(2, username);
            stmt.setString(3, username);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean deleteAllHistory(String username) {
        String query = "DELETE t FROM transfer_history t " +
                "JOIN users s ON t.sender_id = s.id " +
                "JOIN users r ON t.receiver_id = r.id " +
                "WHERE s.username = ? OR r.username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}

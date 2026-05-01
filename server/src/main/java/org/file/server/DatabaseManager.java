package org.file.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    public static int getUserIdByUsername(String username) {
        String query = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
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
            System.err.println("Register error: " + e.getMessage());
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

    public static class MarketFileRecord {
        public int id;
        public String fileName;
        public long fileSize;
        public String filePath;
        public int sellerId;
        public String sellerName;
        public long price;
        public String uploadDate;
    }

    public static List<MarketFileRecord> getAllMarketFiles() {
        List<MarketFileRecord> list = new ArrayList<>();
        // Try with upload_date first, if it fails, try without it or with created_at
        String query = "SELECT m.id, m.file_name, m.file_size, m.file_path, m.seller_id, u.username as seller_name, m.price, "
                + "(CASE WHEN 1=1 THEN CURRENT_TIMESTAMP END) as upload_date " 
                + "FROM market_files m " +
                "JOIN users u ON m.seller_id = u.id " +
                "ORDER BY m.id DESC";
                
        // A safer query if we don't know the exact name of the date column
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement("SELECT m.*, u.username as seller_name FROM market_files m JOIN users u ON m.seller_id = u.id ORDER BY m.id DESC");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                MarketFileRecord rec = new MarketFileRecord();
                rec.id = rs.getInt("id");
                rec.fileName = rs.getString("file_name");
                if (rec.fileName == null) rec.fileName = "Unknown";
                rec.fileSize = rs.getLong("file_size");
                rec.filePath = rs.getString("file_path");
                if (rec.filePath == null) rec.filePath = "";
                rec.sellerId = rs.getInt("seller_id");
                rec.sellerName = rs.getString("seller_name");
                if (rec.sellerName == null) rec.sellerName = "Unknown";
                rec.price = rs.getLong("price");
                
                // Try to get upload_date or created_at, gracefully fallback if neither exists
                try {
                    java.sql.Timestamp ts = rs.getTimestamp("upload_date");
                    rec.uploadDate = ts != null ? ts.toString() : "N/A";
                } catch (SQLException ignore) {
                    try {
                        java.sql.Timestamp ts = rs.getTimestamp("created_at");
                        rec.uploadDate = ts != null ? ts.toString() : "N/A";
                    } catch (SQLException ignore2) {
                        rec.uploadDate = "N/A";
                    }
                }
                list.add(rec);
            }
        } catch (SQLException e) {
            System.err.println("Error in getAllMarketFiles: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public static boolean hasUserBoughtFile(int userId, int fileId) {
        String query = "SELECT 1 FROM transaction WHERE buyer_id = ? AND file_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean buyFile(int buyerId, int fileId) {
        String query = "INSERT INTO transaction (buyer_id, file_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, buyerId);
            stmt.setInt(2, fileId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getFilePath(int fileId) {
        String query = "SELECT file_path FROM market_files WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("file_path");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean deleteMarketFile(int fileId, int sellerId) {
        String deleteTransactions = "DELETE FROM transaction WHERE file_id = ?";
        String deleteFile = "DELETE FROM market_files WHERE id = ? AND seller_id = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            // Try to delete from transaction table first, ignoring errors if table doesn't exist
            try (PreparedStatement stmt1 = conn.prepareStatement(deleteTransactions)) {
                stmt1.setInt(1, fileId);
                stmt1.executeUpdate();
            } catch (SQLException ignore) {
                System.err.println("Warning: Could not delete from transaction table: " + ignore.getMessage());
            }

            try (PreparedStatement stmt2 = conn.prepareStatement(deleteFile)) {
                stmt2.setInt(1, fileId);
                stmt2.setInt(2, sellerId);
                int rows = stmt2.executeUpdate();

                conn.commit();
                System.out.println("[DB] deleteMarketFile: deleted " + rows + " rows for fileId " + fileId);
                return rows > 0;
            } catch (SQLException ex) {
                conn.rollback();
                System.err.println("Error in deleteMarketFile during execution: " + ex.getMessage());
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error in deleteMarketFile connection: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean insertMarketFile(String fileName, long fileSize, String filePath, int sellerId, long price) {
        String query = "INSERT INTO market_files (file_name, file_size, file_path, seller_id, price) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, fileName);
            pstmt.setLong(2, fileSize);
            pstmt.setString(3, filePath);
            pstmt.setInt(4, sellerId);
            pstmt.setLong(5, price);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error in insertMarketFile: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}

package org.file.server;

import java.sql.*;

public class CheckDB {
    public static void main(String[] args) {
        try (Connection conn = DatabaseManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            System.out.println("=== table market_files ===");
            try (ResultSet rs = metaData.getColumns(null, null, "market_files", null)) {
                while (rs.next()) {
                    System.out.println(rs.getString("COLUMN_NAME") + " - " + rs.getString("TYPE_NAME"));
                }
            }
            
            System.out.println("=== table transaction ===");
            try (ResultSet rs = metaData.getColumns(null, null, "transaction", null)) {
                while (rs.next()) {
                    System.out.println(rs.getString("COLUMN_NAME") + " - " + rs.getString("TYPE_NAME"));
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

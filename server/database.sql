CREATE DATABASE IF NOT EXISTS file_transfer;
USE file_transfer;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transfer_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT,
    receiver_id INT,
    file_name VARCHAR(255),
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    transfer_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (receiver_id) REFERENCES users(id)
);

-- Insert a default admin user (password: admin123)
-- In a real app, passwords should be hashed (e.g., BCrypt). This is for demonstration.
INSERT IGNORE INTO users (username, password, role) VALUES ('admin', 'admin123', 'ADMIN');

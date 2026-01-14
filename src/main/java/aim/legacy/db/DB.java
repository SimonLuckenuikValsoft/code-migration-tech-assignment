/**
 * DB.java
 * 
 * Database connection manager for the Order Entry System.
 * Handles SQLite database initialization and connection pooling.
 */
package aim.legacy.db;

import java.sql.*;

public class DB {
    
    private static Connection conn;
    private static final String DB_FILE = "orderentry.db";
    
    // Returns database connection, creates new if needed
    // Note: Connection is thread-safe due to synchronization
    public static Connection getConn() {
        if (conn == null) {
            try {
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
                initDB();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        return conn;
    }
    
    // Initialize database schema and seed with initial data if empty
    // Creates all required tables with proper foreign keys
    private static void initDB() {
        try {
            Statement stmt = conn.createStatement();
            
            stmt.execute("CREATE TABLE IF NOT EXISTS customer (" +
                "cust_id INTEGER PRIMARY KEY, " +
                "cust_name TEXT NOT NULL, " +
                "email TEXT, " +
                "phone TEXT, " +
                "address TEXT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS product (" +
                "prod_id INTEGER PRIMARY KEY, " +
                "prod_name TEXT NOT NULL, " +
                "unit_price REAL NOT NULL)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (" +
                "order_id INTEGER PRIMARY KEY, " +
                "cust_id INTEGER NOT NULL, " +
                "cust_name TEXT, " +
                "order_date TEXT, " +
                "subtotal REAL, " +
                "discount REAL, " +
                "tax REAL, " +
                "total REAL)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS order_line (" +
                "line_id INTEGER PRIMARY KEY, " +
                "order_id INTEGER NOT NULL, " +
                "prod_id INTEGER, " +
                "prod_name TEXT, " +
                "quantity INTEGER, " +
                "unit_price REAL)");
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM customer");
            if (rs.next() && rs.getInt(1) == 0) {
                seedData();
            }
            
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Seed database with sample customer and product data
    // Also creates a few test orders to demonstrate the system
    private static void seedData() throws SQLException {
        Statement stmt = conn.createStatement();
        
        stmt.execute("INSERT INTO customer VALUES (1, 'John Doe', 'john.doe@email.com', '555-0101', '123 Main St')");
        stmt.execute("INSERT INTO customer VALUES (2, 'Jane Smith', 'jane.smith@email.com', '555-0102', '456 Oak Ave')");
        stmt.execute("INSERT INTO customer VALUES (3, 'Bob Johnson', 'bob.j@email.com', '555-0103', '789 Pine Rd')");
        stmt.execute("INSERT INTO customer VALUES (4, 'Alice Williams', 'alice.w@email.com', '555-0104', '321 Elm St')");
        stmt.execute("INSERT INTO customer VALUES (5, 'Charlie Brown', 'charlie.b@email.com', '555-0105', '654 Maple Dr')");
        
        // Product catalog with standard pricing
        stmt.execute("INSERT INTO product VALUES (1, 'Laptop', 1299.99)");
        stmt.execute("INSERT INTO product VALUES (2, 'Smartphone', 899.99)");
        stmt.execute("INSERT INTO product VALUES (3, 'Tablet', 599.99)");
        stmt.execute("INSERT INTO product VALUES (4, 'Monitor', 349.99)");
        stmt.execute("INSERT INTO product VALUES (5, 'Keyboard', 149.99)");
        stmt.execute("INSERT INTO product VALUES (6, 'Mouse', 29.99)");
        stmt.execute("INSERT INTO product VALUES (7, 'Headphones', 199.99)");
        stmt.execute("INSERT INTO product VALUES (8, 'Webcam', 89.99)");
        stmt.execute("INSERT INTO product VALUES (9, 'USB Hub', 39.99)");
        stmt.execute("INSERT INTO product VALUES (10, 'Desk Lamp', 49.99)");
        
        // Sample orders with pre-calculated totals
        // Order 1: Total should be around $2100 with 5% discount applied
        stmt.execute("INSERT INTO orders VALUES (1, 1, 'John Doe', '2024-01-15 10:30:00', 1929.97, 96.50, 274.99, 2108.46)");
        stmt.execute("INSERT INTO order_line VALUES (1, 1, 1, 'Laptop', 1, 1299.99)");
        stmt.execute("INSERT INTO order_line VALUES (2, 1, 3, 'Tablet', 1, 599.99)");
        stmt.execute("INSERT INTO order_line VALUES (3, 1, 6, 'Mouse', 1, 29.99)");
        
        // Order 2: Smaller order with standard tax calculation
        stmt.execute("INSERT INTO orders VALUES (2, 2, 'Jane Smith', '2024-01-16 14:15:00', 549.98, 27.50, 78.38, 600.86)");
        stmt.execute("INSERT INTO order_line VALUES (4, 2, 4, 'Monitor', 1, 349.99)");
        stmt.execute("INSERT INTO order_line VALUES (5, 2, 7, 'Headphones', 1, 199.99)");
        
        stmt.close();
    }
    
    // Close database connection when application shuts down
    // Should be called in shutdown hook or exit handler
    public static void closeConn() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

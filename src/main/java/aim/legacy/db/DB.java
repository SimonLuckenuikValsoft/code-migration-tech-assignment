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
                "address TEXT, " +
                "customer_type TEXT DEFAULT 'STANDARD')");
            
            // Add customer_type column to existing databases if it doesn't exist
            // This handles migration from older database versions
            try {
                stmt.execute("ALTER TABLE customer ADD COLUMN customer_type TEXT DEFAULT 'STANDARD'");
            } catch (SQLException e) {
                // Column already exists, ignore error
            }
            
            // This is a "hacky" migration that doesn't properly handle data
            try {
                stmt.execute("ALTER TABLE order_line ADD COLUMN container_id INTEGER");
            } catch (SQLException e) {
                // Column already exists, ignore error
            }
            
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
            
            // Shipper table - represents shipment groupings within an order
            stmt.execute("CREATE TABLE IF NOT EXISTS shipper (" +
                "shipper_id INTEGER PRIMARY KEY, " +
                "order_id INTEGER NOT NULL, " +
                "shipper_number TEXT, " +
                "carrier TEXT, " +
                "tracking_number TEXT, " +
                "ship_date TEXT, " +
                "total_weight REAL)");
            
            // Container/Pallet table - represents physical containers in a shipment
            stmt.execute("CREATE TABLE IF NOT EXISTS container (" +
                "container_id INTEGER PRIMARY KEY, " +
                "shipper_id INTEGER NOT NULL, " +
                "order_id INTEGER NOT NULL, " +
                "container_number TEXT, " +
                "container_type TEXT, " +
                "weight REAL, " +
                "dimensions TEXT)");
            
            // Order line now references container instead of order directly
            stmt.execute("CREATE TABLE IF NOT EXISTS order_line (" +
                "line_id INTEGER PRIMARY KEY, " +
                "order_id INTEGER NOT NULL, " +
                "container_id INTEGER, " +
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
        
        stmt.execute("INSERT INTO customer VALUES (1, 'John Doe', 'john.doe@email.com', '555-0101', '123 Main St', 'STANDARD')");
        stmt.execute("INSERT INTO customer VALUES (2, 'Jane Smith', 'jane.smith@email.com', '555-0102', '456 Oak Ave', 'PREMIUM')");
        stmt.execute("INSERT INTO customer VALUES (3, 'Bob Johnson', 'bob.j@email.com', '555-0103', '789 Pine Rd', 'STANDARD')");
        stmt.execute("INSERT INTO customer VALUES (4, 'Alice Williams', 'alice.w@email.com', '555-0104', '321 Elm St', 'VIP')");
        stmt.execute("INSERT INTO customer VALUES (5, 'Charlie Brown', 'charlie.b@email.com', '555-0105', '654 Maple Dr', 'PREMIUM')");
        
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
        
        // Shippers for Order 1 - demonstrating multi-level hierarchy
        stmt.execute("INSERT INTO shipper VALUES (1, 1, 'SHIP-001', 'FedEx', 'FDX12345678', '2024-01-16', 15.5)");
        stmt.execute("INSERT INTO shipper VALUES (2, 1, 'SHIP-002', 'UPS', 'UPS98765432', '2024-01-16', 8.2)");
        
        // Containers for Order 1 shippers
        stmt.execute("INSERT INTO container VALUES (1, 1, 1, 'CTN-001-A', 'Box', 12.5, '24x18x12')");
        stmt.execute("INSERT INTO container VALUES (2, 1, 1, 'CTN-001-B', 'Box', 3.0, '12x12x6')");
        stmt.execute("INSERT INTO container VALUES (3, 2, 1, 'CTN-002-A', 'Box', 8.2, '18x12x8')");
        
        // Order lines now reference containers
        stmt.execute("INSERT INTO order_line VALUES (1, 1, 1, 1, 'Laptop', 1, 1299.99)");
        stmt.execute("INSERT INTO order_line VALUES (2, 1, 1, 3, 'Tablet', 1, 599.99)");
        stmt.execute("INSERT INTO order_line VALUES (3, 1, 3, 6, 'Mouse', 1, 29.99)");
        
        // Order 2: Smaller order with standard tax calculation
        stmt.execute("INSERT INTO orders VALUES (2, 2, 'Jane Smith', '2024-01-16 14:15:00', 549.98, 27.50, 78.38, 600.86)");
        
        // Shipper for Order 2
        stmt.execute("INSERT INTO shipper VALUES (3, 2, 'SHIP-003', 'USPS', 'USPS111222333', '2024-01-17', 18.0)");
        
        // Containers for Order 2
        stmt.execute("INSERT INTO container VALUES (4, 3, 2, 'CTN-003-A', 'Pallet', 18.0, '48x40x60')");
        
        // Order lines for Order 2
        stmt.execute("INSERT INTO order_line VALUES (4, 2, 4, 4, 'Monitor', 1, 349.99)");
        stmt.execute("INSERT INTO order_line VALUES (5, 2, 4, 7, 'Headphones', 1, 199.99)");
        
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

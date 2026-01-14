/**
 * MainApp.java
 * 
 * Main application window and entry point.
 * Provides navigation between customer and order management screens.
 * Uses CardLayout for switching between different views.
 */
package aim.legacy.ui;

import aim.legacy.db.DB;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class MainApp extends JFrame {
    
    private CardLayout cardLayout;
    private JPanel mainPanel;
    
    private CustomersScreen customersScreen;
    private OrdersScreen ordersScreen;
    
    // Initialize database connection on startup
    // This ensures the database is ready before any screens load
    static {
        DB.getConn();
    }
    
    public MainApp() {
        super("Order Entry System");
        
        setupUI();
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }
    
    // Set up the main window with menu bar and content panels
    // Creates all screens and adds them to the card layout manager
    private void setupUI() {
        setJMenuBar(createMenuBar());
        
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        
        customersScreen = new CustomersScreen(this);
        ordersScreen = new OrdersScreen(this);
        
        mainPanel.add(customersScreen, "customers");
        mainPanel.add(ordersScreen, "orders");
        
        add(mainPanel);
        
        showCustomersScreen();
    }
    
    // Creates the application menu bar with navigation options
    // Includes shortcuts for quick navigation between screens
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        JMenu menu = new JMenu("Navigation");
        
        JMenuItem customersItem = new JMenuItem("Customers");
        customersItem.addActionListener(e -> showCustomersScreen());
        menu.add(customersItem);
        
        JMenuItem ordersItem = new JMenuItem("Orders");
        ordersItem.addActionListener(e -> showOrdersScreen());
        menu.add(ordersItem);
        
        menu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            DB.closeConn();
            System.exit(0);
        });
        menu.add(exitItem);
        
        menuBar.add(menu);
        
        return menuBar;
    }
    
    // Switch to customers screen and refresh the data
    // Uses card layout to swap views without creating new instances
    public void showCustomersScreen() {
        customersScreen.refresh();
        cardLayout.show(mainPanel, "customers");
    }
    
    // Switch to orders screen and refresh the data
    // Orders screen shows all customer orders with totals
    public void showOrdersScreen() {
        ordersScreen.refresh();
        cardLayout.show(mainPanel, "orders");
    }
    
    // Application entry point
    // Sets look and feel to match OS and launches the main window
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        
        SwingUtilities.invokeLater(() -> {
            MainApp app = new MainApp();
            app.setVisible(true);
        });
    }
}

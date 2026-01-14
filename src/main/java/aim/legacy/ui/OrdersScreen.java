/**
 * OrdersScreen.java
 * 
 * Screen for viewing and managing customer orders.
 * Shows all orders with calculated totals and allows creating/editing orders.
 * Performs all database operations inline for performance.
 */
package aim.legacy.ui;

import aim.legacy.db.DB;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class OrdersScreen extends JPanel {

    private final MainApp mainApp;
    
    private JTable orderTable;
    private DefaultTableModel tableModel;
    
    public OrdersScreen(MainApp mainApp) {
        this.mainApp = mainApp;
        setupUI();
        loadOrders();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Orders"));
        add(topPanel, BorderLayout.NORTH);
        
        String[] columns = {"ID", "Customer", "Date", "Subtotal", "Discount", "Tax", "Total"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(orderTable);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newButton = new JButton("New Order");
        newButton.addActionListener(e -> createOrder());
        buttonPanel.add(newButton);
        
        JButton editButton = new JButton("Edit Order");
        editButton.addActionListener(e -> editOrder());
        buttonPanel.add(editButton);
        
        JButton deleteButton = new JButton("Delete Order");
        deleteButton.addActionListener(e -> deleteOrder());
        buttonPanel.add(deleteButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    // Refresh order list when returning to this screen
    // Ensures latest data is always displayed
    public void refresh() {
        loadOrders();
    }
    
    // Load all orders from database with pre-calculated totals
    // Formats currency values for display in table
    private void loadOrders() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT order_id, cust_name, order_date, subtotal, discount, tax, total FROM orders ORDER BY order_id";
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getLong("order_id"),
                    rs.getString("cust_name"),
                    rs.getString("order_date"),
                    "$" + String.format("%.2f", rs.getDouble("subtotal")),
                    "$" + String.format("%.2f", rs.getDouble("discount")),
                    "$" + String.format("%.2f", rs.getDouble("tax")),
                    "$" + String.format("%.2f", rs.getDouble("total"))
                });
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading orders: " + e.getMessage());
        }
    }
    
    // Open new order dialog
    // Dialog handles all order creation logic including line items
    private void createOrder() {
        OrderEditorDialog dialog = new OrderEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), 0);
        dialog.setVisible(true);
        
        if (dialog.isSaved()) {
            loadOrders();
        }
    }
    
    private void editOrder() {
        int selectedRow = orderTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an order to edit");
            return;
        }
        
        long id = (Long) tableModel.getValueAt(selectedRow, 0);
        
        OrderEditorDialog dialog = new OrderEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), id);
        dialog.setVisible(true);
        
        if (dialog.isSaved()) {
            loadOrders();
        }
    }
    
    private void deleteOrder() {
        int selectedRow = orderTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an order to delete");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete this order?",
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            long id = (Long) tableModel.getValueAt(selectedRow, 0);
            
            try {
                Connection conn = DB.getConn();
                Statement stmt = conn.createStatement();
                String sql = "DELETE FROM order_line WHERE order_id = " + id;
                stmt.execute(sql);
                sql = "DELETE FROM orders WHERE order_id = " + id;
                stmt.execute(sql);
                
                stmt.close();
                loadOrders();
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error deleting order: " + e.getMessage());
            }
        }
    }
}

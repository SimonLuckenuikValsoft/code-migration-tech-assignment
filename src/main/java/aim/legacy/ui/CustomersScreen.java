/**
 * CustomersScreen.java
 * 
 * Screen for managing customer records.
 * Provides functionality to view, add, edit, delete, and search customers.
 * All database operations are performed directly in this class for simplicity.
 */
package aim.legacy.ui;

import aim.legacy.db.DB;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class CustomersScreen extends JPanel {

    private final MainApp mainApp;
    
    private JTable customerTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    
    public CustomersScreen(MainApp mainApp) {
        this.mainApp = mainApp;
        setupUI();
        loadCustomers();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        topPanel.add(searchField);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchCustomers());
        topPanel.add(searchButton);
        JButton clearButton = new JButton("Show All");
        clearButton.addActionListener(e -> loadCustomers());
        topPanel.add(clearButton);
        
        add(topPanel, BorderLayout.NORTH);
        
        String[] columns = {"ID", "Name", "Email", "Phone", "Address"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        customerTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(customerTable);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Customer");
        addButton.addActionListener(e -> addCustomer());
        buttonPanel.add(addButton);
        
        JButton editButton = new JButton("Edit Customer");
        editButton.addActionListener(e -> editCustomer());
        buttonPanel.add(editButton);
        
        JButton deleteButton = new JButton("Delete Customer");
        deleteButton.addActionListener(e -> deleteCustomer());
        buttonPanel.add(deleteButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    // Refresh the customer list from database
    // Called when switching back to this screen
    public void refresh() {
        loadCustomers();
    }
    
    // Load all customers from database into the table
    // Executes direct SQL query and populates table model
    private void loadCustomers() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT cust_id, cust_name, email, phone, address FROM customer ORDER BY cust_id";
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getLong("cust_id"),
                    rs.getString("cust_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("address")
                });
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading customers: " + e.getMessage());
        }
    }
    
    // Search customers by name using LIKE query
    // Note: Uses string concatenation for SQL (should use prepared statements)
    private void searchCustomers() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            loadCustomers();
            return;
        }
        
        tableModel.setRowCount(0);
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT cust_id, cust_name, email, phone, address FROM customer " +
                        "WHERE LOWER(cust_name) LIKE '%" + query.toLowerCase() + "%' ORDER BY cust_id";
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getLong("cust_id"),
                    rs.getString("cust_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("address")
                });
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Open dialog to add new customer
    // Generates next ID by finding MAX(cust_id) + 1
    private void addCustomer() {
        CustomerDialog dialog = new CustomerDialog((Frame) SwingUtilities.getWindowAncestor(this), 0, "", "", "", "");
        dialog.setVisible(true);
        
        if (dialog.isSaved()) {
            try {
                Connection conn = DB.getConn();
                Statement stmt = conn.createStatement();
                String sql = "SELECT MAX(cust_id) FROM customer";
                ResultSet rs = stmt.executeQuery(sql);
                long nextId = 1;
                if (rs.next()) {
                    nextId = rs.getLong(1) + 1;
                }
                
                String insertSql = "INSERT INTO customer (cust_id, cust_name, email, phone, address) VALUES (" +
                    nextId + ", '" + dialog.getName().replace("'", "''") + "', '" +
                    dialog.getEmail().replace("'", "''") + "', '" +
                    dialog.getPhone().replace("'", "''") + "', '" +
                    dialog.getAddress().replace("'", "''") + "')";
                stmt.execute(insertSql);
                
                rs.close();
                stmt.close();
                loadCustomers();
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error adding customer: " + e.getMessage());
            }
        }
    }
    
    // Edit existing customer record
    // Opens dialog with current values pre-populated
    private void editCustomer() {
        int selectedRow = customerTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a customer to edit");
            return;
        }
        
        long id = (Long) tableModel.getValueAt(selectedRow, 0);
        String name = (String) tableModel.getValueAt(selectedRow, 1);
        String email = (String) tableModel.getValueAt(selectedRow, 2);
        String phone = (String) tableModel.getValueAt(selectedRow, 3);
        String address = (String) tableModel.getValueAt(selectedRow, 4);
        
        CustomerDialog dialog = new CustomerDialog((Frame) SwingUtilities.getWindowAncestor(this), id, name, email, phone, address);
        dialog.setVisible(true);
        
        if (dialog.isSaved()) {
            try {
                Connection conn = DB.getConn();
                Statement stmt = conn.createStatement();
                String sql = "UPDATE customer SET cust_name = '" + dialog.getName().replace("'", "''") + "', " +
                    "email = '" + dialog.getEmail().replace("'", "''") + "', " +
                    "phone = '" + dialog.getPhone().replace("'", "''") + "', " +
                    "address = '" + dialog.getAddress().replace("'", "''") + "' " +
                    "WHERE cust_id = " + id;
                stmt.execute(sql);
                
                stmt.close();
                loadCustomers();
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error updating customer: " + e.getMessage());
            }
        }
    }
    
    // Delete customer from database
    // Uses CASCADE delete to remove associated orders
    private void deleteCustomer() {
        int selectedRow = customerTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a customer to delete");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete this customer?",
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            long id = (Long) tableModel.getValueAt(selectedRow, 0);
            
            try {
                Connection conn = DB.getConn();
                Statement stmt = conn.createStatement();
                String sql = "DELETE FROM customer WHERE cust_id = " + id;
                stmt.execute(sql);
                
                stmt.close();
                loadCustomers();
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error deleting customer: " + e.getMessage());
            }
        }
    }
}

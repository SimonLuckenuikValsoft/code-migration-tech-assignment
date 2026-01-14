/**
 * OrderEditorDialog.java
 * 
 * Complex dialog for creating and editing orders.
 * Handles customer selection, line items, and automatic pricing calculations.
 * Implements temp-table pattern for managing line items before save.
 * All business logic and database operations are contained within this dialog.
 */
package aim.legacy.ui;

import aim.legacy.db.DB;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

public class OrderEditorDialog extends JDialog {

    private long orderId;
    private boolean saved = false;
    
    private JComboBox<String> customerCombo;
    private Map<String, Long> customerMap = new HashMap<>();
    private JTable linesTable;
    private DefaultTableModel linesTableModel;
    
    private JLabel subtotalLabel;
    private JLabel discountLabel;
    private JLabel taxLabel;
    private JLabel totalLabel;
    private JTextArea statusArea;
    
    // Tax rate is fixed at 14.975% for all orders
    private static final BigDecimal TAX_RATE = new BigDecimal("0.14975");
    
    // Temp-table pattern: holds line items in memory before committing to database
    // This is similar to Progress ABL temp-tables for transaction buffering
    private class TempLine {
        long lineId;
        long prodId;
        String prodName;
        int qty;
        BigDecimal price;
        
        TempLine(long lid, long pid, String pname, int q, BigDecimal p) {
            lineId = lid;
            prodId = pid;
            prodName = pname;
            qty = q;
            price = p;
        }
    }
    
    private ArrayList<TempLine> tempLines = new ArrayList<>();
    
    public OrderEditorDialog(Frame parent, long id) {
        super(parent, id == 0 ? "New Order" : "Edit Order", true);
        this.orderId = id;
        
        setupUI();
        loadCustomers();
        if (id > 0) {
            loadOrder();
        }
        calculateTotals();
        
        setSize(800, 600);
        setLocationRelativeTo(parent);
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Customer:"));
        customerCombo = new JComboBox<>();
        customerCombo.setPreferredSize(new Dimension(300, 25));
        topPanel.add(customerCombo);
        add(topPanel, BorderLayout.NORTH);
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        
        String[] columns = {"Product", "Quantity", "Unit Price", "Line Total"};
        linesTableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        linesTable = new JTable(linesTableModel);
        JScrollPane scrollPane = new JScrollPane(linesTable);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel lineButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addLineButton = new JButton("Add Line");
        addLineButton.addActionListener(e -> addLine());
        lineButtonPanel.add(addLineButton);
        
        JButton removeLineButton = new JButton("Remove Line");
        removeLineButton.addActionListener(e -> removeLine());
        lineButtonPanel.add(removeLineButton);
        
        centerPanel.add(lineButtonPanel, BorderLayout.SOUTH);
        
        add(centerPanel, BorderLayout.CENTER);
        
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        JPanel totalsPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        totalsPanel.setBorder(BorderFactory.createTitledBorder("Totals"));
        totalsPanel.add(new JLabel("Subtotal:"));
        subtotalLabel = new JLabel("$0.00");
        totalsPanel.add(subtotalLabel);
        totalsPanel.add(new JLabel("Discount:"));
        discountLabel = new JLabel("$0.00");
        totalsPanel.add(discountLabel);
        totalsPanel.add(new JLabel("Tax:"));
        taxLabel = new JLabel("$0.00");
        totalsPanel.add(taxLabel);
        totalsPanel.add(new JLabel("Total:"));
        totalLabel = new JLabel("$0.00");
        Font boldFont = totalLabel.getFont().deriveFont(Font.BOLD, 14f);
        totalLabel.setFont(boldFont);
        totalsPanel.add(totalLabel);
        
        rightPanel.add(totalsPanel, BorderLayout.NORTH);
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
        statusArea = new JTextArea(5, 20);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusPanel.add(statusScroll, BorderLayout.CENTER);
        
        rightPanel.add(statusPanel, BorderLayout.CENTER);
        
        add(rightPanel, BorderLayout.EAST);
        
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> save());
        bottomPanel.add(saveButton);
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        bottomPanel.add(cancelButton);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void loadCustomers() {
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT cust_id, cust_name FROM customer ORDER BY cust_name";
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                long id = rs.getLong("cust_id");
                String name = rs.getString("cust_name");
                customerCombo.addItem(name);
                customerMap.put(name, id);
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void loadOrder() {
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT cust_name FROM orders WHERE order_id = " + orderId;
            ResultSet rs = stmt.executeQuery(sql);
            
            if (rs.next()) {
                String custName = rs.getString("cust_name");
                customerCombo.setSelectedItem(custName);
            }
            
            rs.close();
            
            sql = "SELECT line_id, prod_id, prod_name, quantity, unit_price FROM order_line WHERE order_id = " + orderId;
            rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                TempLine line = new TempLine(
                    rs.getLong("line_id"),
                    rs.getLong("prod_id"),
                    rs.getString("prod_name"),
                    rs.getInt("quantity"),
                    new BigDecimal(rs.getString("unit_price"))
                );
                tempLines.add(line);
            }
            
            rs.close();
            stmt.close();
            
            refreshLines();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void refreshLines() {
        linesTableModel.setRowCount(0);
        for (TempLine line : tempLines) {
            BigDecimal lineTotal = line.price.multiply(BigDecimal.valueOf(line.qty));
            linesTableModel.addRow(new Object[]{
                line.prodName,
                line.qty,
                "$" + line.price,
                "$" + lineTotal.setScale(2, RoundingMode.HALF_UP)
            });
        }
    }
    
    // Calculate order totals including discounts and tax
    // Discount tiers: 5% over $600, 10% over $1200, 15% over $2500
    // Tax is applied to subtotal after discount
    private void calculateTotals() {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (TempLine line : tempLines) {
            BigDecimal lineTotal = line.price.multiply(BigDecimal.valueOf(line.qty));
            subtotal = subtotal.add(lineTotal);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal discount = BigDecimal.ZERO;
        if (subtotal.compareTo(new BigDecimal("2000")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.15"));
        } else if (subtotal.compareTo(new BigDecimal("1000")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.10"));
        } else if (subtotal.compareTo(new BigDecimal("500")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.05"));
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal taxableAmount = subtotal.subtract(discount);
        BigDecimal tax = taxableAmount.multiply(TAX_RATE);
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal total = subtotal.subtract(discount).add(tax);
        total = total.setScale(2, RoundingMode.HALF_UP);
        
        subtotalLabel.setText("$" + subtotal);
        discountLabel.setText("$" + discount);
        taxLabel.setText("$" + tax);
        totalLabel.setText("$" + total);
    }
    
    private void addLine() {
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT prod_id, prod_name, unit_price FROM product ORDER BY prod_name";
            ResultSet rs = stmt.executeQuery(sql);
            
            ArrayList<String> productList = new ArrayList<>();
            Map<String, Long> prodIdMap = new HashMap<>();
            Map<String, BigDecimal> priceMap = new HashMap<>();
            
            while (rs.next()) {
                long id = rs.getLong("prod_id");
                String name = rs.getString("prod_name");
                BigDecimal price = new BigDecimal(rs.getString("unit_price"));
                String item = name + " - $" + price;
                productList.add(item);
                prodIdMap.put(item, id);
                priceMap.put(item, price);
            }
            
            rs.close();
            stmt.close();
            
            if (productList.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No products available");
                return;
            }
            
            String selected = (String) JOptionPane.showInputDialog(
                this,
                "Select product:",
                "Add Line",
                JOptionPane.QUESTION_MESSAGE,
                null,
                productList.toArray(),
                productList.get(0)
            );
            
            if (selected == null) return;
            
            String qtyStr = JOptionPane.showInputDialog(this, "Enter quantity:", "1");
            if (qtyStr == null) return;
            
            int quantity = 1;
            try {
                quantity = Integer.parseInt(qtyStr);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid quantity");
                return;
            }
            
            long prodId = prodIdMap.get(selected);
            BigDecimal price = priceMap.get(selected);
            String prodName = selected.substring(0, selected.lastIndexOf(" - $"));
            
            long nextLineId = tempLines.size() + 1;
            TempLine line = new TempLine(nextLineId, prodId, prodName, quantity, price);
            tempLines.add(line);
            
            refreshLines();
            calculateTotals();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void removeLine() {
        int selectedRow = linesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a line to remove");
            return;
        }
        
        tempLines.remove(selectedRow);
        refreshLines();
        calculateTotals();
    }
    
    private void save() {
        String customerName = (String) customerCombo.getSelectedItem();
        if (customerName == null) {
            JOptionPane.showMessageDialog(this, "Please select a customer");
            return;
        }
        
        Long custId = customerMap.get(customerName);
        if (custId == null) {
            JOptionPane.showMessageDialog(this, "Customer not found");
            return;
        }
        
        ArrayList<String> errors = new ArrayList<>();
        
        if (tempLines.isEmpty()) {
            errors.add("Order must have at least one line item");
        }
        
        for (int i = 0; i < tempLines.size(); i++) {
            TempLine line = tempLines.get(i);
            if (line.qty <= 0) {
                errors.add("Line " + (i + 1) + ": Quantity must be positive");
            }
            if (line.price == null || line.price.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Line " + (i + 1) + ": Unit price must be zero or greater");
            }
        }
        
        BigDecimal subtotal = BigDecimal.ZERO;
        for (TempLine line : tempLines) {
            subtotal = subtotal.add(line.price.multiply(BigDecimal.valueOf(line.qty)));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal discount = BigDecimal.ZERO;
        if (subtotal.compareTo(new BigDecimal("2000")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.15"));
        } else if (subtotal.compareTo(new BigDecimal("1000")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.10"));
        } else if (subtotal.compareTo(new BigDecimal("500")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.05"));
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        
        if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountRate = discount.divide(subtotal, 4, RoundingMode.HALF_UP);
            if (discountRate.compareTo(new BigDecimal("0.15")) > 0) {
                errors.add("Discount cannot exceed 15%");
            }
        }
        
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Validation errors:\n");
            for (String error : errors) {
                sb.append("- ").append(error).append("\n");
            }
            statusArea.setText(sb.toString());
            JOptionPane.showMessageDialog(this, sb.toString(), "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            
            BigDecimal taxableAmount = subtotal.subtract(discount);
            BigDecimal tax = taxableAmount.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal.subtract(discount).add(tax);
            
            if (orderId == 0) {
                String sql = "SELECT MAX(order_id) FROM orders";
                ResultSet rs = stmt.executeQuery(sql);
                long nextOrderId = 1;
                if (rs.next()) {
                    nextOrderId = rs.getLong(1) + 1;
                }
                orderId = nextOrderId;
                
                String insertSql = "INSERT INTO orders (order_id, cust_id, cust_name, order_date, subtotal, discount, tax, total) VALUES (" +
                    orderId + ", " + custId + ", '" + customerName.replace("'", "''") + "', datetime('now'), " +
                    subtotal + ", " + discount + ", " + tax + ", " + total + ")";
                stmt.execute(insertSql);
                
                // Get max line_id to ensure globally unique line IDs
                rs = stmt.executeQuery("SELECT MAX(line_id) FROM order_line");
                long nextLineId = 1;
                if (rs.next()) {
                    nextLineId = rs.getLong(1) + 1;
                }
                
                for (int i = 0; i < tempLines.size(); i++) {
                    TempLine line = tempLines.get(i);
                    String lineSql = "INSERT INTO order_line (line_id, order_id, prod_id, prod_name, quantity, unit_price) VALUES (" +
                        (nextLineId + i) + ", " + orderId + ", " + line.prodId + ", '" + line.prodName.replace("'", "''") + "', " +
                        line.qty + ", " + line.price + ")";
                    stmt.execute(lineSql);
                }
            } else {
                String updateSql = "UPDATE orders SET cust_id = " + custId + ", cust_name = '" + customerName.replace("'", "''") + "', " +
                    "subtotal = " + subtotal + ", discount = " + discount + ", tax = " + tax + ", total = " + total +
                    " WHERE order_id = " + orderId;
                stmt.execute(updateSql);
                
                String deleteSql = "DELETE FROM order_line WHERE order_id = " + orderId;
                stmt.execute(deleteSql);
                
                // Get max line_id to ensure globally unique line IDs
                ResultSet rs = stmt.executeQuery("SELECT MAX(line_id) FROM order_line");
                long nextLineId = 1;
                if (rs.next()) {
                    nextLineId = rs.getLong(1) + 1;
                }
                
                for (int i = 0; i < tempLines.size(); i++) {
                    TempLine line = tempLines.get(i);
                    String lineSql = "INSERT INTO order_line (line_id, order_id, prod_id, prod_name, quantity, unit_price) VALUES (" +
                        (nextLineId + i) + ", " + orderId + ", " + line.prodId + ", '" + line.prodName.replace("'", "''") + "', " +
                        line.qty + ", " + line.price + ")";
                    stmt.execute(lineSql);
                }
            }
            
            stmt.close();
            statusArea.setText("Order saved successfully");
            saved = true;
            
            javax.swing.Timer timer = new javax.swing.Timer(500, e -> dispose());
            timer.setRepeats(false);
            timer.start();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving order: " + e.getMessage());
        }
    }
    
    private void cancel() {
        saved = false;
        dispose();
    }
    
    public boolean isSaved() {
        return saved;
    }
}

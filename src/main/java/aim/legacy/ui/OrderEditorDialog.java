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
    private Map<String, String> customerTypeMap = new HashMap<>();
    private JTable linesTable;
    private DefaultTableModel linesTableModel;
    
    private JLabel subtotalLabel;
    private JLabel discountLabel;
    private JLabel taxLabel;
    private JLabel totalLabel;
    private JTextArea statusArea;
    
    // Default tax rate is 14.975% for STANDARD customers
    // Tax rates vary by customer type: STANDARD=14.975%, PREMIUM=12%, VIP=10%
    private static final BigDecimal TAX_RATE = new BigDecimal("0.14975");
    

    private class TempLine {
        long lineId;
        long prodId;
        String prodName;
        int qty;
        BigDecimal price;
        long containerId; // Reference to container
        
        TempLine(long lid, long pid, String pname, int q, BigDecimal p, long cid) {
            lineId = lid;
            prodId = pid;
            prodName = pname;
            qty = q;
            price = p;
            containerId = cid;
        }
    }
    
    private class TempShipper {
        long shipperId;
        String shipperNumber;
        String carrier;
        String trackingNumber;
        
        TempShipper(long sid, String snum, String car, String track) {
            shipperId = sid;
            shipperNumber = snum;
            carrier = car;
            trackingNumber = track;
        }
    }
    
    private class TempContainer {
        long containerId;
        long shipperId;
        String containerNumber;
        String containerType;
        
        TempContainer(long cid, long sid, String cnum, String ctype) {
            containerId = cid;
            shipperId = sid;
            containerNumber = cnum;
            containerType = ctype;
        }
    }
    
    private ArrayList<TempLine> tempLines = new ArrayList<>();
    private ArrayList<TempShipper> tempShippers = new ArrayList<>();
    private ArrayList<TempContainer> tempContainers = new ArrayList<>();
    
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
        // Add listener to recalculate totals when customer selection changes
        customerCombo.addActionListener(e -> calculateTotals());
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
            String sql = "SELECT cust_id, cust_name, customer_type FROM customer ORDER BY cust_name";
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                long id = rs.getLong("cust_id");
                String name = rs.getString("cust_name");
                String customerType = rs.getString("customer_type");
                if (customerType == null) customerType = "STANDARD";
                customerCombo.addItem(name);
                customerMap.put(name, id);
                customerTypeMap.put(name, customerType);
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
            
            // Load shippers
            sql = "SELECT shipper_id, shipper_number, carrier, tracking_number FROM shipper WHERE order_id = " + orderId;
            rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                TempShipper shipper = new TempShipper(
                    rs.getLong("shipper_id"),
                    rs.getString("shipper_number"),
                    rs.getString("carrier"),
                    rs.getString("tracking_number")
                );
                tempShippers.add(shipper);
            }
            
            rs.close();
            
            // Load containers
            sql = "SELECT container_id, shipper_id, container_number, container_type FROM container WHERE order_id = " + orderId;
            rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                TempContainer container = new TempContainer(
                    rs.getLong("container_id"),
                    rs.getLong("shipper_id"),
                    rs.getString("container_number"),
                    rs.getString("container_type")
                );
                tempContainers.add(container);
            }
            
            rs.close();
            
            // Load order lines with container reference
            sql = "SELECT line_id, prod_id, prod_name, quantity, unit_price, container_id FROM order_line WHERE order_id = " + orderId;
            rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                long containerId = rs.getLong("container_id");
                TempLine line = new TempLine(
                    rs.getLong("line_id"),
                    rs.getLong("prod_id"),
                    rs.getString("prod_name"),
                    rs.getInt("quantity"),
                    new BigDecimal(rs.getString("unit_price")),
                    containerId
                );
                tempLines.add(line);
            }
            
            rs.close();
            stmt.close();
            
            refreshLines();
            updateHierarchyStatus(); // Show hierarchy in status area
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
    
    // Display hierarchy information in status area
    private void updateHierarchyStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Order Hierarchy:\n");
        sb.append("Shippers: ").append(tempShippers.size()).append("\n");
        sb.append("Containers: ").append(tempContainers.size()).append("\n");
        sb.append("Line Items: ").append(tempLines.size()).append("\n\n");
        
        for (TempShipper shipper : tempShippers) {
            sb.append("📦 ").append(shipper.shipperNumber)
              .append(" (").append(shipper.carrier).append(")\n");
            
            for (TempContainer container : tempContainers) {
                if (container.shipperId == shipper.shipperId) {
                    sb.append("  📦 ").append(container.containerNumber)
                      .append(" (").append(container.containerType).append(")\n");
                    
                    int itemCount = 0;
                    for (TempLine line : tempLines) {
                        if (line.containerId == container.containerId) {
                            itemCount++;
                        }
                    }
                    sb.append("    Items: ").append(itemCount).append("\n");
                }
            }
        }
        
        statusArea.setText(sb.toString());
    }
    
    // Calculate order totals including discounts and tax
    // Discount and tax rules vary by customer type:
    // STANDARD: Tiered discounts (5% @ $500, 10% @ $1000, 15% @ $2000), 14.975% tax
    // PREMIUM: Tiered discounts (7% @ $400, 12% @ $800, 18% @ $1500), 12% tax
    // VIP: Flat 20% discount, 10% tax
    // Tax is applied to subtotal after discount
    private void calculateTotals() {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (TempLine line : tempLines) {
            BigDecimal lineTotal = line.price.multiply(BigDecimal.valueOf(line.qty));
            subtotal = subtotal.add(lineTotal);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        
        // Get customer type for calculation rules
        String customerName = (String) customerCombo.getSelectedItem();
        String customerType = customerTypeMap.get(customerName);
        if (customerType == null) customerType = "STANDARD";
        
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal taxRate = TAX_RATE;
        
        // Calculate discount based on customer type
        if ("VIP".equals(customerType)) {
            // VIP: Flat 20% discount
            discount = subtotal.multiply(new BigDecimal("0.20"));
            taxRate = new BigDecimal("0.10"); // 10% tax for VIP
        } else if ("PREMIUM".equals(customerType)) {
            // PREMIUM: Enhanced tiered discounts
            if (subtotal.compareTo(new BigDecimal("1500")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.18"));
            } else if (subtotal.compareTo(new BigDecimal("800")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.12"));
            } else if (subtotal.compareTo(new BigDecimal("400")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.07"));
            }
            taxRate = new BigDecimal("0.12"); // 12% tax for PREMIUM
        } else {
            // STANDARD: Original tiered discounts
            if (subtotal.compareTo(new BigDecimal("2000")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.15"));
            } else if (subtotal.compareTo(new BigDecimal("1000")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.10"));
            } else if (subtotal.compareTo(new BigDecimal("500")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.05"));
            }
            // taxRate remains TAX_RATE (14.975%) for STANDARD
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal taxableAmount = subtotal.subtract(discount);
        BigDecimal tax = taxableAmount.multiply(taxRate);
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
            // Step 1: Select or create shipper
            long shipperId = selectOrCreateShipper();
            if (shipperId == 0) return; // User cancelled
            
            // Step 2: Select or create container
            long containerId = selectOrCreateContainer(shipperId);
            if (containerId == 0) return; // User cancelled
            
            // Step 3: Select product
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
            TempLine line = new TempLine(nextLineId, prodId, prodName, quantity, price, containerId);
            tempLines.add(line);
            
            refreshLines();
            calculateTotals();
            updateHierarchyStatus();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private long getNextShipperId() {
        long max = 0;
        for (TempShipper s : tempShippers) {
            if (s.shipperId > max) max = s.shipperId;
        }
        return max + 1;
    }
    
    private long getNextContainerId() {
        long max = 0;
        for (TempContainer c : tempContainers) {
            if (c.containerId > max) max = c.containerId;
        }
        return max + 1;
    }
    
    // Select existing shipper or create new one
    private long selectOrCreateShipper() {
        ArrayList<String> shipperList = new ArrayList<>();
        shipperList.add("[Create New Shipper]");
        Map<String, Long> shipperIdMap = new HashMap<>();
        
        for (TempShipper s : tempShippers) {
            String display = s.shipperNumber + " - " + s.carrier;
            shipperList.add(display);
            shipperIdMap.put(display, s.shipperId);
        }
        
        String selected = (String) JOptionPane.showInputDialog(
            this,
            "Select shipper:",
            "Select Shipper",
            JOptionPane.QUESTION_MESSAGE,
            null,
            shipperList.toArray(),
            shipperList.get(0)
        );
        
        if (selected == null) return 0;
        
        if (selected.equals("[Create New Shipper]")) {
            // Create new shipper
            String shipperNum = JOptionPane.showInputDialog(this, "Enter shipper number:", "SHIP-" + String.format("%03d", getNextShipperId()));
            if (shipperNum == null) return 0;
            
            String carrier = JOptionPane.showInputDialog(this, "Enter carrier:", "FedEx");
            if (carrier == null) carrier = "FedEx";
            
            String tracking = JOptionPane.showInputDialog(this, "Enter tracking number:", "TRK" + System.currentTimeMillis());
            if (tracking == null) tracking = "";
            
            long newId = getNextShipperId();
            TempShipper newShipper = new TempShipper(newId, shipperNum, carrier, tracking);
            tempShippers.add(newShipper);
            return newId;
        } else {
            return shipperIdMap.get(selected);
        }
    }
    
    // Select existing container or create new one
    private long selectOrCreateContainer(long shipperId) {
        ArrayList<String> containerList = new ArrayList<>();
        containerList.add("[Create New Container]");
        Map<String, Long> containerIdMap = new HashMap<>();
        
        for (TempContainer c : tempContainers) {
            if (c.shipperId == shipperId) {
                String display = c.containerNumber + " - " + c.containerType;
                containerList.add(display);
                containerIdMap.put(display, c.containerId);
            }
        }
        
        String selected = (String) JOptionPane.showInputDialog(
            this,
            "Select container:",
            "Select Container",
            JOptionPane.QUESTION_MESSAGE,
            null,
            containerList.toArray(),
            containerList.get(0)
        );
        
        if (selected == null) return 0;
        
        if (selected.equals("[Create New Container]")) {
            // Create new container
            String containerNum = JOptionPane.showInputDialog(this, "Enter container number:", "CTN-" + String.format("%03d", getNextContainerId()));
            if (containerNum == null) return 0;
            
            String[] types = {"Box", "Pallet", "Crate", "Bag"};
            String containerType = (String) JOptionPane.showInputDialog(
                this,
                "Select container type:",
                "Container Type",
                JOptionPane.QUESTION_MESSAGE,
                null,
                types,
                types[0]
            );
            if (containerType == null) containerType = "Box";
            
            long newId = getNextContainerId();
            TempContainer newContainer = new TempContainer(newId, shipperId, containerNum, containerType);
            tempContainers.add(newContainer);
            return newId;
        } else {
            return containerIdMap.get(selected);
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
        
        // Get customer type for calculation rules
        String customerType = customerTypeMap.get(customerName);
        if (customerType == null) customerType = "STANDARD";
        
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal maxDiscountRate = new BigDecimal("0.15");
        
        // Calculate discount based on customer type
        if ("VIP".equals(customerType)) {
            discount = subtotal.multiply(new BigDecimal("0.20"));
            maxDiscountRate = new BigDecimal("0.20");
        } else if ("PREMIUM".equals(customerType)) {
            if (subtotal.compareTo(new BigDecimal("1500")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.18"));
            } else if (subtotal.compareTo(new BigDecimal("800")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.12"));
            } else if (subtotal.compareTo(new BigDecimal("400")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.07"));
            }
            maxDiscountRate = new BigDecimal("0.18");
        } else {
            // STANDARD
            if (subtotal.compareTo(new BigDecimal("2000")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.15"));
            } else if (subtotal.compareTo(new BigDecimal("1000")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.10"));
            } else if (subtotal.compareTo(new BigDecimal("500")) >= 0) {
                discount = subtotal.multiply(new BigDecimal("0.05"));
            }
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        
        if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountRate = discount.divide(subtotal, 4, RoundingMode.HALF_UP);
            if (discountRate.compareTo(maxDiscountRate) > 0) {
                errors.add("Discount cannot exceed " + maxDiscountRate.multiply(new BigDecimal("100")).intValue() + "%");
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
            
            // Calculate tax based on customer type (customerType already defined above)
            BigDecimal taxRate = TAX_RATE;
            if ("VIP".equals(customerType)) {
                taxRate = new BigDecimal("0.10");
            } else if ("PREMIUM".equals(customerType)) {
                taxRate = new BigDecimal("0.12");
            }
            
            BigDecimal taxableAmount = subtotal.subtract(discount);
            BigDecimal tax = taxableAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal.subtract(discount).add(tax);
            
            if (orderId == 0) {
                // New order - create order first
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
                
                // Save shippers with max ID lookup
                rs = stmt.executeQuery("SELECT MAX(shipper_id) FROM shipper");
                long nextShipperId = 1;
                if (rs.next()) {
                    nextShipperId = rs.getLong(1) + 1;
                }
                
                Map<Long, Long> shipperIdMap = new HashMap<>(); // tempId -> dbId
                for (int i = 0; i < tempShippers.size(); i++) {
                    TempShipper shipper = tempShippers.get(i);
                    long dbShipperId = nextShipperId + i;
                    shipperIdMap.put(shipper.shipperId, dbShipperId);
                    
                    String shipperSql = "INSERT INTO shipper (shipper_id, order_id, shipper_number, carrier, tracking_number, ship_date, total_weight) VALUES (" +
                        dbShipperId + ", " + orderId + ", '" + shipper.shipperNumber.replace("'", "''") + "', '" +
                        shipper.carrier.replace("'", "''") + "', '" + shipper.trackingNumber.replace("'", "''") + "', " +
                        "datetime('now'), 0)";
                    stmt.execute(shipperSql);
                }
                
                // Save containers
                rs = stmt.executeQuery("SELECT MAX(container_id) FROM container");
                long nextContainerId = 1;
                if (rs.next()) {
                    nextContainerId = rs.getLong(1) + 1;
                }
                
                Map<Long, Long> containerIdMap = new HashMap<>(); // tempId -> dbId
                for (int i = 0; i < tempContainers.size(); i++) {
                    TempContainer container = tempContainers.get(i);
                    long dbContainerId = nextContainerId + i;
                    containerIdMap.put(container.containerId, dbContainerId);
                    
                    long dbShipperId = shipperIdMap.get(container.shipperId);
                    String containerSql = "INSERT INTO container (container_id, shipper_id, order_id, container_number, container_type, weight, dimensions) VALUES (" +
                        dbContainerId + ", " + dbShipperId + ", " + orderId + ", '" + container.containerNumber.replace("'", "''") + "', '" +
                        container.containerType.replace("'", "''") + "', 0, '')";
                    stmt.execute(containerSql);
                }
                
                // Get max line_id to ensure globally unique line IDs
                rs = stmt.executeQuery("SELECT MAX(line_id) FROM order_line");
                long nextLineId = 1;
                if (rs.next()) {
                    nextLineId = rs.getLong(1) + 1;
                }
                
                // Save order lines with container references
                for (int i = 0; i < tempLines.size(); i++) {
                    TempLine line = tempLines.get(i);
                    long dbContainerId = containerIdMap.get(line.containerId);
                    String lineSql = "INSERT INTO order_line (line_id, order_id, container_id, prod_id, prod_name, quantity, unit_price) VALUES (" +
                        (nextLineId + i) + ", " + orderId + ", " + dbContainerId + ", " + line.prodId + ", '" + line.prodName.replace("'", "''") + "', " +
                        line.qty + ", " + line.price + ")";
                    stmt.execute(lineSql);
                }
            } else {
                // Update existing order
                String updateSql = "UPDATE orders SET cust_id = " + custId + ", cust_name = '" + customerName.replace("'", "''") + "', " +
                    "subtotal = " + subtotal + ", discount = " + discount + ", tax = " + tax + ", total = " + total +
                    " WHERE order_id = " + orderId;
                stmt.execute(updateSql);
                
                // Delete and recreate everything
                String deleteSql = "DELETE FROM order_line WHERE order_id = " + orderId;
                stmt.execute(deleteSql);
                deleteSql = "DELETE FROM container WHERE order_id = " + orderId;
                stmt.execute(deleteSql);
                deleteSql = "DELETE FROM shipper WHERE order_id = " + orderId;
                stmt.execute(deleteSql);
                
                // Recreate shippers
                ResultSet rs = stmt.executeQuery("SELECT MAX(shipper_id) FROM shipper");
                long nextShipperId = 1;
                if (rs.next()) {
                    nextShipperId = rs.getLong(1) + 1;
                }
                
                Map<Long, Long> shipperIdMap = new HashMap<>();
                for (int i = 0; i < tempShippers.size(); i++) {
                    TempShipper shipper = tempShippers.get(i);
                    long dbShipperId = nextShipperId + i;
                    shipperIdMap.put(shipper.shipperId, dbShipperId);
                    
                    String shipperSql = "INSERT INTO shipper (shipper_id, order_id, shipper_number, carrier, tracking_number, ship_date, total_weight) VALUES (" +
                        dbShipperId + ", " + orderId + ", '" + shipper.shipperNumber.replace("'", "''") + "', '" +
                        shipper.carrier.replace("'", "''") + "', '" + shipper.trackingNumber.replace("'", "''") + "', " +
                        "datetime('now'), 0)";
                    stmt.execute(shipperSql);
                }
                
                // Recreate containers
                rs = stmt.executeQuery("SELECT MAX(container_id) FROM container");
                long nextContainerId = 1;
                if (rs.next()) {
                    nextContainerId = rs.getLong(1) + 1;
                }
                
                Map<Long, Long> containerIdMap = new HashMap<>();
                for (int i = 0; i < tempContainers.size(); i++) {
                    TempContainer container = tempContainers.get(i);
                    long dbContainerId = nextContainerId + i;
                    containerIdMap.put(container.containerId, dbContainerId);
                    
                    long dbShipperId = shipperIdMap.get(container.shipperId);
                    String containerSql = "INSERT INTO container (container_id, shipper_id, order_id, container_number, container_type, weight, dimensions) VALUES (" +
                        dbContainerId + ", " + dbShipperId + ", " + orderId + ", '" + container.containerNumber.replace("'", "''") + "', '" +
                        container.containerType.replace("'", "''") + "', 0, '')";
                    stmt.execute(containerSql);
                }
                
                // Recreate order lines
                rs = stmt.executeQuery("SELECT MAX(line_id) FROM order_line");
                long nextLineId = 1;
                if (rs.next()) {
                    nextLineId = rs.getLong(1) + 1;
                }
                
                for (int i = 0; i < tempLines.size(); i++) {
                    TempLine line = tempLines.get(i);
                    long dbContainerId = containerIdMap.get(line.containerId);
                    String lineSql = "INSERT INTO order_line (line_id, order_id, container_id, prod_id, prod_name, quantity, unit_price) VALUES (" +
                        (nextLineId + i) + ", " + orderId + ", " + dbContainerId + ", " + line.prodId + ", '" + line.prodName.replace("'", "''") + "', " +
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

/**
 * OrdersScreen.java

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

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

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

        JButton viewHierarchyButton = new JButton("View Hierarchy");
        viewHierarchyButton.addActionListener(e -> viewHierarchy());
        buttonPanel.add(viewHierarchyButton);

        JButton reportButton = new JButton("Generate Report");
        reportButton.addActionListener(e -> generateReport());
        buttonPanel.add(reportButton);

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
                sql = "DELETE FROM container WHERE order_id = " + id;
                stmt.execute(sql);
                sql = "DELETE FROM shipper WHERE order_id = " + id;
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

    // View order hierarchy (shippers, containers, line items)
    private void viewHierarchy() {
        int selectedRow = orderTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an order to view hierarchy");
            return;
        }

        long orderId = (Long) tableModel.getValueAt(selectedRow, 0);

        try {
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();

            StringBuilder hierarchy = new StringBuilder();
            hierarchy.append("Order #").append(orderId).append(" Hierarchy\n");

            for (int i = 0; i < 50; i++) {
                hierarchy.append("=");
            }
            hierarchy.append("\n\n");

            // Load shippers
            String sql = "SELECT shipper_id, shipper_number, carrier, tracking_number FROM shipper WHERE order_id = " + orderId;
            ResultSet shippersRs = stmt.executeQuery(sql);

            while (shippersRs.next()) {
                long shipperId = shippersRs.getLong("shipper_id");
                String shipperNum = shippersRs.getString("shipper_number");
                String carrier = shippersRs.getString("carrier");
                String tracking = shippersRs.getString("tracking_number");

                hierarchy.append("📦 Shipper: ").append(shipperNum).append("\n");
                hierarchy.append("   Carrier: ").append(carrier).append("\n");
                hierarchy.append("   Tracking: ").append(tracking).append("\n");

                // Load containers for this shipper
                Statement stmt2 = conn.createStatement();
                String containerSql = "SELECT container_id, container_number, container_type FROM container WHERE shipper_id = " + shipperId;
                ResultSet containersRs = stmt2.executeQuery(containerSql);

                while (containersRs.next()) {
                    long containerId = containersRs.getLong("container_id");
                    String containerNum = containersRs.getString("container_number");
                    String containerType = containersRs.getString("container_type");

                    hierarchy.append("   └─ 📦 Container: ").append(containerNum).append(" (").append(containerType).append(")\n");

                    // Load line items for this container
                    Statement stmt3 = conn.createStatement();
                    String linesSql = "SELECT prod_name, quantity, unit_price FROM order_line WHERE container_id = " + containerId;
                    ResultSet linesRs = stmt3.executeQuery(linesSql);

                    while (linesRs.next()) {
                        String prodName = linesRs.getString("prod_name");
                        int qty = linesRs.getInt("quantity");
                        double price = linesRs.getDouble("unit_price");

                        hierarchy.append("      └─ ").append(prodName)
                                .append(" (Qty: ").append(qty)
                                .append(", Price: $").append(String.format("%.2f", price))
                                .append(")\n");
                    }

                    linesRs.close();
                    stmt3.close();
                }

                containersRs.close();
                stmt2.close();
                hierarchy.append("\n");
            }

            shippersRs.close();
            stmt.close();

            // Display hierarchy in a text area dialog
            JTextArea textArea = new JTextArea(hierarchy.toString());
            textArea.setEditable(false);
            textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));

            JOptionPane.showMessageDialog(this,
                    scrollPane,
                    "Order Hierarchy",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading hierarchy: " + e.getMessage());
        }
    }

    // Generate order summary report
    // Creates PDF report in reports directory
    private void generateReport() {
        try {
            String reportDir = "reports";
            String reportFilename = "OrderSummary.pdf";

            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = reportDir + File.separator + reportFilename;

            // Create PDF document
            Document document = new Document(PageSize.LETTER, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // PDF formatting constants defined inline
            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 18, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 12, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 10, com.itextpdf.text.Font.NORMAL);
            com.itextpdf.text.Font smallFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 8, com.itextpdf.text.Font.NORMAL);

            // Add header section
            Paragraph company = new Paragraph("AIM Order Entry System", headerFont);
            company.setAlignment(Element.ALIGN_CENTER);
            document.add(company);
            document.add(new Paragraph(" "));

            Paragraph title = new Paragraph("Order Summary Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            Paragraph timestamp = new Paragraph("Generated: " + sdf.format(new Date()), smallFont);
            timestamp.setAlignment(Element.ALIGN_CENTER);
            document.add(timestamp);

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            // Create orders table directly in this method
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.0f, 2.5f, 2.0f, 1.5f, 1.5f, 1.5f, 1.5f});

            // Add header row
            BaseColor headerColor = new BaseColor(200, 200, 200);
            String[] headers = {"Order ID", "Customer", "Date", "Subtotal", "Discount", "Tax", "Total"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBackgroundColor(headerColor);
                cell.setPadding(5);
                table.addCell(cell);
            }

            // Query database directly - no repository pattern
            Connection conn = DB.getConn();
            Statement stmt = conn.createStatement();
            String sql = "SELECT order_id, cust_name, order_date, subtotal, discount, tax, total FROM orders ORDER BY order_id";
            ResultSet rs = stmt.executeQuery(sql);

            // Summary variables
            int totalOrders = 0;
            double totalRevenue = 0.0;
            double totalDiscounts = 0.0;
            double totalTax = 0.0;

            // Add data rows with alternating colors
            boolean alternate = false;
            BaseColor lightGray = new BaseColor(240, 240, 240);

            while (rs.next()) {
                totalOrders++;

                long orderId = rs.getLong("order_id");
                String custName = rs.getString("cust_name");
                String orderDate = rs.getString("order_date");
                double subtotal = rs.getDouble("subtotal");
                double discount = rs.getDouble("discount");
                double tax = rs.getDouble("tax");
                double total = rs.getDouble("total");

                totalRevenue += total;
                totalDiscounts += discount;
                totalTax += tax;

                BaseColor rowColor = alternate ? lightGray : BaseColor.WHITE;

                // Format and add cells
                PdfPCell cell;

                cell = new PdfPCell(new Phrase(String.valueOf(orderId), normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase(custName, normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                // Format date inline
                String formattedDate = orderDate;
                try {
                    if (orderDate != null && orderDate.length() >= 16) {
                        String[] parts = orderDate.split(" ");
                        if (parts.length >= 2) {
                            String[] dateParts = parts[0].split("-");
                            String time = parts[1].substring(0, 5);
                            formattedDate = dateParts[1] + "/" + dateParts[2] + "/" + dateParts[0] + " " + time;
                        }
                    }
                } catch (Exception e) {
                }

                cell = new PdfPCell(new Phrase(formattedDate, normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                // Currency formatting inline - repeated code
                cell = new PdfPCell(new Phrase("$" + String.format("%.2f", subtotal), normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase("$" + String.format("%.2f", discount), normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase("$" + String.format("%.2f", tax), normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase("$" + String.format("%.2f", total), normalFont));
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setBackgroundColor(rowColor);
                cell.setPadding(5);
                table.addCell(cell);

                alternate = !alternate;
            }

            rs.close();
            stmt.close();

            document.add(table);

            // Add summary section
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            Paragraph summaryTitle = new Paragraph("Summary Statistics", headerFont);
            document.add(summaryTitle);
            document.add(new Paragraph(" "));

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(50);
            summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            summaryTable.setWidths(new float[]{3.0f, 2.0f});

            BaseColor summaryColor = new BaseColor(230, 230, 230);

            // Add summary rows
            PdfPCell labelCell = new PdfPCell(new Phrase("Total Orders:", normalFont));
            labelCell.setBackgroundColor(summaryColor);
            labelCell.setPadding(5);
            labelCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(String.valueOf(totalOrders), headerFont));
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setBackgroundColor(summaryColor);
            valueCell.setPadding(5);
            valueCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(valueCell);

            labelCell = new PdfPCell(new Phrase("Total Revenue:", normalFont));
            labelCell.setBackgroundColor(summaryColor);
            labelCell.setPadding(5);
            labelCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(labelCell);

            valueCell = new PdfPCell(new Phrase("$" + String.format("%.2f", totalRevenue), headerFont));
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setBackgroundColor(summaryColor);
            valueCell.setPadding(5);
            valueCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(valueCell);

            labelCell = new PdfPCell(new Phrase("Total Discounts:", normalFont));
            labelCell.setBackgroundColor(summaryColor);
            labelCell.setPadding(5);
            labelCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(labelCell);

            valueCell = new PdfPCell(new Phrase("$" + String.format("%.2f", totalDiscounts), headerFont));
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setBackgroundColor(summaryColor);
            valueCell.setPadding(5);
            valueCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(valueCell);

            labelCell = new PdfPCell(new Phrase("Total Tax Collected:", normalFont));
            labelCell.setBackgroundColor(summaryColor);
            labelCell.setPadding(5);
            labelCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(labelCell);

            valueCell = new PdfPCell(new Phrase("$" + String.format("%.2f", totalTax), headerFont));
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setBackgroundColor(summaryColor);
            valueCell.setPadding(5);
            valueCell.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
            summaryTable.addCell(valueCell);

            document.add(summaryTable);

            // Close document
            document.close();

            JOptionPane.showMessageDialog(this,
                    "Report generated successfully!\nSaved to: " + filePath,
                    "Report Generated",
                    JOptionPane.INFORMATION_MESSAGE);

            // Ask user if they want to open the report
            int openFile = JOptionPane.showConfirmDialog(this,
                    "Would you like to open the report?",
                    "Open Report",
                    JOptionPane.YES_NO_OPTION);

            if (openFile == JOptionPane.YES_OPTION) {
                java.awt.Desktop.getDesktop().open(new java.io.File(filePath));
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error generating report: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}

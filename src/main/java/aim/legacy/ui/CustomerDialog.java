/**
 * CustomerDialog.java
 * 
 * Dialog for adding or editing customer information.
 * Validates input and returns customer data to calling screen.
 * Uses simple modal dialog pattern for user interaction.
 */
package aim.legacy.ui;

import javax.swing.*;
import java.awt.*;

public class CustomerDialog extends JDialog {

    private boolean saved = false;
    
    private JTextField nameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField addressField;
    
    private long custId;
    
    public CustomerDialog(Frame parent, long id, String name, String email, String phone, String address) {
        super(parent, id == 0 ? "Add Customer" : "Edit Customer", true);
        this.custId = id;
        
        setupUI();
        
        if (id > 0) {
            nameField.setText(name);
            emailField.setText(email);
            phoneField.setText(phone);
            addressField.setText(address);
        }
        
        setSize(400, 250);
        setLocationRelativeTo(parent);
    }
    
    private void setupUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JTextField(20);
        panel.add(nameField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        emailField = new JTextField(20);
        panel.add(emailField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        phoneField = new JTextField(20);
        panel.add(phoneField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Address:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        addressField = new JTextField(20);
        panel.add(addressField, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> save());
        buttonPanel.add(saveButton);
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        buttonPanel.add(cancelButton);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buttonPanel, gbc);
        
        add(panel);
    }
    
    private void save() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required");
            return;
        }
        
        saved = true;
        dispose();
    }
    
    private void cancel() {
        saved = false;
        dispose();
    }
    
    public boolean isSaved() {
        return saved;
    }
    
    public String getName() {
        return nameField.getText().trim();
    }
    
    public String getEmail() {
        return emailField.getText().trim();
    }
    
    public String getPhone() {
        return phoneField.getText().trim();
    }
    
    public String getAddress() {
        return addressField.getText().trim();
    }
}

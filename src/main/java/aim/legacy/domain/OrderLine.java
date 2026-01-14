/**
 * OrderLine.java
 * 
 * Represents a single line item within an order.
 * Contains product reference, quantity, and pricing information.
 */
package aim.legacy.domain;

import java.math.BigDecimal;
import java.util.Objects;

public class OrderLine {
    private Long id;
    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    public OrderLine() {
    }

    public OrderLine(Long id, Long productId, String productName, int quantity, BigDecimal unitPrice) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    // Calculates the total for this line item
    // Returns quantity multiplied by unit price with proper rounding
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderLine orderLine = (OrderLine) o;
        return Objects.equals(id, orderLine.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return productName + " x" + quantity + " @ $" + unitPrice + " = $" + getLineTotal();
    }
}

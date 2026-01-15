/**
 * Shipper.java 
 * 
 * Represents a shipment grouping within an order.
 */
package aim.legacy.domain;

import java.util.Objects;

public class Shipper {
    private Long shipperId;
    private Long orderId;
    private String shipperNumber;
    private String carrier;
    private String trackingNumber;
    private String shipDate;
    private Double totalWeight;
    
    public Shipper() {
    }
    
    public Shipper(Long shipperId, Long orderId, String shipperNumber, String carrier, 
                   String trackingNumber, String shipDate, Double totalWeight) {
        this.shipperId = shipperId;
        this.orderId = orderId;
        this.shipperNumber = shipperNumber;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shipDate = shipDate;
        this.totalWeight = totalWeight;
    }
    
    // Getters and setters
    public Long getShipperId() {
        return shipperId;
    }
    
    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public String getShipperNumber() {
        return shipperNumber;
    }
    
    public void setShipperNumber(String shipperNumber) {
        this.shipperNumber = shipperNumber;
    }
    
    public String getCarrier() {
        return carrier;
    }
    
    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }
    
    public String getTrackingNumber() {
        return trackingNumber;
    }
    
    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
    
    public String getShipDate() {
        return shipDate;
    }
    
    public void setShipDate(String shipDate) {
        this.shipDate = shipDate;
    }
    
    public Double getTotalWeight() {
        return totalWeight;
    }
    
    public void setTotalWeight(Double totalWeight) {
        this.totalWeight = totalWeight;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shipper shipper = (Shipper) o;
        return Objects.equals(shipperId, shipper.shipperId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(shipperId);
    }
    
    @Override
    public String toString() {
        return "Shipper{" +
                "shipperId=" + shipperId +
                ", orderId=" + orderId +
                ", shipperNumber='" + shipperNumber + '\'' +
                ", carrier='" + carrier + '\'' +
                ", trackingNumber='" + trackingNumber + '\'' +
                ", shipDate='" + shipDate + '\'' +
                ", totalWeight=" + totalWeight +
                '}';
    }
}

/**
 * Container.java
 * 
 * Represents a physical container (box, pallet, etc.) within a shipment.
 * Legacy pattern: Contains denormalized data (orderId) for "convenience"
 */
package aim.legacy.domain;

import java.util.Objects;

public class Container {
    private Long containerId;
    private Long shipperId;
    private Long orderId; // Denormalized - should derive from shipper, but stored for "convenience"
    private String containerNumber;
    private String containerType;
    private Double weight;
    private String dimensions;
    
    public Container() {
    }
    
    public Container(Long containerId, Long shipperId, Long orderId, String containerNumber,
                     String containerType, Double weight, String dimensions) {
        this.containerId = containerId;
        this.shipperId = shipperId;
        this.orderId = orderId;
        this.containerNumber = containerNumber;
        this.containerType = containerType;
        this.weight = weight;
        this.dimensions = dimensions;
    }
    
    // Getters and setters
    public Long getContainerId() {
        return containerId;
    }
    
    public void setContainerId(Long containerId) {
        this.containerId = containerId;
    }
    
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
    
    public String getContainerNumber() {
        return containerNumber;
    }
    
    public void setContainerNumber(String containerNumber) {
        this.containerNumber = containerNumber;
    }
    
    public String getContainerType() {
        return containerType;
    }
    
    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }
    
    public Double getWeight() {
        return weight;
    }
    
    public void setWeight(Double weight) {
        this.weight = weight;
    }
    
    public String getDimensions() {
        return dimensions;
    }
    
    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Container container = (Container) o;
        return Objects.equals(containerId, container.containerId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(containerId);
    }
    
    @Override
    public String toString() {
        return "Container{" +
                "containerId=" + containerId +
                ", shipperId=" + shipperId +
                ", orderId=" + orderId +
                ", containerNumber='" + containerNumber + '\'' +
                ", containerType='" + containerType + '\'' +
                ", weight=" + weight +
                ", dimensions='" + dimensions + '\'' +
                '}';
    }
}

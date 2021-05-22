package com.boa.orderbook;

import com.boa.orderbook.client.OrderBookClientHandle;
import com.boa.orderbook.util.Analyzer;

import java.io.Serializable;

public class Order implements Serializable {
    private static final long serialVersionUID = 8822833371248140397L;

    private Long orderId;
    private String clientId;
    private String securityId;
    private Integer orderQty;
    private Double orderPrice;
    private String orderSide;
    private Long priorityTime;
    private Long displayTime;
    private OrderBookClientHandle clientHandle;

    public Order(String clientId, String securityId, Integer orderQty, Double orderPrice, String orderSide, long timestamp, OrderBookClientHandle clientHandle) {
        this.orderId = OrderIdGenerator.getInstance().getId();
        this.clientId = clientId;
        this.securityId = securityId;
        this.orderQty = orderQty;
        this.orderPrice = orderPrice;
        this.orderSide = orderSide;
        this.priorityTime = timestamp;
        this.displayTime = timestamp;
        this.clientHandle = clientHandle;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSecurityId() {
        return securityId;
    }

    public Integer getOrderQty() {
        synchronized (orderQty) {
            return orderQty;
        }
    }

    public void setOrderQty(Integer orderQty) {
        synchronized (orderQty) {
            this.orderQty = orderQty;
        }
    }

    public void setDisplayTime(Long milliseconds) {
        synchronized (displayTime) {
            this.displayTime = milliseconds;
        }
    }

    public Double getOrderPrice() {
        return orderPrice;
    }

    public String getOrderSide() {
        return orderSide;
    }

    public Long getDisplayTime() {
        synchronized (displayTime) {
            return displayTime;
        }
    }

    public Long getPriorityTime() {
        synchronized (priorityTime) {
            return priorityTime;
        }
    }

    public OrderBookClientHandle getClientHandle() {
        return clientHandle;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((orderId == null) ? 0 : orderId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Order other = (Order) obj;
        if (orderId == null) {
            return other.orderId == null;
        } else return orderId.equals(other.orderId);
    }

    @Override
    public String toString() {
        return "ORDERID=" + orderId + " CLIENT=" + clientId + " SECURITY=" + securityId
                + " QTY=" + orderQty + " PRICE=" + orderPrice + " SIDE="
                + orderSide + ", TIMESTAMP=" + Analyzer.milliSecondsToTimestamp(displayTime);
    }

}

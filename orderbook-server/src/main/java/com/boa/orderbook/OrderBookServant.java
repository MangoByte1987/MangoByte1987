package com.boa.orderbook;

import com.boa.orderbook.client.OrderBookClientHandle;
import com.boa.orderbook.server.OrderBookService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class OrderBookServant implements OrderBookService {
    private final PriorityOrderBook orders;

    public OrderBookServant() throws RemoteException {
        System.out.println("Servant init");
        this.orders = new PriorityOrderBook();
        UnicastRemoteObject.exportObject(this, 0);
    }

    @Override
    public List<Order> listOrders() {
        return orders.getAllOrders();
    }

    @Override
    public void bookOrder(String clientId, String securityId, Integer orderQty, Double orderPrice, String orderSide, OrderBookClientHandle clientHandler)
            throws RemoteException {
        Order bookedOrder = new Order(clientId, securityId, orderQty, orderPrice,
                orderSide, System.currentTimeMillis(), clientHandler);
        System.out.println("Booking...");
        if (orderSide.equals("BUY")) {
            orders.buy(bookedOrder);
        } else {
            orders.sell(bookedOrder);
        }
        System.out.println(bookedOrder);
    }

    @Override
    public void clientExits(String clientId) {
        System.out.println("Client " + clientId + " has exited. We remove his orders.");
        orders.remove(clientId);
    }

    /**
     * Notifies all pending orders as cancelled and shuts down the service
     * Note that this method is not part of the interface because
     * clients don't need to know about it.
     */
    public void finishSession() {
        System.out.println("Finishing: " + orders.toString());
        List<Order> allOrders = orders.getAllOrders();
        for (Order order : allOrders) {
            try {
                order.getClientHandle().notifyOrderCancelled(order.getSecurityId());
            } catch (RemoteException e) {
                System.out.println("Attempted to notify a client that has probably disconnected");
            }
        }
        orders.clear();
    }

}

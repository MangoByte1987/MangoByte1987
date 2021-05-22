package com.boa.orderbook.server;

import com.boa.orderbook.Order;
import com.boa.orderbook.client.OrderBookClientHandle;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface OrderBookService extends Remote {

    List<Order> listOrders() throws RemoteException;

    void bookOrder(String clientId, String securityId, Integer orderQty, Double orderPrice, String orderSide, OrderBookClientHandle clientHandler) throws RemoteException;

    void clientExits(String clientId) throws RemoteException;

}

package com.boa.orderbook;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

public class PriorityOrderBook {
    private static final int INITIAL_CAPACITY = 100;
    private Map<String, PriorityBlockingQueue<Order>> buyMap;
    private Map<String, PriorityBlockingQueue<Order>> sellMap;

    public PriorityOrderBook() {
        this.buyMap = new ConcurrentHashMap<>();
        this.sellMap = new ConcurrentHashMap<>();
    }

    public Double sell(Order sellOrder) throws RemoteException {
        if (sellOrder.getOrderSide().equals("BUY") || sellOrder.getOrderQty() <= 0) {
            throw new IllegalArgumentException("Attempted selling a buying order");
        }
        String desiredSecurity = sellOrder.getSecurityId();
        Double transactionValue = 0.0;
        PriorityBlockingQueue<Order> buyQueueForSecurity = buyMap.get(desiredSecurity);
        if (buyQueueForSecurity != null) {
            requireClientDoesntExist(buyQueueForSecurity, sellOrder);
            transactionValue = match(buyQueueForSecurity, sellOrder);
        }
        if (sellOrder.getOrderQty() > 0) {
            if (sellMap.containsKey(desiredSecurity)) {
                sellMap.get(desiredSecurity).offer(sellOrder);
            } else {
                PriorityBlockingQueue<Order> pq = new PriorityBlockingQueue<Order>(INITIAL_CAPACITY, new SellSideComparator());
                pq.offer(sellOrder);
                sellMap.put(desiredSecurity, pq);
            }
        }
        return transactionValue;
    }

    public Double buy(Order buyOrder) throws RemoteException {
        if (!buyOrder.getOrderSide().equals("BUY") || buyOrder.getOrderQty() <= 0) {
            throw new IllegalArgumentException("Attempted buying a selling order");
        }
        String desiredSecurity = buyOrder.getSecurityId();
        Double transactionValue = 0.0;
        PriorityBlockingQueue<Order> sellQueueForSecurity = sellMap.get(desiredSecurity);
        if (sellQueueForSecurity != null) {
            requireClientDoesntExist(sellQueueForSecurity, buyOrder);
            transactionValue = match(sellQueueForSecurity, buyOrder);
        }
        if (buyOrder.getOrderQty() > 0) {
            if (buyMap.containsKey(desiredSecurity)) {
                buyMap.get(desiredSecurity).offer(buyOrder);
            } else {
                PriorityBlockingQueue<Order> pq = new PriorityBlockingQueue<Order>(INITIAL_CAPACITY, new BuySideComparator());
                pq.offer(buyOrder);
                buyMap.put(desiredSecurity, pq);
            }
        }
        return transactionValue;
    }

    // we expect that buyer & seller can't be the same user for the same security.
    // otherwise we would have to iterate the pq in case the bestcandidate
    // is one of the same user's orders.
    private void requireClientDoesntExist(PriorityBlockingQueue<Order> pq, Order order) {
        List<Order> st = pq.stream()
                .filter(o -> o.getClientId().equals(order.getClientId())).collect(Collectors.toList());
        if (st.size() > 0) {
            String msg = order.getClientId() +
                    " is Trying to buy and Sell the same security, which we don't allow";
            System.err.println(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    private Double match(PriorityBlockingQueue<Order> pq, Order o) throws RemoteException {
        Order bestCandidate = pq.peek();
        if (bestCandidate == null || o.getOrderQty() == 0) {
            return 0.0;
        }
        String security = o.getSecurityId();
        Double transactionValue = bestCandidate.getOrderPrice();
        int placedUnits = 0;
        boolean shouldMakeTransaction = o.getOrderSide().equals("BUY") ? (o.getOrderPrice() >= transactionValue) : (o.getOrderPrice() <= transactionValue);
        if (shouldMakeTransaction) {
            int oUnits = o.getOrderQty();
            int bestCandidateUnits = bestCandidate.getOrderQty();

            if (oUnits > bestCandidateUnits) {
                placedUnits = bestCandidateUnits;
                o.setOrderQty(oUnits - bestCandidateUnits);
                transactionValue = bestCandidate.getOrderPrice();
                o.getClientHandle().notifyOrderMatched(security, placedUnits, transactionValue, o.getOrderSide());
                bestCandidate.setOrderQty(0);
                bestCandidate.getClientHandle().notifyOrderMatched(security, placedUnits, transactionValue, bestCandidate.getOrderSide());
                pq.remove(bestCandidate);
            } else if (oUnits < bestCandidateUnits) {
                placedUnits = oUnits;
                o.setOrderQty(0);
                o.getClientHandle().notifyOrderMatched(security, placedUnits, transactionValue, o.getOrderSide());
                bestCandidate.setOrderQty(bestCandidateUnits - oUnits);
                bestCandidate.getClientHandle().notifyOrderMatched(security, placedUnits, transactionValue, bestCandidate.getOrderSide());
            } else {
                placedUnits = oUnits;//either one...
                o.setOrderQty(0);
                o.getClientHandle().notifyOrderMatched(security, placedUnits, transactionValue, o.getOrderSide());
                bestCandidate.setOrderQty(0);
                bestCandidate.getClientHandle().notifyOrderMatched(security, placedUnits, transactionValue, bestCandidate.getOrderSide());
                pq.remove(bestCandidate);
            }
            //If we still have units, attempt to match recursively
            return transactionValue * placedUnits + match(pq, o);
        }
        return transactionValue;
    }

    public void clear() {
        buyMap.clear();
        sellMap.clear();
    }

    public void remove(String clientId) {
        removeFromMap(clientId, buyMap);
        removeFromMap(clientId, sellMap);
    }

    private void removeFromMap(String clientId, Map<String, PriorityBlockingQueue<Order>> map) {
        Set<String> keys = map.keySet();
        for (String key : keys) {
            PriorityBlockingQueue<Order> securitiesForKey = map.get(key);
            securitiesForKey.removeIf(o -> o.getClientId().equals(clientId));
        }
    }

    public List<Order> getAllOrders() {
        List<Order> ret = new LinkedList<Order>();
        dumpMap(ret, buyMap);
        dumpMap(ret, sellMap);
        return ret;
    }

    private void dumpMap(Collection<Order> ret, Map<String, PriorityBlockingQueue<Order>> map) {
        Set<String> keys = map.keySet();
        for (String key : keys) {
            PriorityBlockingQueue<Order> securitiesForKey = map.get(key);
            PriorityBlockingQueue<Order> securitiesForKeyClone = new PriorityBlockingQueue<Order>(INITIAL_CAPACITY,securitiesForKey.comparator());
            for (Order order : securitiesForKey) {
                //				System.out.println("Offered "+ order);
                securitiesForKeyClone.offer(order);
            }
            while (!securitiesForKeyClone.isEmpty()) {
                Order polled = securitiesForKeyClone.poll();
                //				System.out.println("Polled "+ polled);
                ret.add(polled);
            }
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("========START==========\n");
        sb.append("BUYING: \n");
        List<Order> buyingOrders = new LinkedList<Order>();
        dumpMap(buyingOrders, buyMap);
        for (Order order : buyingOrders) {
            sb.append(order.toString() + "\n");
        }
        sb.append("SELLING: \n");
        List<Order> sellingOrders = new LinkedList<Order>();
        dumpMap(sellingOrders, sellMap);
        for (Order order : sellingOrders) {
            sb.append(order.toString() + "\n");
        }
        sb.append("========END==========\n");
        return sb.toString();
    }

    static class BuySideComparator implements Comparator<Order> {

        /**
         * The orders are listed Highest to Lowest on the Buy Side, we do the opposite of natural order on the value.
         * Returns: a positive integer if Order one is of LESS value than Order two.
         * A negative integer if Order one is of GREATER value than Order two.
         * or, if they are of the same value, it prioritizes orders that arrived earlier.
         */
        public int compare(Order one, Order two) {
            if (!one.getSecurityId().equals(two.getSecurityId())) {
                System.err.println("These orders are not comparable, they need to be for the same security");
                new IllegalArgumentException();
            }
            int naturalOrder = Double.compare(one.getOrderPrice(), two.getOrderPrice());
            if (naturalOrder == 0) {
                return Long.compare(one.getPriorityTime(), two.getPriorityTime());
            }
            return -(naturalOrder);
        }
    }

    static class SellSideComparator implements Comparator<Order> {

        /**
         * Because orders are listed Lowest to Highest on the Sell Side, we use natural ordering.
         * Returns: a positive integer if Order one is of GREATER value than Order two.
         * a negative integer if Order one is of LESS value than Order two.
         * or, if they are of the same value, it prioritizes orders that arrived earlier.
         */
        public int compare(Order one, Order two) {
            if (!one.getSecurityId().equals(two.getSecurityId())) {
                System.err.println("These orders are not comparable, they need to be for the same security");
                new IllegalArgumentException();
            }
            int naturalOrder = Double.compare(one.getOrderPrice(), two.getOrderPrice());
            if (naturalOrder == 0) {
                return Long.compare(one.getPriorityTime(), two.getPriorityTime());
            }
            return naturalOrder;
        }
    }
}

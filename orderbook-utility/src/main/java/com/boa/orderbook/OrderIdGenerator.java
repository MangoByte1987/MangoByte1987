package com.boa.orderbook;

public class OrderIdGenerator {

    /**
     * Simulates an auto incremented value from a database somewhere.
     **/
    private static Long currentOrderId = 0L;
    private static OrderIdGenerator instance;

    private OrderIdGenerator() {
    }

    ;

    /**
     * So no one can instantiate a second copy
     **/

    public static OrderIdGenerator getInstance() {
        if (instance == null) {
            synchronized (OrderIdGenerator.class) {
                if (instance == null) {
                    instance = new OrderIdGenerator();
                }
            }
        }
        return instance;
    }

    /**
     * Generates a system-wide unique order id
     **/
    public Long getId() {
        synchronized (currentOrderId) {
            return currentOrderId++;
        }
    }

}

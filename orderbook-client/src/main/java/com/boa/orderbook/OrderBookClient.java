package com.boa.orderbook;

import com.boa.orderbook.client.OrderBookClientHandle;
import com.boa.orderbook.server.OrderBookService;
import com.boa.orderbook.util.Analyzer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Objects;

public class OrderBookClient {

    private static OrderBookClientHandleImpl clientHandler;
    private static String clientId;
    private static OrderBookService serverHandle;

    public static void main(String[] args) {
        try {
            Analyzer auxi = new Analyzer(args);
            Object port = auxi.get("PORT");
            Object hostname = auxi.get("HOSTNAME");
            Object service = auxi.get("SERVICE");
            clientId = (String) auxi.get("CLIENT");
            if (clientId == null) {
                System.err.println("Please authenticate by passing your clientId through cli arguments: CLIENT=myId");
                System.exit(-1);
            }
            auxi.dump();

            serverHandle = (OrderBookService) Naming.lookup(String.format("//%s:%s/%s",
                    hostname, port, service));

            clientHandler = new OrderBookClientHandleImpl(clientId);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println("The client had to quit. All pending orders will be cancelled");
                    finishSession();
                }
            });

            do {
                System.out.println("Enter your command(s) separated by a new line (return key):");
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String line;
                while (!(line = br.readLine()).equals("")) {
                    String[] input = line.split(" ");
                    if (input.length == 1 && input[0].equalsIgnoreCase("LIST")) {
                        listAllOrders(serverHandle, clientHandler);
                    } else {
                        try {
                            parseTransaction(clientId, serverHandle, clientHandler, new Analyzer(input));
                        } catch (NullPointerException e) {
                            System.err.println("Error: " + e.getMessage());
                            System.err.println("A transaction should look like this: "
                                    + "SECURITY=ABC QTY=20 PRICE=20.19 SIDE=BUY");
                        }
                    }
                }
            } while (true);
        } catch (Exception e) {
            System.err.println("Can't connect now... Try again when trade sessions open" + e);
            System.exit(-1);
        } finally {
            finishSession();
        }
    }


    private static void finishSession() {
        try {
            serverHandle.clientExits(clientId);
            clientHandler.unexport();
        } catch (NoSuchObjectException e) {
            System.err.println("Error while unexporting handler");
        } catch (RemoteException e) {
            System.err.println("The server is down, your orders are probably canceled already.");
        }
    }

    private static void parseTransaction(String clientId, OrderBookService serverHandle, OrderBookClientHandle clientHandler, Analyzer command) throws RemoteException {
        String securityId = Objects.requireNonNull(command.get("SECURITY"), "Must enter a SECURITY").toString();
        Integer orderQty = Integer.valueOf(Objects.requireNonNull(command.get("QTY"), "Must enter an QTY").toString());
        Double orderPrice = Double.valueOf(Objects.requireNonNull(command.get("PRICE"), "Must enter a PRICE").toString());
        String orderSide = Objects.requireNonNull(command.get("SIDE"), "Must indicate a SIDE (BUY/SELL)").toString();
        try {
            serverHandle.bookOrder(clientId, securityId, orderQty, orderPrice, orderSide, clientHandler);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void listAllOrders(OrderBookService serverHandle, OrderBookClientHandleImpl clientHandle) throws RemoteException {
        System.out.println("=============BEGIN==============");
        System.out.println("=== Server state - All current orders ===");
        final List<Order> orders = serverHandle.listOrders();
        for (Order order : orders) {
            System.out.println(order);
        }
        System.out.println("\n\n===  Client state - All orders for the day ===");
        List<String> transactions = clientHandle.getTransactionsLog();
        for (String transaction : transactions) {
            System.out.println(transaction);
        }
        System.out.println("=============END==============");
    }

}

/*
 * roomstore - an irc journaller using cassandra.
 *
 * Copyright 2011-2012 MeBigFatGuy.com
 * Copyright 2011-2012 Dave Brosius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.mebigfatguy.roomstore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Cassandra.Client;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectionPool {
    private static final int DEFAULT_PORT = 9160;
    private static final long MAINTENANCE_INTERVAL = 30*1000;
    private static final Integer ZERO = Integer.valueOf(0);
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraWriter.class);

    private Thread maintenanceThread;
    private Object failLock = new Object();
    private Object activeLock = new Object();
    private Map<String, Integer> failedServers = new HashMap<String, Integer>();
    private Map<String, TTransport> transports = new HashMap<String, TTransport>();
    private Map<String, Client> freeClients = new HashMap<String, Client>();
    private Map<Client, LeaseDetails> inUseClients = new HashMap<Client, LeaseDetails>();

    public ConnectionPool(String... servers) throws Exception {

        for (String server : servers) {
            failedServers.put(server, ZERO);
        }

        retryFailedServers();

        if (freeClients.isEmpty()) {
            throw new Exception("Failed connecting to any servers: " + Arrays.toString(servers));
        }

        maintenanceThread = new Thread(new MaintenanceJob());
        maintenanceThread.setName("CP-MAINTENANCE");
        maintenanceThread.setPriority(Thread.MIN_PRIORITY);
        maintenanceThread.setDaemon(true);
        maintenanceThread.start();
    }

    public void terminate() {
        try {
            maintenanceThread.interrupt();
            maintenanceThread.join();
        } catch (InterruptedException ie) {

        }

        failedServers.clear();
        freeClients.clear();
        inUseClients.clear();
        for (TTransport transport : transports.values()) {
            transport.close();
        }
        transports.clear();
    }

    public Client lease(long leaseTimeout, LeaseListener listener) throws InterruptedException {
        assert (leaseTimeout > 0) : "Lease timeout must be positive";

        Client client = null;
        LeaseDetails details = new LeaseDetails();
        synchronized(activeLock) {
            while (freeClients.size() == 0) {
                activeLock.wait();
            }
            Iterator<Map.Entry<String, Client>> it = freeClients.entrySet().iterator();
            Map.Entry<String, Client> entry = it.next();
            details.server = entry.getKey();
            client = entry.getValue();
            details.leaseEndTime = System.currentTimeMillis() + leaseTimeout;
            details.listener = listener;

            it.remove();
            inUseClients.put(entry.getValue(), details);
        }


        return client;
    }

    public void recycle(Client client) {
        if (client == null) {
            return;
        }

        synchronized(activeLock) {
            LeaseDetails details = inUseClients.remove(client);
            if (details != null) {
                freeClients.put(details.server, client);
                activeLock.notifyAll();
            }
        }
    }

    public void renew(Client client, long leaseTimeout) {
        synchronized(activeLock) {
            for (Map.Entry<Client, LeaseDetails> entry : inUseClients.entrySet()) {
                if (entry.getKey() == client) {
                    entry.getValue().leaseEndTime = System.currentTimeMillis() + leaseTimeout;
                    break;
                }
            }
        }
    }

    private void retryFailedServers() {
        synchronized(failLock) {
            for (Map.Entry<String, Integer> entry : failedServers.entrySet()) {
                String server = entry.getKey();
                try {
                    String[] serverParts = server.split("/");
                    String host = serverParts[0];
                    int port;
                    if (serverParts.length > 0) {
                        port = Integer.parseInt(serverParts[1]);
                    } else {
                        port = DEFAULT_PORT;
                    }

                    TTransport transport = new TFramedTransport(new TSocket(host, port));
                    TProtocol proto = new TBinaryProtocol(transport);
                    Client client = new Cassandra.Client(proto);
                    transport.open();

                    synchronized(activeLock) {
                        transports.put(server, transport);
                        freeClients.put(server, client);
                        failedServers.remove(server);
                        activeLock.notifyAll();
                    }

                } catch (Exception e) {
                    LOGGER.error("Failed creating connection to: " + server + ". This is the " + entry.getValue() + " time in a row", e);
                }
            }
        }
    }

    class LeaseDetails {
        String server;
        long leaseEndTime;
        LeaseListener listener;
    }

    class MaintenanceJob implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    Thread.sleep(MAINTENANCE_INTERVAL);

                    Client client = null;
                    LeaseDetails details = null;

                    long now = System.currentTimeMillis();
                    synchronized(activeLock) {
                        Iterator<Map.Entry<Client, LeaseDetails>> it = inUseClients.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry<Client, LeaseDetails> entry = it.next();
                            details = entry.getValue();
                            if (details.leaseEndTime > now) {
                                it.remove();
                                client = entry.getKey();
                                break;
                            }
                        }
                        if (details != null) {
                            TTransport transport = transports.remove(details.server);
                            transport.close();
                        }
                    }

                    if (details != null) {
                        synchronized(failLock) {
                            failedServers.put(details.server, ZERO);
                        }

                        if (details.listener != null) {
                            details.listener.leaseTimedout(details.server, client);
                        }
                    }

                    retryFailedServers();
                }
            } catch (InterruptedException ie) {
            }
        }
    }

    public interface LeaseListener {
        void leaseTimedout(String server, Client client);
    }
}

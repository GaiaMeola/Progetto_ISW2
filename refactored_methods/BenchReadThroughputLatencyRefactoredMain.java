/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.bookkeeper.benchmark;

import org.apache.bookkeeper.client.BKException;
import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.bookkeeper.client.BookKeeper;
import org.apache.bookkeeper.client.LedgerHandle;
import org.apache.bookkeeper.client.LedgerEntry;
import org.apache.bookkeeper.client.AsyncCallback.AddCallback;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event;

import java.util.Enumeration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchReadThroughputLatencyRefactoredCopy {
    static Logger LOG = LoggerFactory.getLogger(BenchReadThroughputLatency.class);

    private static final Pattern LEDGER_PATTERN = Pattern.compile("L([0-9]+)$");

    private static final Comparator<String> ZK_LEDGER_COMPARE = new Comparator<String>() {
        public int compare(String o1, String o2) {
            try {
                Matcher m1 = LEDGER_PATTERN.matcher(o1);
                Matcher m2 = LEDGER_PATTERN.matcher(o2);
                if (m1.find() && m2.find()) {
                    return Integer.valueOf(m1.group(1))
                            - Integer.valueOf(m2.group(1));
                } else {
                    return o1.compareTo(o2);
                }
            } catch (Throwable t) {
                return o1.compareTo(o2);
            }
        }
    };

    private static void readLedger(ClientConfiguration conf, long ledgerId, byte[] passwd) {
        LOG.info("Reading ledger {}", ledgerId);
        BookKeeper bk = null;
        long time = 0;
        long entriesRead = 0;
        long lastRead = 0;
        int nochange = 0;

        long absoluteLimit = 5000000;
        LedgerHandle lh = null;
        try {
            bk = new BookKeeper(conf);
            while (true) {
                lh = bk.openLedgerNoRecovery(ledgerId, BookKeeper.DigestType.CRC32,
                        passwd);
                long lastConfirmed = Math.min(lh.getLastAddConfirmed(), absoluteLimit);
                if (lastConfirmed == lastRead) {
                    nochange++;
                    if (nochange == 10) {
                        break;
                    } else {
                        Thread.sleep(1000);
                        continue;
                    }
                } else {
                    nochange = 0;
                }
                long starttime = System.nanoTime();

                while (lastRead < lastConfirmed) {
                    long nextLimit = lastRead + 100000;
                    long readTo = Math.min(nextLimit, lastConfirmed);
                    Enumeration<LedgerEntry> entries = lh.readEntries(lastRead+1, readTo);
                    lastRead = readTo;
                    while (entries.hasMoreElements()) {
                        LedgerEntry e = entries.nextElement();
                        entriesRead++;
                        if ((entriesRead % 10000) == 0) {
                            LOG.info("{} entries read", entriesRead);
                        }
                    }
                }
                long endtime = System.nanoTime();
                time += endtime - starttime;

                lh.close();
                lh = null;
                Thread.sleep(1000);
            }
        } catch (InterruptedException ie) {
            // ignore
        } catch (Exception e ) {
            LOG.error("Exception in reader", e);
        } finally {
            LOG.info("Read {} in {}ms", entriesRead, time/1000/1000);

            try {
                if (lh != null) {
                    lh.close();
                }
                if (bk != null) {
                    bk.close();
                }
            } catch (Exception e) {
                LOG.error("Exception closing stuff", e);
            }
        }
    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("BenchReadThroughputLatency <options>", options);
    }

    /**
     * Metodo principale orchestratore.
     * Riduce lo Statement Count delegando parsing e inizializzazione.
     */
    public static void main(String[] args) throws Exception {
        Options options = getOptions();
        try {
            CommandLine cmd = new PosixParser().parse(options, args);
            if (cmd.hasOption("help")) {
                usage(options);
                return;
            }
            validateArgs(cmd, options);
            startBenchmark(cmd);
        } catch (ParseException e) {
            LOG.error("Errore nel parsing: " + e.getMessage());
            usage(options);
        }
    }

    private static void startBenchmark(CommandLine cmd) throws Exception {
        final String servers = cmd.getOptionValue("zookeeper", "localhost:2181");
        final byte[] passwd = cmd.getOptionValue("password", "benchPasswd").getBytes();
        final int sockTimeout = Integer.parseInt(cmd.getOptionValue("sockettimeout", "5"));

        final AtomicInteger ledger = new AtomicInteger(0);
        final AtomicInteger numLedgers = new AtomicInteger(0);
        setLedgerParams(cmd, ledger, numLedgers);

        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        final CountDownLatch connectedLatch = new CountDownLatch(1);
        final String nodepath = String.format("/ledgers/L%010d", ledger.get());

        final ClientConfiguration conf = new ClientConfiguration();
        conf.setReadTimeout(sockTimeout).setZkServers(servers);

        // Uso di Watcher anonimo invece della Lambda per connessione
        final ZooKeeper zk = new ZooKeeper(servers, 3000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    connectedLatch.countDown();
                }
            }
        });

        try {
            // Registriamo il watcher delegando al metodo statico
            zk.register(new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    handleZkEvent(event, zk, conf, ledger, numLedgers, passwd, nodepath, connectedLatch, shutdownLatch);
                }
            });

            connectedLatch.await();
            initializeZkState(zk, ledger, nodepath, conf, passwd, shutdownLatch);
            shutdownLatch.await();
        } finally {
            zk.close();
        }
    }

    // Visibilità package-private per evitare AccessorMethodGeneration
    static void handleZkEvent(WatchedEvent event, ZooKeeper zk, ClientConfiguration conf,
                              AtomicInteger ledger, AtomicInteger numLedgers, byte[] passwd,
                              String nodepath, CountDownLatch connectedLatch, CountDownLatch shutdownLatch) {
        // Rimosso catch generico per evitare AvoidCatchingGenericException
        if (event.getState() == Event.KeeperState.SyncConnected && event.getType() == Event.EventType.None) {
            connectedLatch.countDown();
        } else if (event.getType() == Event.EventType.NodeCreated && event.getPath().equals(nodepath)) {
            readLedger(conf, ledger.get(), passwd);
            shutdownLatch.countDown();
        } else if (event.getType() == Event.EventType.NodeChildrenChanged) {
            try {
                handleChildrenChanged(zk, conf, numLedgers, passwd, shutdownLatch);
            } catch (Exception e) { // Qui il catch è più giustificato ma meglio se specifico
                LOG.error("Errore nel recupero figli: {}", e.getMessage());
            }
        }
    }

    static void handleChildrenChanged(ZooKeeper zk, ClientConfiguration conf, AtomicInteger numLedgers,
                                      final byte[] passwd, final CountDownLatch shutdownLatch) throws KeeperException, InterruptedException {
        if (numLedgers.get() < 0) return;

        List<String> children = zk.getChildren("/ledgers", true);
        Long latestId = findLatestLedgerId(children); // Estrazione logica (Decomposizione)

        if (latestId != null) {
            final long ledgerId = latestId;
            final ClientConfiguration fConf = conf;
            if (numLedgers.decrementAndGet() <= 0) shutdownLatch.countDown();

            new Thread(new Runnable() {
                @Override public void run() { readLedger(fConf, ledgerId, passwd); }
            }).start();
        }
    }

    // Nuovo metodo per abbassare la complessità di handleChildrenChanged
    private static Long findLatestLedgerId(List<String> children) {
        List<String> ledgers = new ArrayList<String>();
        for (String child : children) {
            if (LEDGER_PATTERN.matcher(child).find()) ledgers.add(child);
        }
        if (ledgers.isEmpty()) return null;
        Collections.sort(ledgers, ZK_LEDGER_COMPARE);
        Matcher m = LEDGER_PATTERN.matcher(ledgers.get(ledgers.size() - 1));
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private static void initializeZkState(ZooKeeper zk, AtomicInteger ledger, String nodepath,
                                          ClientConfiguration conf, byte[] passwd, CountDownLatch shutdownLatch) throws Exception {
        if (ledger.get() != 0) {
            if (zk.exists(nodepath, true) != null) {
                readLedger(conf, ledger.get(), passwd);
                shutdownLatch.countDown();
            } else {
                LOG.info("In attesa della creazione di {}", nodepath);
            }
        } else {
            zk.getChildren("/ledgers", true);
        }
    }

    private static void setLedgerParams(CommandLine cmd, AtomicInteger ledger, AtomicInteger numLedgers) {
        if (cmd.hasOption("ledger")) {
            ledger.set(Integer.parseInt(cmd.getOptionValue("ledger")));
        } else {
            numLedgers.set(Integer.parseInt(cmd.getOptionValue("listen")));
        }
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("ledger", true, "Ledger to read...");
        options.addOption("listen", true, "Listen for creation...");
        options.addOption("password", true, "Password...");
        options.addOption("zookeeper", true, "Zookeeper...");
        options.addOption("sockettimeout", true, "Socket timeout...");
        options.addOption("help", false, "Help message");
        return options;
    }

    private static void validateArgs(CommandLine cmd, Options options) {
        if (cmd.hasOption("ledger") && cmd.hasOption("listen")) {
            LOG.error("Non puoi usare -ledger e -listen contemporaneamente");
            usage(options);
            System.exit(-1);
        }
        if (!cmd.hasOption("ledger") && !cmd.hasOption("listen")) {
            LOG.error("Devi specificare -ledger o -listen");
            usage(options);
            System.exit(-1);
        }
    }
}
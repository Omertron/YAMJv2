/*
 *      Copyright (c) 2004-2012 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *
 *      For any reuse or distribution, you must make clear to others the
 *      license terms of this work.
 */
package com.moviejukebox.tools;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;

public class ThreadExecutor<T> implements ThreadFactory {

    private Collection<Future<T>> values = new ArrayList<Future<T>>(100);
    private ThreadPoolExecutor pool = null;
    private BlockingQueue<Runnable> queue = null;
    private int threadsRun, threadsIo, threadsTotal;
    private boolean ignoreErrors = true;
    private static final Logger logger = Logger.getLogger(ThreadExecutor.class);
    private Semaphore runningThreads, ioThreads;
    private static final Map<String, String> hostgrp = new HashMap<String, String>();
    private static final Map<String, Semaphore> grouplimits = new HashMap<String, Semaphore>();

    /**
     * Handle IO slots allocation to avoid throttling / ban on source sites Find the proper semaphore for each host: -
     * Map each unique host to a group (hostgrp) - Max each group (rule) to a semaphore
     *
     * @author Gabriel Corneanu
     */
    static {
        // First we have to read/create the rules
        // Default, can be overridden
        grouplimits.put(".*", new Semaphore(1));
        String limitsProperty = PropertiesUtil.getProperty("mjb.MaxDownloadSlots", ".*=1");
        logger.debug("Using download limits: " + limitsProperty);

        Pattern semaphorePattern = Pattern.compile(",?\\s*([^=]+)=(\\d+)");
        Matcher semaphoreMatcher = semaphorePattern.matcher(limitsProperty);
        while (semaphoreMatcher.find()) {
            String group = semaphoreMatcher.group(1);
            try {
                Pattern.compile(group);
                logger.debug(group + "=" + semaphoreMatcher.group(2));
                grouplimits.put(group, new Semaphore(Integer.parseInt(semaphoreMatcher.group(2))));
            } catch (Exception error) {
                logger.debug("Rule \"" + group + "\" is not valid regexp, ignored");
            }
        }
    }

    /**
     * Helper class Encapsulates a fixed thread pool ExecutorService Saves futures, used just to catch inner exceptions
     * Usage patter: - create with thread count and io slots - submit tasks (Callable) - call waitFor; this logs
     *
     * - in addition processing threads should call pairs EnterIO, LeaveIO to switch from running to io state
     *
     * @author Gabriel Corneanu
     */
    public ThreadExecutor(int threadsRun, int threadsIo) {
        this.threadsRun = threadsRun;
        this.threadsIo = threadsIo <= 0 ? threadsRun : threadsIo;
        threadsTotal = this.threadsRun + this.threadsIo;
        restart();
    }

    /*
     * Thread descendant class used for our execution scheduling
     */
    private static final class ScheduledThread extends Thread {

        private Semaphore sRun, sIo, sIotarget;
        private Stack<String> hosts = new Stack<String>();

        private ScheduledThread(Runnable r, Semaphore sRun, Semaphore sIo) {
            super(r);
            this.sRun = sRun;
            this.sIo = sIo;
        }

        @Override
        public void run() {
            sRun.acquireUninterruptibly();
            try {
                super.run();
            } finally {
                sRun.release();
            }
        }

        private void enterIO(URL url) {
            String host = url.getHost().toLowerCase();

            if (!hosts.empty()) {
                //going to the same host is ok
                if (!host.equals(hosts.peek())) {
                    logger.debug("ThreadExecutor: Nested EnterIO(" + host + "); previous(" + hosts.peek() + "); ignored");
                }
                hosts.push(host);
                return;
            }
            String semaphoreGroup;
            synchronized (hostgrp) {
                semaphoreGroup = hostgrp.get(host);
                // first time not found, search for matching group
                if (semaphoreGroup == null) {
                    semaphoreGroup = ".*";
                    for (String searchGroup : grouplimits.keySet()) {
                        if (host.matches(searchGroup)) {
                            if (searchGroup.length() > semaphoreGroup.length()) {
                                semaphoreGroup = searchGroup;
                            }
                        }
                    }
                    logger.debug(String.format("IO download host: %s; rule: %s", host, semaphoreGroup));
                    hostgrp.put(host, semaphoreGroup);
                }
            }

            // there should be NO way to fail
            //String dbgstr = "host="+host+"; thread="+getName();
            //logger.finest("ThreadExecutor: Try EnterIO: "+dbgstr);
            Semaphore s = grouplimits.get(semaphoreGroup);
            sIotarget = s;
            sRun.release(); // exit running state; another thread might be released;
            sIotarget.acquireUninterruptibly(); // aquire URL target semaphore
            hosts.push(host);
            sIo.acquireUninterruptibly(); // enter io state
            //logger.finest("ThreadExecutor: EnterIO done: "+dbgstr);
            //ready to go...
        }

        private void leaveIO() {
            if (hosts.empty()) {
                logger.info(SystemTools.getStackTrace(new Throwable("ThreadExecutor: Unbalanced LeaveIO call.")));
                return;
            }
            String host = hosts.pop();
            if (!hosts.empty()) {
                if (!host.equals(hosts.peek())) {
                    logger.debug("Nested LeaveIO(" + host + "); previous(" + hosts.peek() + "); ignored");
                }
                return;
            }

            //String dbgstr = "host="+host+"; thread="+getName();
            sIotarget.release();
            sIo.release();
            sIotarget = null;
            //logger.finest("ThreadExecutor: Try LeaveIO: "+dbgstr);
            sRun.acquireUninterruptibly(); //back to running state
            //logger.finest("ThreadExecutor: LeaveIO done: "+dbgstr);
        }
    }

    public static void enterIO(URL url) {
        if (!(Thread.currentThread() instanceof ScheduledThread)) {
            // logger.info(getStackTrace(new Throwable("ThreadExecutor: Unmanaged thread call to EnterIO; ignored.")));
            // If this isn't a managed thread, then just exit.
            return;
        }
        ((ScheduledThread) Thread.currentThread()).enterIO(url);
    }

    public static void enterIO(String url) {
        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            try {
                u = new URL("http://" + url);
            } catch (MalformedURLException e1) {
                logger.info("ThreadExecutor: Invalid call to EnterIO.");
                logger.info(SystemTools.getStackTrace(e1));
                return;
            }
        }
        enterIO(u);
    }

    public static void leaveIO() {
        if (!(Thread.currentThread() instanceof ScheduledThread)) {
            //logger.info(getStackTrace(new Throwable("ThreadExecutor: Unmanaged thread call to LeaveIO; ignored.")));
            // If this isn't a managed thread, then just exit.
            return;
        }
        ((ScheduledThread) Thread.currentThread()).leaveIO();
    }

    @Override
    public Thread newThread(Runnable r) {
        return new ScheduledThread(r, runningThreads, ioThreads);
    }

    public final void restart() {
        values.clear();
        runningThreads = new Semaphore(threadsRun);
        ioThreads = new Semaphore(threadsIo);

        //refined: use a fixed queue with some extra space; in relation with submit
        //the size is just an approximation; it has no real connection to thread count
        //make it reasonable sized to avoid waiting in submit
        queue = new ArrayBlockingQueue<Runnable>(100);
//        queue = new LinkedBlockingQueue<Runnable>();
        //allow more threads, they are managed by semaphores
        pool = new ThreadPoolExecutor(threadsRun, 2 * threadsTotal,
                100, TimeUnit.MILLISECONDS,
                queue,
                this);
    }

    public void submit(Callable<T> c) throws InterruptedException {
        //never queue too many objects; wait for some space to limit resource allocations
        //in case of fixed size queues, tasks could even be rejected
        //therefore wait here a very short time
        while (queue.remainingCapacity() <= 0) {
            Thread.sleep(5);
        }
        values.add(pool.submit(c));
    }

    public void submit(Runnable r) throws InterruptedException {
        T result = null;
        submit(Executors.callable(r, result));
    }

    public ArrayList<T> waitForValues() throws Throwable {
        pool.shutdown();
        ArrayList<T> v = new ArrayList<T>(values.size());
        for (Future<T> f : values) {
            try {
                v.add(f.get());
            } catch (ExecutionException e) {
                if (ignoreErrors) {
                    logger.info(SystemTools.getStackTrace(e.getCause()));
                } else {
                    throw e.getCause();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pool.shutdownNow();
        return v;
    }

    public void waitFor() throws Throwable {
        waitForValues();
        int dif = threadsIo - ioThreads.availablePermits();
        if (dif != 0) {
            logger.error("ThreadExecutor: Unfinished downloading threads detected: " + dif);
        }
    }
}

/*
 * roomstore - an irc journaller using cassandra.
 *
 * Copyright 2011-2016 MeBigFatGuy.com
 * Copyright 2011-2016 Dave Brosius
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

import java.util.ArrayDeque;
import java.util.Deque;

import org.jibble.pircbot.PircBot;


public final class MessagePoster implements Runnable {

    private PircBot bot;
    private Thread thread;
    private Deque<Pair<String, String>> queue;
    
    public MessagePoster() {
        queue = new ArrayDeque<Pair<String, String>>();
    }
    
    public void startPosting(PircBot b) {
        bot = b;
        if (thread == null) {
            thread = new Thread(this);
            thread.setName("MessagePoster");
            thread.start();
        }
    }
    
    public void post(String sender, String message) {
        
        Pair<String, String> pair = new Pair<String, String>(sender, message);
        synchronized(queue) {
            queue.add(pair);
            queue.notifyAll();
        }
    }
    
    public void stopPosting() {
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException ie) {   
            } finally {
                thread = null;
            }
        }
    }
    
    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                Pair<String, String> pair;
                synchronized(queue) {
                    while (queue.isEmpty()) {
                        queue.wait();
                    }
                    pair = queue.removeFirst();
                }
                bot.sendMessage(pair.getKey(), pair.getValue());
            }
        } catch (InterruptedException ie) {      
        }
    }

}

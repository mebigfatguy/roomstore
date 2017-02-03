/*
 * roomstore - an irc journaller using cassandra.
 *
 * Copyright 2011-2017 MeBigFatGuy.com
 * Copyright 2011-2017 Dave Brosius
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

import com.datastax.driver.core.LocalDate;

public class Message implements Comparable<Message> {

    private final String channel;
    private final String sender;
    private final LocalDate time;
    private final String message;

    public Message(String msgChannel, String msgSender, LocalDate msgTime, String msgMessage) {
        channel = msgChannel;
        sender = msgSender;
        time = msgTime;
        message = msgMessage;
    }

    public String getChannel() {
        return channel;
    }

    public String getSender() {
        return sender;
    }

    public LocalDate getTime() {
        return time;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        return channel.hashCode() ^ sender.hashCode() ^ time.hashCode() ^ message.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Message)) {
            return false;
        }

        Message that = (Message) o;

        return channel.equals(that.channel) && sender.equals(that.sender) && time.equals(that.time) && message.equals(that.message);
    }

    @Override
    public int compareTo(Message that) {
        int cmp = channel.compareTo(that.channel);
        if (cmp != 0) {
            return cmp;
        }

        cmp = time.getDaysSinceEpoch() - that.time.getDaysSinceEpoch();
        if (cmp != 0) {
            return cmp;
        }

        cmp = sender.compareTo(that.sender);
        if (cmp != 0) {
            return cmp;
        }

        return message.compareTo(that.message);
    }
}

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

import java.util.Date;

public class Message {

    private String channel;
    private String sender;
    private Date time;
    private String message;

    public Message(String msgChannel, String msgSender, Date msgTime, String msgMessage) {
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

    public Date getTime() {
        return time;
    }

    public String getMessage() {
        return message;
    }
}

/*
 * roomstore - an irc journaller using cassandra.
 *
 * Copyright 2011-2019 MeBigFatGuy.com
 * Copyright 2011-2019 Dave Brosius
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

import java.io.IOException;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IRCConnector {

    static final Logger LOGGER = LoggerFactory.getLogger(IRCConnector.class);
    CassandraWriter writer;
    CasBot casBot;
    MessagePoster poster;
    String server;
    String[] channels;

    public IRCConnector(String nickName, String ircServer, String[] ircChannels) {
        poster = new MessagePoster();
        casBot = new CasBot(this, poster, nickName);
        poster.startPosting(casBot);
        server = ircServer;
        channels = ircChannels;
    }

    public void setWriter(CassandraWriter cassandraWriter) {
        writer = cassandraWriter;
    }

    public void startRecording() throws IrcException, IOException {
        boolean started = false;
        while (!started) {
            try {
                casBot.connect(server);
                for (String channel : channels) {
                    if (!channel.startsWith("#")) {
                        channel = '#' + channel;
                    }
                    casBot.joinChannel(channel);
                    started = true;
                }
            } catch (NickAlreadyInUseException naiue) {
                String name = casBot.getName();
                name += String.valueOf((int) (Math.random() * 10));
                casBot.rename(name);
            }
        }
    }
    
    public void stopRecording() {
        poster.stopPosting();
        casBot.disconnect();
    }
}

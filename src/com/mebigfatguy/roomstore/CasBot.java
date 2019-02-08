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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jibble.pircbot.PircBot;

class CasBot extends PircBot {
    private final IRCConnector ircConnector;
    private final MessagePoster messagePoster;

    public CasBot(IRCConnector con, MessagePoster poster, String nick) {
        ircConnector = con;
        messagePoster = poster;
        setName(nick);
        setLogin(nick);
        setFinger(nick);
        setVersion(nick);
    }

    public void rename(String nick) {
        super.setName(nick);
    }

    @Override
    public void onMessage(String channel, String sender, String login, String hostname, String message) {
        try {
            String[] msgParts = message.split("\\s+");
            if (msgParts.length >= 2) {
                if ("~".equals(msgParts[0])) {
                    if ("help".equalsIgnoreCase(msgParts[1])) {
                        StringBuilder response = new StringBuilder();
                        response.append("roomstore - https://github.com/mebigfatguy/roomstore\n");
                        response.append("~ help                  -- this message\n");
                        response.append("~ seen user             -- show last time user said something if available\n");
                        response.append("~ today                 -- see messages from today\n");
                        response.append("~ date MM/yy/dddd       -- see messages from date\n");
                        response.append("~ topic {word} ...      -- see messages that talk about the words specified\n");
                        messagePoster.post(sender, response.toString());
                    } else if ((msgParts.length >= 3) && "seen".equalsIgnoreCase(msgParts[1])) {
                        String user = msgParts[2].trim();
                        Message msg = ircConnector.writer.getLastMessage(channel, user);
                        if (msg != null) {
                            messagePoster.post(sender, user + " last seen " + DateFormat.getInstance().format(msg.getTime()) + " saying: " + msg.getMessage());
                        }
                    } else if ((msgParts.length >= 3) && "topic".equalsIgnoreCase(msgParts[1])) {
                        String word = msgParts[2].trim().toLowerCase();
                        Set<Message> intersectionMessages = new TreeSet<>(ircConnector.writer.getTopicMessages(channel, word));

                        for (int i = 3; i < msgParts.length; ++i) {
                            word = msgParts[i].trim().toLowerCase();
                            List<Message> messages = ircConnector.writer.getTopicMessages(channel, word);
                            intersectionMessages.retainAll(messages);
                        }
                        for (Message msg : intersectionMessages) {
                            messagePoster.post(msg.getSender(),
                                    msg.getSender() + " @ " + DateFormat.getInstance().format(msg.getTime()) + " said: " + msg.getMessage());
                        }
                    } else if ("today".equalsIgnoreCase(msgParts[1])) {
                        Calendar dayCal = Calendar.getInstance();
                        dayCal.set(Calendar.HOUR_OF_DAY, 0);
                        dayCal.set(Calendar.MINUTE, 0);
                        dayCal.set(Calendar.SECOND, 0);
                        dayCal.set(Calendar.MILLISECOND, 0);

                        List<Message> msgs = ircConnector.writer.getMessages(channel, dayCal.getTime());
                        sendMessageList(sender, msgs);
                    } else if ((msgParts.length >= 3) && "date".equalsIgnoreCase(msgParts[1])) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
                        Date day = sdf.parse(msgParts[2]);

                        List<Message> msgs = ircConnector.writer.getMessages(channel, day);
                        sendMessageList(sender, msgs);
                    }
                    return;
                }
            }
            ircConnector.writer.addMessage(channel, sender, message);

        } catch (Exception e) {
            IRCConnector.LOGGER.error("Failed processing message on channel {} for user {} - {}", channel, sender, message);
        }
    }

    public void sendMessageList(String sender, List<Message> msgs) {
        for (Message m : msgs) {
            messagePoster.post(sender, m.getSender() + ": " + DateFormat.getInstance().format(m.getTime()) + ": " + m.getMessage() + "\n");
        }
    }

    @Override
    protected void onDisconnect() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                long sleepTime = 2000;
                while (!Thread.interrupted()) {
                    try {
                        Thread.sleep(sleepTime);
                        sleepTime *= 1.5;
                        if (sleepTime > 60000) {
                            sleepTime = 60000;
                        }

                        CasBot.this.ircConnector.casBot.connect(CasBot.this.ircConnector.server);
                        for (String channel : CasBot.this.ircConnector.channels) {
                            if (!channel.startsWith("#")) {
                                channel = '#' + channel;
                            }
                            CasBot.this.ircConnector.casBot.joinChannel(channel);
                        }
                        return;
                    } catch (Exception e) {
                    }
                }
            }
        });
        t.start();
    }

}
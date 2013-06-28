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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.AlreadyExistsException;

public class CassandraWriter {

    private static final String TOTAL_COUNTER = ":TOTAL:";
    
    private Session session;
    private PreparedStatement addMessagePS;
    private PreparedStatement setLastAccessPS;
    private PreparedStatement addTopicPS;
    private PreparedStatement getLastAccessPS;
    private PreparedStatement getMessagePS;
    private PreparedStatement getMessagesOnDatePS;
    private PreparedStatement getTopicEntriesPS;
    private PreparedStatement getSpecificMessagePS;
    private PreparedStatement incrementCounterPS;

    public CassandraWriter(Session s, int replicationFactor) throws Exception {
        session = s;
        setUpSchema(replicationFactor);
        setUpStatements();
    }

    public void addMessage(String channel, String sender, String hostname, String message) throws Exception {

        Calendar dayCal = Calendar.getInstance();
        Date dateTime = dayCal.getTime();
        
        dayCal.set(Calendar.HOUR_OF_DAY, 0);
        dayCal.set(Calendar.MINUTE, 0);
        dayCal.set(Calendar.SECOND, 0);
        dayCal.set(Calendar.MILLISECOND, 0);
        Date day = dayCal.getTime();
        
        session.execute(addMessagePS.bind(day, channel, dateTime, sender, message));
        session.execute(setLastAccessPS.bind(sender, channel, day, dateTime));
        
        long total = 0;
        for (String word : message.split("\\s+|\\.|\\,|\\?|\\:")) {
            if (word.length() > 0) {
                word = word.toLowerCase();
                session.execute(addTopicPS.bind(channel, word, dateTime, sender));
                ++total;
                session.execute(incrementCounterPS.bind(1L, word));
            }
        }
        
        if (total > 0) 
            session.execute(incrementCounterPS.bind(total, TOTAL_COUNTER));
    }

    public Message getLastMessage(String channel, String sender) throws Exception {

        ResultSet rs = session.execute(getLastAccessPS.bind(sender, channel));
        
        if (!rs.isExhausted()) {
            Row row = rs.one();
            Date day = row.getDate("last_seen_day");
            Date dateTime = row.getDate("last_seen_date_time");
            
            rs = session.execute(getMessagePS.bind(day, channel, dateTime, sender));
            if (!rs.isExhausted()) {
                row = rs.one();
                return new Message(channel, sender, dateTime, row.getString("message"));
            }
        }
        
        return null;
    }
    
    public List<Message> getMessages(String channel, Date day) {
        
        List<Message> messages = new ArrayList<Message>();
        ResultSet rs = session.execute(getMessagesOnDatePS.bind(day, channel));
        for (Row row : rs) {
            Message m = new Message(channel, row.getString("user"), row.getDate("date_time"), row.getString("message"));
            messages.add(0, m);
        }
        
        return messages; 
    }
    
    public Message getSpecificMessage(Date day, String channel, Date date_time) {
        ResultSet rs = session.execute(getSpecificMessagePS.bind(day, channel, date_time));
        if (!rs.isExhausted()) {
            Row row = rs.one();
            return new Message(channel, row.getString("user"), date_time, row.getString("message"));        
        }
        
        return null;
    }
    
    public List<Message> getTopicMessages(String channel, String word) {
        Calendar cal = Calendar.getInstance();
        List<Message> messages = new ArrayList<Message>();
        ResultSet rs = session.execute(getTopicEntriesPS.bind(channel, word));
        for (Row row : rs) {
            Date date_time = row.getDate("date_time");
            
            cal.setTime(date_time);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            Date day = cal.getTime();
            
            Message m = getSpecificMessage(day, channel, date_time);
            if (m != null) {
                messages.add(m);
            }
        }
        
        return messages;
    }

    private void setUpSchema(int replicationFactor) throws Exception {
        
        try {
            session.execute(String.format("CREATE KEYSPACE roomstore WITH replication = { 'class' : 'SimpleStrategy', 'replication_factor' : %d }", replicationFactor));
        } catch (AlreadyExistsException aee) {
        } finally {
            session.execute("use roomstore");
        }

        try {
            session.execute("CREATE TABLE roomstore.messages (day timestamp, channel text, date_time timestamp, user text, message text, primary key(day, channel, date_time, user)) with compact storage and clustering order by (channel desc, date_time desc)");
        } catch (AlreadyExistsException aee) {
        }
        
        try {
            session.execute("CREATE TABLE roomstore.users (user text, channel text, last_seen_day timestamp, last_seen_date_time timestamp, primary key(user, channel))");
        } catch (AlreadyExistsException aee) {
        }
        
        try {
            session.execute("CREATE TABLE roomstore.topics (channel text, word text, date_time timestamp, user text, primary key(channel, word, date_time)) with compact storage and clustering order by (word asc, date_time desc)");
        } catch (AlreadyExistsException aee) {
        }
        
        try {
            session.execute("CREATE TABLE roomstore.topic_counters (word text primary key, count counter)");
        } catch (AlreadyExistsException aee) {
        }
    }
    
    private void setUpStatements() throws Exception {
        
        addMessagePS = session.prepare("insert into roomstore.messages (day, channel, date_time, user, message) values (?,?,?,?,?)");
        setLastAccessPS = session.prepare("insert into roomstore.users (user, channel, last_seen_day, last_seen_date_time) values (?,?,?,?)");
        addTopicPS = session.prepare("insert into roomstore.topics (channel, word, date_time, user) values (?, ?, ?, ?)");
        getLastAccessPS = session.prepare("select last_seen_day, last_seen_date_time from roomstore.users where user = ? and channel = ?");
        getMessagePS = session.prepare("select message from roomstore.messages where day = ? and channel = ? and date_time = ? and user = ?");
        getMessagesOnDatePS = session.prepare("select user, date_time, message from roomstore.messages where day = ? and channel = ?");
        getTopicEntriesPS = session.prepare("select date_time, user from roomstore.topics where channel = ? and word = ?");
        getSpecificMessagePS = session.prepare("select message, user from roomstore.messages where day = ? and channel = ? and date_time = ?");
        incrementCounterPS = session.prepare("update roomstore.topic_counters set count = count + ? where word = ?");
    }
}

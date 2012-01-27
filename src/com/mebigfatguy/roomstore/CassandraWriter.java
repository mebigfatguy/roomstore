/*
 * roomstore - an irc journaller using cassandra.
 * 
 * Copyright 2011 MeBigFatGuy.com
 * Copyright 2011 Dave Brosius
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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnDef;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.IndexType;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.KsDef;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.cassandra.utils.UUIDGen;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class CassandraWriter {

    private static final String KEY_SPACE_NAME = "roomstore";
    private static final String COLUMN_FAMILY_NAME = "messages";
    private static final String STRATEGY_NAME = "SimpleStrategy";
    private static final ByteBuffer CHANNEL_BUFFER = toByteBuffer("channel");
    private static final ByteBuffer SENDER_BUFFER = toByteBuffer("sender");
    private static final ByteBuffer HOST_BUFFER = toByteBuffer("host");
    private static final ByteBuffer MESSAGE_BUFFER = toByteBuffer("message");

    
    private TTransport transport;
    private Cassandra.Client client; 
    
    public CassandraWriter(String cassandraServer) throws TTransportException, TException, InvalidRequestException, SchemaDisagreementException {
        setupClient();
        setupKeyspace();
    }

    public void addMessage(String channel, String sender, String hostname, String message) throws TException, UnknownHostException, InvalidRequestException, TimedOutException, UnavailableException {
        
        client.set_keyspace(KEY_SPACE_NAME);

        long timestamp = System.currentTimeMillis();

        UUID uuid = UUIDGen.makeType1UUIDFromHost(InetAddress.getLocalHost());
        ByteBuffer uuidBuffer = ByteBuffer.wrap(UUIDGen.decompose(uuid));    
        
        Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap = new HashMap<ByteBuffer, Map<String, List<Mutation>>>();
        
        Column channelColumn = new Column(CHANNEL_BUFFER);
        channelColumn.setValue(toByteBuffer(channel));
        channelColumn.setTimestamp(timestamp);
        addToMutationMap(mutationMap, uuidBuffer, COLUMN_FAMILY_NAME, channelColumn);
        
        Column senderColumn = new Column(SENDER_BUFFER);
        senderColumn.setValue(toByteBuffer(sender));
        senderColumn.setTimestamp(timestamp);
        addToMutationMap(mutationMap, uuidBuffer, COLUMN_FAMILY_NAME, senderColumn);
        
        Column hostColumn = new Column(HOST_BUFFER);
        hostColumn.setValue(toByteBuffer(hostname));
        hostColumn.setTimestamp(timestamp);
        addToMutationMap(mutationMap, uuidBuffer, COLUMN_FAMILY_NAME, hostColumn);
        
        Column messageColumn = new Column(MESSAGE_BUFFER);
        messageColumn.setValue(toByteBuffer(message));
        messageColumn.setTimestamp(timestamp);
        addToMutationMap(mutationMap, uuidBuffer, COLUMN_FAMILY_NAME, messageColumn);
        
        client.batch_mutate(mutationMap, ConsistencyLevel.ONE);
    }
    
    private static void addToMutationMap(Map<ByteBuffer,Map<String,List<Mutation>>> mutationMap, ByteBuffer key, String cf, Column c)
    {
        Map<String, List<Mutation>> cfMutation = mutationMap.get(key);
        if (cfMutation == null) {
            cfMutation = new HashMap<String, List<Mutation>>();
            mutationMap.put(key, cfMutation);
        }
        
        List<Mutation> mutationList = cfMutation.get(cf);
        if (mutationList == null) {
            mutationList = new ArrayList<Mutation>();
            cfMutation.put(cf,  mutationList);
        }
        
        ColumnOrSuperColumn cc = new ColumnOrSuperColumn();
        Mutation m = new Mutation();

        cc.setColumn(c);
        m.setColumn_or_supercolumn(cc);
        mutationList.add(m);
    }
    
    private void setupClient() throws TTransportException {
        transport = new TFramedTransport(new TSocket("localhost", 9160));
        TProtocol proto = new TBinaryProtocol(transport);
        client = new Cassandra.Client(proto);
        transport.open();
    }
    
    private void setupKeyspace() throws TException, InvalidRequestException, SchemaDisagreementException {
        try {
            client.describe_keyspace(KEY_SPACE_NAME);
        } catch (NotFoundException nfe) {
            List<CfDef> columnDefs = new ArrayList<CfDef>();
            CfDef columnFamily = new CfDef(KEY_SPACE_NAME, COLUMN_FAMILY_NAME);

            List<ColumnDef> columnMetaData = new ArrayList<ColumnDef>();
            ColumnDef channelDef = new ColumnDef(CHANNEL_BUFFER, "UTF8Type");
            channelDef.setIndex_type(IndexType.KEYS);
            columnMetaData.add(channelDef);
            ColumnDef senderDef = new ColumnDef(SENDER_BUFFER, "UTF8Type");
            senderDef.setIndex_type(IndexType.KEYS);
            columnMetaData.add(senderDef);
            ColumnDef hostDef = new ColumnDef(HOST_BUFFER, "UTF8Type");
            columnMetaData.add(hostDef);
            ColumnDef messageDef = new ColumnDef(MESSAGE_BUFFER, "UTF8Type");
            columnMetaData.add(messageDef);
            columnFamily.setColumn_metadata(columnMetaData);
            columnDefs.add(columnFamily);
            KsDef ksdef = new KsDef(KEY_SPACE_NAME, STRATEGY_NAME, columnDefs);
            Map<String, String> options = new HashMap<String, String>();
            options.put("replication_factor", "1");
            ksdef.setStrategy_options(options);
            client.system_add_keyspace(ksdef);
        }
    }
    
    private static ByteBuffer toByteBuffer(String value)
    {
        try {
            return ByteBuffer.wrap(value.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            throw new Error("UTF-8 not supported", uee);
        }
    }
}

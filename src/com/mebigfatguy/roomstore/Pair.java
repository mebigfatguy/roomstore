/*
 * roomstore - an irc journaller using cassandra.
 *
 * Copyright 2011-2018 MeBigFatGuy.com
 * Copyright 2011-2018 Dave Brosius
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

public class Pair<K, V> {
    
    private final K key;
    private final V value;
    
    public Pair(K k, V v) {
        key = k;
        value = v;
    }
    
    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }
    
    @Override
	public int hashCode() {
        return key.hashCode();
    }
    
    @Override
	public boolean equals(Object o) {
        if (!(o instanceof Pair)) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Pair<K, V> that = (Pair<K, V>) o;
        return key.equals(that.key);
    }
    
    @Override
	public String toString() {
        return "Pair[" + key + ", " + value + "]";
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.strimzi.controller.topic;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConfigTest {

    private static final Map<String, String> mandatory = new HashMap<>();

    static {
        mandatory.put(Config.ZOOKEEPER_CONNECT.key, "localhost:2181");
        mandatory.put(Config.KAFKA_BOOTSTRAP_SERVERS.key, "localhost:9092");
        mandatory.put(Config.NAMESPACE.key, "default");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownKey() {
        new Config(Collections.singletonMap("foo", "bar"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void empty() {
        Config c = new Config(Collections.emptyMap());
    }

    @Test
    public void defaults() {
        Map<String, String> map = new HashMap<>(mandatory);
        Config c = new Config(map);
        assertEquals(20_000, c.get(Config.ZOOKEEPER_SESSION_TIMEOUT_MS).intValue());
    }

    @Test
    public void override() {
        Map<String, String> map = new HashMap<>(mandatory);
        map.put(Config.ZOOKEEPER_SESSION_TIMEOUT_MS.key, "13 seconds");

        Config c = new Config(map);
        assertEquals(13_000, c.get(Config.ZOOKEEPER_SESSION_TIMEOUT_MS).intValue());
    }

    @Test
    public void intervals() {
        Map<String, String> map = new HashMap<>(mandatory);

        map.put(Config.ZOOKEEPER_SESSION_TIMEOUT_MS.key, "13 seconds");
        new Config(map);

        map.put(Config.ZOOKEEPER_SESSION_TIMEOUT_MS.key, "13seconds");
        new Config(map);

        try {
            map.put(Config.ZOOKEEPER_SESSION_TIMEOUT_MS.key, "13foos");
            new Config(map);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }
}

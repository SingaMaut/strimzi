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

package io.strimzi.controller.cluster.resources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.strimzi.controller.cluster.ResourceUtils;
import org.junit.Test;

import static io.strimzi.controller.cluster.ResourceUtils.labels;
import static org.junit.Assert.assertEquals;

public class KafkaClusterTest {

    private final String namespace = "test";
    private final String cluster = "foo";
    private final int replicas = 1;
    private final String image = "image";
    private final int healthDelay = 120;
    private final int healthTimeout = 30;
    private final ConfigMap cm = ResourceUtils.createConfigMap(namespace, cluster, replicas, image, healthDelay, healthTimeout);
    private final KafkaCluster kc = KafkaCluster.fromConfigMap(cm);

    @Test
    public void testGenerateService() {
        Service headful = kc.generateService();
        assertEquals("ClusterIP", headful.getSpec().getType());
        assertEquals(ResourceUtils.labels("strimzi.io/cluster", cluster, "strimzi.io/kind", "kafka-cluster", "strimzi.io/name", cluster + "-kafka"), headful.getSpec().getSelector());
        assertEquals(1, headful.getSpec().getPorts().size());
        assertEquals("clients", headful.getSpec().getPorts().get(0).getName());
        assertEquals(new Integer(9092), headful.getSpec().getPorts().get(0).getPort());
        assertEquals("TCP", headful.getSpec().getPorts().get(0).getProtocol());
    }

    @Test
    public void testGenerateHeadlessService() {
        Service headless = kc.generateHeadlessService();
        assertEquals(cluster+"-kafka-headless", headless.getMetadata().getName());
        assertEquals("ClusterIP", headless.getSpec().getType());
        assertEquals("None", headless.getSpec().getClusterIP());
        assertEquals(labels("strimzi.io/cluster", "foo", "strimzi.io/kind", "kafka-cluster", "strimzi.io/name", "foo-kafka"), headless.getSpec().getSelector());
        assertEquals(1, headless.getSpec().getPorts().size());
        assertEquals("clients", headless.getSpec().getPorts().get(0).getName());
        assertEquals(new Integer(9092), headless.getSpec().getPorts().get(0).getPort());
        assertEquals("TCP", headless.getSpec().getPorts().get(0).getProtocol());
    }

    @Test
    public void testGenerateStatefulSet() {
        // We expect a single statefulSet ...
        StatefulSet ss = kc.generateStatefulSet(true);
        assertEquals(cluster + "-kafka", ss.getMetadata().getName());
        // ... in the same namespace ...
        assertEquals(namespace, ss.getMetadata().getNamespace());
        // ... with these labels
        assertEquals(labels("strimzi.io/cluster", cluster,
                "strimzi.io/kind", "kafka-cluster",
                "strimzi.io/name", cluster + "-kafka"),
                ss.getMetadata().getLabels());

        assertEquals(new Integer(replicas), ss.getSpec().getReplicas());
        assertEquals(image, ss.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertEquals(new Integer(healthTimeout), ss.getSpec().getTemplate().getSpec().getContainers().get(0).getLivenessProbe().getTimeoutSeconds());
        assertEquals(new Integer(healthDelay), ss.getSpec().getTemplate().getSpec().getContainers().get(0).getLivenessProbe().getInitialDelaySeconds());
        assertEquals(new Integer(healthTimeout), ss.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().getTimeoutSeconds());
        assertEquals(new Integer(healthDelay), ss.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().getInitialDelaySeconds());
    }

}
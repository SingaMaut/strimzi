# Topic Controller

The role of the topic controller is to keep in-sync a set of K8s ConfigMaps describing Kafka topics, 
and those Kafka topics. 

Specifically:
 
* if a config map is created, the controller will create the topic it describes
* if a config map is deleted, the controller will delete the topic it describes
* if a config map is changed, the controller will update the topic it describes

And also, in the other direction:

* if a topic is created, the controller will create a config map describing it
* if a topic is deleted, the controller will create the config map describing it
* if a topic is changed, the controller will update the config map describing it

This is beneficial to a Kubernetes/OpenShift-centric style of deploying 
applications, because it allows you to declare a ConfigMap as part of your
applications deployment and the controller will take care of creating 
the topic for you, so your application just needs to deal with producing 
and/or consuming from the necessary topics.

Should the topic be reconfigured, reassigned to different Kafka nodes etc, 
the ConfigMap will always be up to date.


## Reconciliation

A fundamental problem that the controller has to solve is that there is no 
single source of truth: 
Both the ConfigMap and the topic can be modified independently of the controller. 
Complicating this, the topic controller might not always be able to observe
changes at each end in real time (the controller might be down etc).
 
To resolve this, the controller maintains its own private copy of the 
information about each topic. 
When a change happens either in the Kafka cluster, or 
in Kubernetes/OpenShift, it looks at both the state of the other system, and at its 
private copy in order to determine what needs to change to keep everything in sync.  
The same thing happens whenever the controller starts, and periodically while its running.

For example, suppose the topic controller is not running, and a ConfigMap "my-topic" gets created. 
When the controller starts it will lack a private copy of "my-topic", 
so it can infer that the ConfigMap has been created since it was last running. 
The controller will create the topic corresponding to "my-topic" and also store a private copy of the 
metadata for "my-topic".

The private copy allows the controller to cope with scenarios where the topic 
config gets changed both in Kafka and in Kubernetes/OpenShift, so long as the 
changes are not incompatible (e.g. both changing the same topic config key, but to 
different values). 
In the case of incompatible changes, the Kafka configuration wins, and the ConfigMap will 
be updated to reflect that. Defaulting to the Kafka configuration ensures that, 
in the worst case, data won't be lost. 

The private copy is held in the same ZooKeeper ensemble used by Kafka itself. 
This mitigates availability concerns, because if ZooKeeper is not running
then Kafka itself cannot run, so the controller will be no less available 
than it would even if it was stateless. 


## Usage Recommendations

1. Try to either always operate on ConfigMaps or always operate directly on topics.
2. When creating a ConfigMap:
    * Remember that you can't easily change the name later.
    * Choose a name for the ConfigMap that reflects the name of the topic it describes.
    * Ideally the ConfigMap's `metadata.name` should be the same as its `data.name`.
      To do this, the topic name will have to be a [valid Kubernetes resource name][identifiers].
3. When creating a topic:
    * Remember that you can't change the name later.
    * It's best to use a name that is a [valid Kubernetes resource name][identifiers], 
      otherwise the controller will have to sanitize the name when creating 
      the corresponding ConfigMap.

    
## Format of the ConfigMap

By default, the controller only considers ConfigMaps having the label `strimzi.io/kind=topic`, 
but this is configurable via the `STRIMZI_CONFIGMAP_LABELS` environment variable.

The `data` of such ConfigMaps supports the following keys:

* `name` The name of the topic. Optional; if this is absent the name of the ConfigMap itself is used.
* `partitions` The number of partitions of the Kafka topic. This can be increased, but not decreased. Required. 
* `replicas` The number of replicas of the Kafka topic. Required. 
* `config` A string in JSON format representing the [topic configuration][topic-config]. Optional, defaulting to the empty set.
 

## Example

Suppose you want to create a topic called "orders" with 10 partitions and 2 replicas. 

You would first prepare a ConfigMap:

```yaml
# contents of orders-topic.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: orders
  labels:
    strimzi.io/kind: topic
data:
  name: orders
  partitions: "10"
  replicas: "2"
```

Because the `config` key is omitted from the `data` the topic's config will be empty, and thus default to the 
Kafka broker default.

You would then create this ConfigMap in Kubernetes:

    kubectl create -f orders-topic.yaml
    
Or in OpenShift:

    oc create -f orders-topic.yaml

That's it! The controller will create the topic "orders".

Suppose you later want to change the log segment retention time to 4 days, 
you can update `orders-topic.yaml` like this:

```yaml
# contents of orders-topic.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: orders
  labels:
    strimzi.io/kind: topic
data:
  name: orders
  partitions: "10"
  replicas: "2"
  config: '{ "retention.ms":"345600000" }'
```

And use `oc update -f` or `kubectl update -f` to up update the ConfigMap 
in OpenShift/Kubernetes.


## Unsupported operations

* You can't change the `data.name` key in a ConfigMap, because Kafka doesn't support changing topic names.
* You can't decrease the `data.partitions`, because Kafka doesn't support this.
* You should exercise caution in increasing `data.partitions` for topics with keys, as it will change 
  how records are partitioned. 

    
## Controller environment

The controller is configured from environment variables:

* `STRIMZI_CONFIGMAP_LABELS` 
– The Kubernetes label selector used to identify ConfigMaps to be managed by the controller.
  Default: `strimzi.io/kind=topic`.  
* `STRIMZI_ZOOKEEPER_SESSION_TIMEOUT`
– The Zookeeper session timeout. For example `10 seconds`. Default: `20 seconds`.
* `STRIMZI_KAFKA_BOOTSTRAP_SERVERS`
– The list of Kafka bootstrap servers. This variable is mandatory.
* `STRIMZI_ZOOKEEPER_CONNECT`
– The Zookeeper connection information. This variable is mandatory.
* `STRIMZI_FULL_RECONCILIATION_INTERVAL`
– The interval between periodic reconciliations.

If the controller configuration needs to be changed the process must be killed and restarted.
Since the controller is intended to execute within Kubernetes, this can be achieved
by deleting the pod.

[identifiers]: https://github.com/kubernetes/community/blob/master/contributors/design-proposals/architecture/identifiers.md
[topic-config]: https://kafka.apache.org/documentation/#topicconfigs
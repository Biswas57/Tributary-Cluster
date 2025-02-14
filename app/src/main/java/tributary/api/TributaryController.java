package tributary.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import tributary.core.tributaryObject.producers.*;
import tributary.core.rebalancingStrategy.RebalancingStrategy;
import tributary.core.tokenManager.TokenManager;
import tributary.core.tributaryFactory.*;
import tributary.core.tributaryObject.*;

/**
 * This class represents the main controller for managing and interacting with
 * the components of the Tributary Cluster. It provides functionality to create,
 * update, and manage topics, partitions, producers, consumers, and consumer
 * groups, as well as supporting parallel operations and event consumption.
 */
public class TributaryController {
    private TributaryCluster cluster;
    private ObjectFactory objectFactory;
    private Map<String, Class<?>> typeMap;
    private TributaryHelper helper;

    /**
     * Constructor to initialize the TributaryController with default settings.
     * It sets up the cluster, object factory, and type mappings.
     */
    public TributaryController() {
        this.cluster = TributaryCluster.getInstance();
        this.objectFactory = new StringFactory();
        this.helper = new TributaryHelper();
        this.typeMap = new HashMap<>();
        typeMap.put("integer", Integer.class);
        typeMap.put("string", String.class);
        typeMap.put("bytes", byte[].class);
    }

    public void setObjectFactoryType(String type) throws IllegalArgumentException {
        Class<?> typeClass = typeMap.get(type);
        if (typeClass == null) {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        this.objectFactory = (typeClass.equals(Integer.class)) ? new IntegerFactory() : new StringFactory();
    }

    public TributaryHelper getHelper() {
        return helper;
    }

    /*
     * Creation methods for objects in the Tributary Cluster.
     */

    /**
     * Creates a new topic in the Tributary Cluster.
     *
     * @param topicId The identifier for the new topic.
     * @param type    The type of events that the topic will handle.
     * @throws IllegalArgumentException if the topic already exists or the type is
     *                                  unsupported.
     */
    public void createTopic(String topicId, String type) throws IllegalArgumentException {
        if (helper.getTopic(topicId) != null) {
            throw new IllegalArgumentException("Topic " + topicId + " already exists.");
        }
        setObjectFactoryType(type);
        objectFactory.createTopic(topicId);
    }

    /**
     * Creates a new partition in the specified topic.
     *
     * @param topicId     The identifier of the topic to add the partition to.
     * @param partitionId The identifier for the new partition.
     * @throws IllegalArgumentException if the topic does not exist or the partition
     *                                  already exists.
     */
    public void createPartition(String topicId, String partitionId) throws IllegalArgumentException {
        Topic<?> topic = helper.getTopic(topicId);
        if (topic == null) {
            throw new IllegalArgumentException("Topic " + topicId + " does not exist.");
        } else if (topic.containsPartition(partitionId)) {
            throw new IllegalArgumentException(
                    "Partition " + partitionId + " already exists in topic " + topicId + ".");
        }
        String topicType = helper.getTopicType(topicId);
        setObjectFactoryType(topicType);
        objectFactory.createPartition(topicId, partitionId);
    }

    /**
     * Creates a new consumer group with the given identifier, subscribed to a
     * specified topic,
     * and initializes it with a specified rebalancing strategy.
     *
     * @param groupId     The identifier for the new consumer group.
     * @param topicId     The topic the consumer group will be subscribed to.
     * @param rebalancing The rebalancing method (e.g., "range", "roundrobin").
     * @throws IllegalArgumentException if the consumer group already exists.
     */
    public void createConsumerGroup(String groupId, String topicId, String rebalancing)
            throws IllegalArgumentException {
        if (helper.getConsumerGroup(groupId) != null) {
            throw new IllegalArgumentException("Consumer group " + groupId + " already exists.");
        }
        String type = helper.getTopicType(topicId);
        setObjectFactoryType(type);
        objectFactory.createConsumerGroup(groupId, topicId, rebalancing);
    }

    /**
     * Creates a new consumer within a specified consumer group.
     *
     * @param groupId    The identifier of the consumer group.
     * @param consumerId The identifier for the new consumer.
     * @throws IllegalArgumentException if the consumer already exists in the group.
     */
    public void createConsumer(String groupId, String consumerId) throws IllegalArgumentException {
        ConsumerGroup<?> group = helper.getConsumerGroup(groupId);
        if (group.containsConsumer(consumerId)) {
            throw new IllegalArgumentException("Consumer " + consumerId + " already exists in group " + groupId + ".");
        }
        String topicType = helper.getTopicType(group.getAssignedTopics().get(0).getId());
        setObjectFactoryType(topicType);
        objectFactory.createConsumer(groupId, consumerId);
    }

    /**
     * Creates a new producer for publishing events to a specified topic.
     *
     * @param producerId The identifier for the producer.
     * @param topicId    The topic the producer will publish to.
     * @param allocation The method of partition allocation (e.g., "random",
     *                   "manual").
     * @throws IllegalArgumentException if the producer creation fails due to a type
     *                                  mismatch.
     */
    public void createProducer(String producerId, String topicId, String allocation) throws IllegalArgumentException {
        String topicType = helper.getTopic(topicId).getType().getSimpleName().toLowerCase();
        setObjectFactoryType(topicType);
        objectFactory.createProducer(producerId, topicId, allocation);
    }

    /**
     * Creates an event from a specified producer to a given topic and partition.
     *
     * @param producerId  The identifier of the producer.
     * @param topicId     The topic to publish the event to.
     * @param eventId     The event identifier (e.g., filename).
     * @param partitionId The partition identifier (optional).
     * @throws IOException if event creation fails.
     */
    public synchronized void createEvent(String producerId, String topicId, String eventId, String partitionId)
            throws IOException {
        Producer<?> producer = helper.getProducer(producerId);
        Topic<?> topic = helper.getTopic(topicId);
        if (producer == null || topic == null) {
            throw new IllegalArgumentException("Producer " + producerId + " or topic " + topicId + " not found.");
        } else if (!helper.verifyProducer(producer, topic)) {
            throw new IllegalArgumentException("Producer does not have permission to produce to this topic.");
        } else if (!topic.getType().equals(producer.getType())) {
            throw new IllegalArgumentException("Producer type does not match topic type.");
        }

        synchronized (topic) {
            String topicType = topic.getType().getSimpleName().toLowerCase();
            setObjectFactoryType(topicType);
            objectFactory.createEvent(producerId, topicId, eventId, partitionId);
        }
    }

    /**
     * Deletes a consumer from the Tributary Cluster and triggers a rebalance in its
     * group.
     *
     * @param consumerId The identifier of the consumer to delete.
     */
    public void deleteConsumer(String consumerId) {
        cluster.deleteConsumer(consumerId);
    }

    /**
     * Returns a JSON representation of a specified topic, including its partitions
     * and messages.
     *
     * @param topicId The identifier of the topic to display.
     * @return A JSONObject representing the topic.
     * @throws IllegalArgumentException if the topic is not found.
     */
    public JSONObject showTopic(String topicId) {
        Topic<?> topic = cluster.getTopic(topicId);
        if (topic == null) {
            throw new IllegalArgumentException("Topic " + topicId + " not found.");
        }
        return topic.showTopic();
    }

    /**
     * Returns a JSON representation of a specified consumer group, including its
     * consumers and their assigned partitions.
     *
     * @param groupId The identifier of the consumer group to display.
     * @return A JSONObject representing the consumer group.
     * @throws IllegalArgumentException if the group is not found.
     */
    public JSONObject showGroup(String groupId) {
        ConsumerGroup<?> group = cluster.getConsumerGroup(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Group " + groupId + " not found.");
        }
        return group.showGroup();
    }

    /**
     * Consumes events from a specified partition using a consumer.
     *
     * @param consumerId     The identifier of the consumer.
     * @param partitionId    The identifier of the partition to consume from.
     * @param numberOfEvents The number of events to consume.
     * @return A JSONObject containing the consumed events.
     * @throws IllegalArgumentException if the consumer or partition is not found,
     *                                  or if access is denied.
     */
    public JSONObject consumeEvents(String consumerId, String partitionId, int numberOfEvents) {
        // Find the consumer first
        Consumer<?> consumer = helper.findConsumer(consumerId);
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer " + consumerId + " not found.");
        }

        // Retrieve the partition from the consumer's assigned partitions
        Partition<?> partition = consumer.getPartition(partitionId);
        if (partition == null) {
            throw new IllegalArgumentException(
                    "Partition " + partitionId + " not found for consumer " + consumerId + ".");
        }

        String topicId = partition.getAllocatedTopicId();
        Topic<?> topic = helper.getTopic(topicId);
        if (!helper.verifyConsumer(consumer, topic)) {
            throw new IllegalArgumentException("Consumer Group of consumer " + consumerId
                    + " does not have permission to consume from topic " + topicId + ".");
        }

        JSONObject data;
        if (topic.getType().equals(Integer.class)) {
            data = helper.consumeEventsGeneric(consumer, partition, Integer.class, numberOfEvents);
        } else if (topic.getType().equals(String.class)) {
            data = helper.consumeEventsGeneric(consumer, partition, String.class, numberOfEvents);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + topic.getType().getSimpleName());
        }

        return data;
    }

    /**
     * Updates the rebalancing strategy of a consumer group.
     *
     * @param groupId     The identifier of the consumer group.
     * @param rebalancing The new rebalancing strategy (e.g., "range",
     *                    "roundrobin").
     */
    public void updateRebalancing(String groupId, String rebalancing) {
        ConsumerGroup<?> group = helper.getConsumerGroup(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Consumer group " + groupId + " not found.");
        }
        RebalancingStrategy<?> prevMethod = group.getRebalanceMethod();
        group.setRebalancingMethod(rebalancing);
        group.rebalance();

        RebalancingStrategy<?> currMethod = group.getRebalanceMethod();
        if (prevMethod.equals(currMethod)) {
            System.out.println("Updated the rebalancing strategy for the " + groupId
                    + " group to " + currMethod.toString() + " rebalancing");
        }
    }

    /**
     * Updates the partition offset for a consumer to allow message replay or
     * backtracking.
     *
     * @param consumerId  The identifier of the consumer.
     * @param partitionId The identifier of the partition.
     * @param offset      The offset to set for the consumer.
     */
    public void updatePartitionOffset(String consumerId, String partitionId, int offset) {
        Consumer<?> consumer = helper.findConsumer(consumerId);
        Partition<?> partition = helper.findPartition(partitionId);
        Topic<?> topic = helper.getTopic(partition.getAllocatedTopicId());

        if (consumer == null || partition == null || topic == null) {
            throw new IllegalArgumentException("Consumer, partition, or topic not found.");
        }
        helper.updatePartitionOffsetGeneric(consumer, partition, offset);
    }

    /**
     * Updates the admin role for a consumer group.
     *
     * @param newGroupId The identifier of the new admin consumer group.
     * @param oldGroupId The identifier of the old admin consumer group (optional).
     * @param password   The password for verification (optional).
     */
    public void updateConsumerGroupAdmin(String newGroupId, String oldGroupId, String password) {
        ConsumerGroup<?> oldGroup = helper.getConsumerGroup(oldGroupId);
        if (oldGroup == null && cluster.getAdminConsToken() != null) {
            throw new IllegalArgumentException("Admin token exists but old Admin could not be identified.");
        } else if (oldGroup != null && cluster.getAdminConsToken() == null) {
            throw new IllegalArgumentException("Old admin token not found.");
        } else if (oldGroup != null && cluster.getAdminConsToken() != null) {
            oldGroup.clearAssignments();
            oldGroup.setToken(null);
            oldGroup.rebalance();

            String token = cluster.getAdminConsToken();
            if (!TokenManager.validateToken(token, oldGroup.getId(), oldGroup.getCreatedTime(), password)) {
                throw new IllegalArgumentException("Incorrect token for old Consumer Group Admin.");
            }
        }

        ConsumerGroup<?> newGroup = helper.getConsumerGroup(newGroupId);
        if (newGroup == null) {
            throw new IllegalArgumentException("New Consumer Group Admin " + newGroupId + " not found.");
        }

        String token = TokenManager.generateToken(newGroup.getId(), newGroup.getCreatedTime());
        cluster.setAdminConsToken(token);
        newGroup.setToken(token);
        helper.assignTopicGeneric(newGroup);
        newGroup.rebalance();
        newGroup.showTopics();
        newGroup.showGroup();
    }

    /**
     * Updates the admin role for a producer.
     *
     * @param newProdId The identifier of the new admin producer.
     * @param oldProdId The identifier of the old admin producer (optional).
     * @param password  The password for verification (optional).
     */
    public void updateProducerAdmin(String newProdId, String oldProdId, String password) {
        Producer<?> oldProd = helper.getProducer(oldProdId);
        if (oldProd == null && cluster.getAdminProdToken() != null) {
            throw new IllegalArgumentException("Admin token exists but old Admin could not be identified.");
        } else if (oldProd != null && cluster.getAdminProdToken() == null) {
            throw new IllegalArgumentException("Old admin token not found.");
        } else if (oldProd != null && cluster.getAdminProdToken() != null) {
            oldProd.clearAssignments();
            oldProd.setToken(null);

            String token = cluster.getAdminProdToken();
            if (!TokenManager.validateToken(token, oldProd.getId(), oldProd.getCreatedTime(), password)) {
                throw new IllegalArgumentException("Invalid token for old Producer Admin.");
            }
        }

        Producer<?> newProd = helper.getProducer(newProdId);
        if (newProd == null) {
            throw new IllegalArgumentException("New Producer Admin " + newProdId + " not found.");
        }

        String token = TokenManager.generateToken(newProd.getId(), newProd.getCreatedTime());
        cluster.setAdminProdToken(token);
        newProd.setToken(token);
        helper.assignTopicGeneric(newProd);
        newProd.showTopics();
    }

    /**
     * Produces a series of events in parallel.
     *
     * @param parts An array of parameters for the producer's events, topics, and
     *              partitions.
     * @throws IOException          if event creation fails.
     * @throws InterruptedException if the thread is interrupted.
     */
    public void parallelProduce(String[] parts) throws IOException, InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < parts.length;) {
            String producerId = parts[i];
            String topicId = parts[i + 1];
            String eventFile = parts[i + 2];

            Producer<?> producer = helper.getProducer(producerId);
            Topic<?> topic = helper.getTopic(topicId);
            if (producer == null || topic == null) {
                throw new IllegalArgumentException("Producer " + producerId + " or topic " + topicId + " not found.");
            } else if (!topic.getType().equals(producer.getType())) {
                throw new IllegalArgumentException(
                        "Producer " + producerId + " type does not match Topic " + topicId + " type.");
            }

            String partitionId = null;
            if (producer instanceof ManualProducer) {
                if (i + 3 < parts.length) {
                    partitionId = parts[i + 3];
                    if (helper.findPartition(partitionId) == null) {
                        throw new IllegalArgumentException("Partition " + partitionId + " not found.");
                    }
                    i += 4;
                } else {
                    throw new IllegalArgumentException("Insufficient parameters for manual producer " + producerId);
                }
            } else {
                i += 3;
            }

            final String finalPartitionId = partitionId;
            Future<?> future = executorService.submit(() -> {
                try {
                    createEvent(producerId, topicId, eventFile, finalPartitionId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else {
                    throw new RuntimeException(cause);
                }
            }
        }
    }

    /**
     * Consumes events in parallel from multiple partitions as specified by the
     * command parts.
     * Returns a JSON object with an "events" key mapping to a JSON array containing
     * the
     * consumption results for each consumer (with the consumer's id as the key).
     *
     * @param parts An array of parameters for the consumer events and the number of
     *              events to consume.
     * @return A JSONObject structured as: { "events": [ { "consumerId1": [ ... ] },
     *         { "consumerId2": [ ... ] } ] }
     */
    public JSONObject parallelConsume(String[] parts) {
        ExecutorService executorService = Executors.newFixedThreadPool(parts.length / 2);
        List<Future<JSONObject>> futures = new ArrayList<>();

        for (int i = 0; i < parts.length;) {
            String consumerId = parts[i];
            String partitionId = parts[i + 1];

            Partition<?> partition = helper.findPartition(partitionId);
            Consumer<?> consumer = helper.findConsumer(consumerId);
            if (consumer == null || partition == null) {
                throw new IllegalArgumentException(
                        "Consumer " + consumerId + " or partition " + partitionId + " not found.");
            }

            Topic<?> topic = helper.getTopic(partition.getAllocatedTopicId());
            int currentOffset = helper.parallelConsumerOffset(consumer, partition, topic.getType());
            int partitionSize = partition.listMessages().size();
            int numberOfEvents = partitionSize - currentOffset - 1;

            if (parts.length > i + 2 && helper.isInteger(parts[i + 2])) {
                numberOfEvents = Integer.parseInt(parts[i + 2]);
                i += 3;
            } else {
                i += 2;
            }

            final int finalNumberOfEvents = numberOfEvents;
            Future<JSONObject> future = executorService.submit(() -> {
                return consumeEvents(consumer.getId(), partition.getId(), finalNumberOfEvents);
            });
            futures.add(future);
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Parallel consume interrupted: " + e.getMessage(), e);
        }

        JSONArray eventsArray = new JSONArray();
        for (Future<JSONObject> future : futures) {
            try {
                JSONObject consumerEvents = future.get();
                eventsArray.put(consumerEvents);
            } catch (Exception e) {
                throw new RuntimeException("Error retrieving future result: " + e.getMessage(), e);
            }
        }

        JSONObject result = new JSONObject();
        result.put("events", eventsArray);
        return result;
    }

    public static void main(String[] args) {
        TributaryController controller = new TributaryController();

        // Create a topic "banana" with type "string"
        controller.createTopic("banana", "string");

        // Create partitions for the "banana" topic
        controller.createPartition("banana", "bananaCookingMethod1");
        controller.createPartition("banana", "bananaCookingMethod2");
        controller.createPartition("banana", "bananaCookingMethod3");
        controller.createPartition("banana", "bananaCookingStyle4");

        // Create a consumer group "bananaChefs" for the "banana" topic
        controller.createConsumerGroup("bananaChefs", "banana", "roundrobin");

        // Create consumers within the "bananaChefs" group
        controller.createConsumer("bananaChefs", "chef1");
        controller.createConsumer("bananaChefs", "chef2");
        controller.createConsumer("bananaChefs", "chef3");
        controller.createConsumer("bananaChefs", "chef4");

        // Create producers for the "banana" topic
        controller.createProducer("bananaBoiler", "banana", "manual");
        controller.createProducer("bananaFrier", "banana", "random");

        // Create events using the JSON file names provided
        try {
            controller.createEvent("bananaBoiler", "banana", "blendBanana", "bananaCookingMethod1");
            controller.createEvent("bananaBoiler", "banana", "boilBanana", "bananaCookingStyle1");
            controller.createEvent("bananaBoiler", "banana", "freezeBanana", "bananaCookingMethod1");
            controller.createEvent("bananaFrier", "banana", "fryBanana", null);
            controller.createEvent("bananaFrier", "banana", "grillBanana", null);
            controller.createEvent("bananaFrier", "banana", "roastBanana", null);
            controller.createEvent("bananaBoiler", "banana", "steamBanana", "bananaCookingMethod1");
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        }

        // Retrieve JSON representations of the topic and consumer group
        JSONObject consume1 = controller.consumeEvents("chef1", "bananaCookingMethod1", 1);
        JSONObject consumeMultiple = controller.consumeEvents("chef1", "bananaCookingMethod1", 4);

        // Print the JSON outputs
        System.out.println(consume1.toString(2));
        System.out.println(consumeMultiple.toString(2));
    }
}

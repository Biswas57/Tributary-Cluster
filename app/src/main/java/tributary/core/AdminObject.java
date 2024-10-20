package tributary.core;

import java.util.ArrayList;
import java.util.List;

import tributary.api.Topic;
import tributary.api.TributaryObject;

public class AdminObject<T> extends TributaryObject {
    private long createdTime;
    private List<Topic<T>> assignedTopics;
    private Class<T> type;

    public AdminObject(String id, Class<T> type) {
        super(id);
        this.createdTime = System.currentTimeMillis();
        this.assignedTopics = new ArrayList<>();
        this.type = type;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void clearAssignments() {
        Topic<T> topic = assignedTopics.get(0);
        assignedTopics.clear();
        assignTopic(topic);
    }

    public List<Topic<T>> getAssignedTopics() {
        return assignedTopics;
    }

    public void assignTopic(Topic<T> topic) {
        assignedTopics.add(topic);
    }

    public void unassignTopic(String topicId) {
        assignedTopics.removeIf(t -> t.getId().equals(topicId));
    }

    public List<Topic<T>> listAssignedTopics() {
        return assignedTopics;
    }

    public Class<T> getType() {
        return this.type;
    }

    public void showTopics() {
        System.out.println("Producer ID: " + getId() + " - Type: " + getType().getSimpleName());
        for (Topic<T> topic : assignedTopics) {
            System.out.println("Topic ID: " + topic.getId() + " - Partitions: ");
            topic.listPartitions().forEach(p -> System.out.print(p.getId() + ", "));
            System.out.println();
        }
        System.out.println("\n--------------------------------------------------\n");
    }
}

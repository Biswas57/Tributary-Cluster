# Engineering Requirements

## About Stream Processing

Stream processing refers to the execution of a process on data from an event stream, oftentimes shortly after the event is created. In other words, as data is received a stream processing application acts upon this data in near real-time. Stream processing, therefore, allows for data to move through a data pipeline in a manner that enables the analysis or utilization of this data in the most efficient manner possible. This is in opposition to batch processing where data is collected and stored to be operated upon at a later time.

A stream processor can have many jobs. For instance, such an application may simply be responsible for creating more specific events which are then detected by another stream processing application further down the data pipeline. In other cases, a stream processor could be tasked with performing real-time operations on event data to provide an alert to a particular condition represented by the data point (i.e. alerting a stockbroker that a particular stock has hit an all-time high, possibly indicating a time to sell) or to provide context to data that has been processed over time (i.e. the real-time percentage change in a stock’s price over the past year).

Today, several stream-processing platforms exist to support the building of stream-processing applications. One such platform (of great popularity) is Apache Kafka. It’s hard to discuss stream processing platforms without discussing Kafka, just as it is hard to discuss data-based applications without recognizing the importance of properly securing them. This library is based on a heavily simplified version of the event streaming infrastructure [Apache Kafka](https://kafka.apache.org/). A quick read of Kafka's design and purpose is recommended to understand the basis and workings of this project, a brief video to understand what they do can be found [here](https://youtu.be/vHbvbwSEYGo).

The fundamental premise on which Event-Driven Architecture rests is the ability of producer and consumer entities in the system to share data asynchronously via a stream-like channel, in other words a Tributary-like platform. However, our library allowed for more complex interactions than simply that of a single channel. 

## Structure
A **Tributary Cluster** contains a series of **topics**. A topic contains events which are logically grouped together. For example, a cluster could contain two topics: one for images-related events and one for video-related events. You can think of them like a table in a database or a folder in a file system.

<img src="images/tributaryClusterExample.png" width="400px" />

Within each topic, there are a series of partitions - each partition is a queue where new messages are appended at the end of the partition.

<img src="images/topicExample.png" width="600px" />

A unit of data within a Tributary is a **message**, or record or event. For example, to update their profile a user may send a message to Partition 1 in Topic A, and this message will be appended to Partition 1 in Topic A. Each message has an optional key to indicate which partition it should be appended to.

A topic can be related to “user profiles” and each message relates to requesting an update to a specific profile. However, considering there can be many such requests at a given time, the system divides the incoming requests into multiple partitions. There can be multiple **consumers** consuming messages at the same time (concurrently). However, each partition is handled by only one consumer. Multiple consumers will allow us to effectively utilise the underlying hardware with multiple cores.

In the context of the library that I am building, topics are parameterised on a generic type; all event payloads within that topic must be of the specified type.

## 1.1. Message Lifecycle: A Simple Example

Let us take the example of a user updating their profile. This results in an event being generated by the **producer** for a topic “user profiles” with the updated profile information. This event is now delivered to the Tributary, which assigns the event to one of the partitions. The producer indicates whether the message is randomly allocated to a partition, or provides a key specifying which partition to append the message to.

A consumer processes one or more partitions by sequentially processing (consuming) events in the allocated partitions.

## 1.2. Message Structure

Individual messages contain the following information:

- Headers
  - Datetime created;
  - ID;
  - Payload type;
- Key; and
- Value. The value is an object containing relevant information for a topic. Considering information required for different topics may change, you should consider using a generic type here.

<img src="images/messageStructure.png" width="600px" />

## 1.3. Producers

A Producer is responsible for sending messages to the Tributary system. As shown in the diagram above, a message contains info including the datetime it was created, the source producer, etc. Messages may have a key which indicates the specific partition to send the message to. Alternatively, messages are randomly assigned to a partition by the system.

### 1.3.1. Allocation of Messages to Partitions

Producers can indicate whether to send a message to a particular partition by providing the corresponding partition key or requesting random allocation. There are two types of producers:

- **Random Producers** - the producer requests the Tributary system to randomly assign a message to a partition
- **Manual Producers** - the producer requests the Tributary system to assign a message to a particular partition by providing its corresponding key.

Once a producer has been created with one of the two above message allocation methods, it cannot change its message allocation method. My implementation will allow for producers to be created with new message allocation methods added in the future.

## 1.4. Consumer and Consumer Groups

### 1.4.1. Consumers

Consumers are responsible for consuming (processing) messages stored in partition queues. A consumer consumes messages from a partition in the order that they were produced, and keeps track of the messages that have been consumed. Although each partition can be consumed by only one consumer, each consumer can consume from more than one partition. Consumers operate as part of a consumer group.

### 1.4.2. Consumer Groups

A consumer group consists of one or more consumers, that are together capable of consuming from all the partitions in a topic.

Each topic can have multiple consumer groups. While each consumer group assigned to the same topic may contain a different number of consumers, they will all consume from the same number of partitions, i.e. all the partitions in a topic will always be handled by any consumer group assigned to the topic.

When a new consumer group is created, the consumers in the group begin their consumption from the **first unconsumed message** in all of the topics partitions they are assigned to. In other words, all consumers that share a partition consume messages parallel to each other, so that each message is only consumed once (except in controlled replays).

For example, in the image below Topic D is consumed by Consumer Group A, which has its 3 consumers assigned to the 5 partitions. Topic D is also consumed by Consumer Group B, which has its 4 consumers assigned to the 5 partitions.

<img src="images/consumerAllocation1.png" width="600px" />

<img src="images/consumerAllocation2.png" width="600px" />

### 1.4.3. Consumer Rebalancing

A system will be able to dynamically change the rebalancing strategy between one of two rebalancing strategies - range rebalancing, and round robin rebalancing. These rebalancing strategies are used to reassign partitions to consumers anytime a consumer is added to a consumer group or an existing consumer is removed from a consumer group.

If a partition is assigned a new consumer after rebalancing, the new consumer will continue consumption from where the previous consumer left off.

#### 1.4.3.1. Range Rebalancing

**Range** - The partitions are divided up evenly and allocated to the consumers. If there is an odd number of partitions, the first consumer takes one extra.

<img src="images/rangeAllocation.png" width="600px" />

In the above example, Partitions 0, 1, 2, 3 are allocated to Consumer I and Partitions 4, 5 and 6 are allocated to Consumer II.

#### 1.4.3.2. Round Robin Rebalancing

**Round Robin** - In a round robin fashion, the partitions are allocated like cards being dealt out, where consumers take turns being allocated the next partition.

<img src="images/roundAllocation.png" width="600px" />

In the above example, Partitions 0, 2, 4 and 6 are allocated to Consumer I, and Partitions 1, 3 and 5 are allocated to Consumer II.

## 1.5. Replay Messages

One of the most powerful aspects of event streaming is the ability to **replay** messages that are stored in the queue. The way this can occur is via a **controlled replay**, which is done from a message offset in a partition. Messages from that point onwards are streamed through the pipeline, until the most recent message at the latest offset is reached.

![](/images/controlledReplay.png)

> ℹ  NOTE: The above image demonstrates a consumer starting at offset 6 that performed normal consumption until offset 9. This consumer then triggered a
> controlled replay from offset 4 that played back all the messages from that offset until the most recently consumed message (i.e messages 6, 7, 8 and 9
> were consumed again).

## 1.6. Design Considerations

Two design considerations I needed to think about were:

- **Concurrency** - since Producers and Consumers are all running in parallel, operating on a shared resource, how will I ensure correctness?
- **Generics** - How will I ensure that an object of any type can be used as an event payload in a tributary topic?

# 2. Interface

## 2.1. Java API

Since I am building a library on which other engineers can develop event-driven systems, I will need to have some classes which are available for others to use in their implementations - just as one does when they import any Java library.

I will need to determine which classes are part of the interface (API which other developers can use), and these classes will need to be documented with JavaDoc and go inside a folder called `api`. All other classes are considered part of the internal workings of the system (black box) and do not need JavaDoc. These classes should go inside a folder called `core`.

## 2.2. Philosophy and Usage

The way we refer to API here is a little bit different to how you might be used to it from previous courses. The term “API” usually refers to a web-based service that you can call upon via an endpoint, to perform some of the work required by your application. In that scenario, components that make up the API are abstracted away from your application.

However, when we refer to an API here, we are describing more a library rather than a service. By using this library, we can construct various different domain specific applications by bringing together the components provided by the library. The Command Line Interface application described below is one specific instance of a domain-specific application that we are using our library to build, but it is not the behaviour of the API in itself. That is to say, the functionality that we want from the CLI isn’t abstracted behind the API as a service that facilitates requests based on user input, but rather we use the components provided by the library and coordinate them for this specific use case.

We should be able to take this library and create some other domain specific application that isn’t tied to this specific Command Line Interface program. Additionally, the components provided by your library like the producer and the consumer should be able to be extended by the user of your library to define new types of producers and consumers for their specific application, building upon the mechanisms and functionality provided by your library.

Although the library exposes a bunch of components publicly (the stuff that goes in `api` folder), there will also be things that the library uses internally that isn’t exposed to the user (the stuff that goes in the `core` folder). Hence the user only interacts with the public API of the library and uses the library and its components in the way the API defines.

You can think of all of this very similarly to the Java API. It is a library that provides a bunch of components that we can use to build our applications. The components that are available and the way we are able to use them are defined by the public API. We can also extend things provided by the library. For example, if I really wanted to, I could extend the `ArrayList` class, or maybe more practically, I can implement provided interfaces in my own custom components.

## 2.3. Command Line Interface

In order to run usability tests on your solution I needed to develop a way to interact with tributaries, producers, and consumers via a command line interface.

To do so, you should write a wrapper class called `TributaryCLI` that allows the user to input commands that create, modify, and interact with a tributary cluster system. This class should be in a separate package to the `api/core` packages of your library, as it shouldn't be a part of your library that other engineers developing their own event-driven systems would use.

As an **example** of what commands your CLI may provide, the following table has CRUD operations that you can implement - you can add/modify/remove CRUD operations as you see fit. If you choose to implement the table as is, note that you are free to modify the naming/syntax/output of commands. **The only requirement for the CLI is that you can use it to showcase an implementation of the [Engineering Requirements](#1-engineering-requirements) discussed in Section 1**.


<table>
  <tr>
    <th><b>Command</b></th>
    <th><b>Description</b></th>
    <th><b>Output</b></th>
  </tr>
  <tr>
    <td><code>create topic &lt;id&gt; &lt;type&gt;</code></td>
    <td>
      <ul>
        <li>Creates a new topic in the tributary.</li>
        <li><code>id</code> is the topic’s identifier.</li>
        <li>
          <code>type</code> is the type of event that goes through the topic.
          While this can be any type in Java, for the purposes of the CLI it can
          either be <code>Integer</code> or <code>String</code>.
        </li>
      </ul>
    </td>
    <td>
      A message showing the id, type and other relevant information about the
      topic confirming its creation.
    </td>
  </tr>
  <tr>
    <td><code>create partition &lt;topic&gt; &lt;id&gt;</code></td>
    <td>
      <ul>
        <li>
          Creates a new partition in the topic with id <code>topic</code>.
        </li>
        <li><code>id</code> is the partition’s identifier.</li>
      </ul>
    </td>
    <td>A message confirming the partition’s creation.</td>
  </tr>
  <tr>
    <td>
      <code>create consumer group &lt;id&gt; &lt;topic&gt; &lt;rebalancing&gt;</code>
    </td>
    <td>
      <ul>
        <li>Creates a new consumer group with the given identifier.</li>
        <li>
          <code>topic</code> is the topic the consumer group is subscribed to.
        </li>
        <li>
          <code>rebalancing</code> is the consumer group’s initial rebalancing
          method, one of <code>Range</code> or <code>RoundRobin</code>.
        </li>
      </ul>
    </td>
    <td>A message confirming the consumer group’s creation.</td>
  </tr>
  <tr>
    <td><code>create consumer &lt;group&gt; &lt;id&gt;</code></td>
    <td>
      <ul>
        <li>Creates a new consumer within a consumer group.</li>
      </ul>
    </td>
    <td>A message confirming the consumer’s creation.</td>
  </tr>
  <tr>
    <td><code>delete consumer &lt;consumer&gt;</code></td>
    <td>
      <ul>
        <li>Deletes the consumer with the given id.</li>
      </ul>
    </td>
    <td>
      A message confirming the consumer’s deletion, and an output of the
      rebalanced consumer group that the consumer was previously in.
    </td>
  </tr>
  <tr>
    <td>
      <code>create producer &lt;id&gt; &lt;type&gt; &lt;allocation&gt;</code>
    </td>
    <td>
      <ul>
        <li>Creates a new producer which produces events of the given type.</li>
        <li>
          <code>allocation</code> is either <code>Random</code> or
          <code>Manual</code>, determining which method of partition selection
          is used for publishing events.
        </li>
      </ul>
    </td>
    <td>A message confirming the producer’s creation.</td>
  </tr>
  <tr>
    <td>
      <code>produce event &lt;producer&gt; &lt;topic&gt; &lt;event&gt;&lt;partition&gt;</code>
    </td>
    <td>
      <ul>
        <li>
          Produces a new event from the given producer to the given topic.
        </li>
        <li>
          How you represent the event is up to you. We recommend using a JSON
          structure to represent the different parts of an event and the
          <code>event</code> parameter to this command is a filename to a JSON
          file with the event content inside.
        </li>
        <li>
          <code>partition</code> is an optional parameter used only if the
          producer publishes events to a manually specified partition
        </li>
      </ul>
    </td>
    <td>The event id, the id of the partition it is currently in.</td>
  </tr>
  <tr>
    <td><code>consume event &lt;consumer&gt; &lt;partition&gt;</code></td>
    <td>
      <ul>
        <li>
          The given consumer consumes an event from the given partition.
          Precondition: The consumer is already allocated to the given
          partition.
        </li>
      </ul>
    </td>
    <td>
      The id and contents of the event, showing that the consumer has received
      the event.
    </td>
  </tr>
  <tr>
    <td>
      <code>consume events &lt;consumer&gt; &lt;partition&gt; &lt;number of events&gt;</code>
    </td>
    <td>
      <ul>
        <li>Consumes multiple events from the given partition.</li>
      </ul>
    </td>
    <td>The id and contents of each event received in order.</td>
  </tr>
  <tr>
    <td><code>show topic &lt;topic&gt;</code></td>
    <td>
      <ul>
        <li>Prints a visual display of the given topic, including all partitions and all of the events currently in each partition.</li>
      </ul>
    </td>
    <td>
      A detailed visual representation of the topic, listing all partitions within the topic and the events currently stored in each partition.
    </td>
  </tr>
  <tr>
    <td><code>show consumer group &lt;group&gt;</code></td>
    <td>
      <ul>
        <li>Shows all consumers in the consumer group, and which partitions each consumer is receiving events from.</li>
      </ul>
    </td>
    <td>
      A detailed list of all consumers in the specified consumer group, including the partitions each consumer is allocated to and is receiving events from.
    </td>
  </tr>
  <tr>
    <td>
      <code>parallel produce (&lt;producer&gt;, &lt;topic&gt;, &lt;event&gt;), ...</code>
    </td>
    <td>
      <ul>
        <li>
          Produces a series of events in parallel. This is purely for
          demonstrating that your tributary can cope with multiple producers
          publishing events simultaneously.
        </li>
      </ul>
    </td>
    <td>For each event, the id of the partition it is currently in.</td>
  </tr>
  <tr>
    <td>
      <code> parallel consume (&lt;consumer&gt;, &lt;partition&gt;), ... </code>
    </td>
    <td>
      <ul>
        <li>
          Consumes a series of events in parallel. This is purely for
          demonstrating that your tributary can cope with multiple consumers
          receiving events simultaneously.
        </li>
      </ul>
    </td>
    <td>For each event consumed, the contents of the event and its id.</td>
  </tr>
  <tr>
    <td>
      <code>update rebalancing method &lt;group&gt; &lt;rebalancing&gt;</code>
    </td>
    <td>
      <ul>
        <li>
          Updates the rebalancing method of consumer group <code>group</code> to
          be one of <code>Range</code> or <code>RoundRobin</code>.
        </li>
      </ul>
    </td>
    <td>A message confirming the new rebalancing method.</td>
  </tr>
  <tr>
    <td>
      <code>update consumer offset &lt;consumer&gt; &lt;partition&gt; &lt;offset&gt;</code>
    </td>
    <td>
      <ul>
        <li>Plays back events for a given consumer from the offset.
        <li>Controlled Replay: Consumers can replay messages from a specific offset. ie. 2 = 2nd message in the partition</li>
        <li>Backtrack Replay: Consumers can backtrack their processed Messages, ie. -2 = 2nd last message processed</li>
        <li>If the offset is not inputted then updateConsumerOffset will set the offset as the last processed message. If the offset inputted is 0 then updateConsumerOffset will set the offset to the last message in the Partition</li>
      </ul>
    </td>
    <td>The id and contents of each event received in order.</td>
  </tr>
</table>

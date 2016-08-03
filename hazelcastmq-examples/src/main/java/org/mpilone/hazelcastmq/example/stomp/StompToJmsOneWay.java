package org.mpilone.hazelcastmq.example.stomp;

import org.mpilone.hazelcastmq.stomp.StompAdapterConfig;
import org.mpilone.hazelcastmq.stomp.StompAdapter;
import org.mpilone.hazelcastmq.stomp.HazelcastMQStomp;

import javax.jms.*;
import javax.jms.Message;

import org.mpilone.hazelcastmq.core.*;
import org.mpilone.hazelcastmq.example.Assert;
import org.mpilone.hazelcastmq.jms.*;
import org.mpilone.hazelcastmq.stomp.server.*;
import org.mpilone.yeti.Frame;
import org.mpilone.yeti.FrameBuilder;
import org.mpilone.yeti.client.StompClient;
import org.slf4j.*;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;

/**
 * This example uses a stomp-server to accept a stomp-client connection. The
 * client then sends a STOMP frame. A JMS consumer then consumes the message.
 * The stomp-server is backed by the {@link HazelcastMQInstance} which is backed
 * by a local Hazelcast instance. This example shows how STOMP clients connected
 * to the stomp-server, backed by HazelcastMQ, can easily interoperate with JMS
 * producers and consumers.
 * 
 * @author mpilone
 * 
 */
public class StompToJmsOneWay {

  /**
   * The log for this class.
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  public static void main(String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    System.setProperty("org.slf4j.simpleLogger.log.com.hazelcast", "info");
    System.setProperty("org.slf4j.simpleLogger.log.io.netty", "info");

    new StompToJmsOneWay();
  }

  public StompToJmsOneWay() throws Exception {

    // Create a Hazelcast instance.
    Config config = new Config();
    config.setProperty("hazelcast.logging.type", "slf4j");
    config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
    HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance(config);

    try {
      // Create the HazelcaseMQ instance.
      HazelcastMQConfig mqConfig = new HazelcastMQConfig();
      mqConfig.setHazelcastInstance(hazelcast);
      HazelcastMQInstance mqInstance = HazelcastMQ
          .newHazelcastMQInstance(mqConfig);

      // Create a Stomp server.
      StompAdapterConfig stompConfig = new StompAdapterConfig(
          mqInstance);
      StompAdapter stompServer = HazelcastMQStomp.newStompAdapter(stompConfig);

      log.info("Stomp server is now listening on port: "
          + stompConfig.getPort());

      // Create a Stomp client.
      StompClient stompClient = new StompClient("localhost", stompConfig.
          getPort());
      stompClient.connect();

      // Send a message to a queue.
      Frame frame = FrameBuilder.send("/queue/demo.test", "Hello World!")
          .build();
      stompClient.send(frame);

      // Now create a JMS consumer to consume that message.
      HazelcastMQJmsConfig jmsConfig = new HazelcastMQJmsConfig();
      jmsConfig.setHazelcastMQInstance(mqInstance);

      HazelcastMQJmsConnectionFactory connectionFactory = new HazelcastMQJmsConnectionFactory(
          jmsConfig);

      Connection connection = connectionFactory.createConnection();
      connection.start();
      Session session = connection.createSession(false,
          Session.AUTO_ACKNOWLEDGE);
      Destination destination = session.createQueue("demo.test");
      MessageConsumer consumer = session.createConsumer(destination);

      Message msg = consumer.receive(5000);
      Assert.notNull(msg, "Did not get required message.");
      Assert.isTrue(msg instanceof TextMessage,
          "Did not get correct message type.");

      log.info("Got expected JMS message: " + ((TextMessage) msg).getText());

      // Shutdown the JMS consumer
      consumer.close();
      session.close();
      connection.close();

      // Shutdown the client.
      stompClient.disconnect();

      // Shutdown the server.
      log.info("Shutting down STOMP server.");
      stompServer.shutdown();
    }
    finally {
      // Shutdown Hazelcast.
      hazelcast.getLifecycleService().shutdown();
    }

  }
}

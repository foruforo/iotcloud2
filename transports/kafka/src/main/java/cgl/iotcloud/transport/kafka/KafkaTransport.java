package cgl.iotcloud.transport.kafka;

import cgl.iotcloud.core.Configuration;
import cgl.iotcloud.core.transport.Channel;
import cgl.iotcloud.core.transport.ChannelName;
import cgl.iotcloud.core.transport.Direction;
import cgl.iotcloud.core.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KafkaTransport implements Transport {
    private static Logger LOG = LoggerFactory.getLogger(KafkaTransport.class);

    public static final int KAFKA_DEFAULT_PORT = 2012;

    public static final String PROP_URLS = "urls";

    public static final String PROP_MAX_READS = "maxReads";
    public static final String PROP_TOPIC = "topic";
    public static final String PROP_PARTITION = "partition";
    public static final String PROP_BUFFER_SIZE = "bufferSize";
    public static final String PROP_FETCH_SIZE = "fetchSize";
    public static final String PROP_POLLING_INTERVAL = "pollingInterval";

    public static final String PROP_SERIALIZER_CLASS = "serializerClass";
    public static final String PROP_PARTITION_CLASS = "partitionClass";
    public static final String PROP_REQUEST_REQUIRED_ACKS = "rrAcks";

    private Map<String, Integer> urls = new HashMap<String, Integer>();

    private Map<ChannelName, KafkaProducer> producers = new HashMap<ChannelName, KafkaProducer>();
    private Map<ChannelName, KafkaConsumer> consumers = new HashMap<ChannelName, KafkaConsumer>();


    private String siteId;

    @Override
    public void configure(String siteId, Map properties) {
        this.siteId = siteId;
        Map params = (Map)properties.get(Configuration.TRANSPORT_PROPERTIES);
        Object urlProp = params.get(PROP_URLS);
        if (urlProp == null || !(urlProp instanceof List)) {
            String message = "Url is required by the Kafka Transport";
            LOG.error(message);
            throw new RuntimeException(message);
        }

        for (Object o : (List)urlProp) {
            if (o instanceof String) {
                String url = (String) o;
                String tokens[] = url.split(":");
                if (tokens.length == 2) {
                    urls.put(tokens[0], Integer.parseInt(tokens[1]));
                } else {
                    urls.put(tokens[0], KAFKA_DEFAULT_PORT);
                }
            }
        }
    }

    @Override
    public void registerChannel(ChannelName name, Channel channel) {
        Map channelConf = channel.getProperties();
        if (channelConf == null) {
            throw new IllegalArgumentException("Channel properties must be specified");
        }

        if (channel.getDirection() == Direction.OUT) {
            createSender(name, channelConf, channel);
        } else if (channel.getDirection() == Direction.IN) {
            createReceiver(name, channelConf, channel);
        }
    }

    private void createSender(ChannelName name, Map channelConf, Channel channel) {
        String topic = (String) channelConf.get(PROP_TOPIC);
        String serializerClass = (String) channelConf.get(PROP_SERIALIZER_CLASS);
        String partitionClass = (String) channelConf.get(PROP_PARTITION_CLASS);
        String requestRequiredAcks = (String) channelConf.get(PROP_REQUEST_REQUIRED_ACKS);

        StringBuilder brokerList = new StringBuilder("");
        int count = 0;
        for (Map.Entry<String, Integer> e : urls.entrySet()) {
            if (count == urls.entrySet().size() - 1) {
                brokerList.append(e.getKey()).append(":").append(e.getValue());
            } else {
                brokerList.append(e.getKey()).append(":").append(e.getValue()).append(',');
            }
        }

        KafkaProducer sender = new KafkaProducer(channel.getConverter(), channel.getOutQueue(), siteId + "." +  topic, brokerList.toString(),
                serializerClass, partitionClass, requestRequiredAcks);
        producers.put(name, sender);
        sender.start();
    }

    private void createReceiver(ChannelName name, Map channelConf, Channel channel) {
        String topic = (String) channelConf.get(PROP_TOPIC);
        int partition = (Integer) channelConf.get(PROP_PARTITION);

        KafkaConsumer listener = new KafkaConsumer(channel.getConverter(), channel.getInQueue(), siteId + "." + topic, partition, urls);
        consumers.put(name, listener);
        listener.start();
    }

    @Override
    public void start() {
        for (KafkaConsumer receiver : consumers.values()) {
            receiver.start();
        }

        for (KafkaProducer sender : producers.values()) {
            sender.start();
        }
    }

    @Override
    public void stop() {
        for (KafkaConsumer receiver : consumers.values()) {
            receiver.stop();
        }

        for (KafkaProducer sender : producers.values()) {
            sender.stop();
        }
    }
}
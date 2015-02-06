/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.lib.kafka;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.ChooserMode;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.Label;
import com.streamsets.pipeline.api.OffsetCommitter;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.ValueChooser;
import com.streamsets.pipeline.api.base.BaseSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@ConfigGroups(HighLevelAbstractKafkaSource.Groups.class)
public abstract class HighLevelAbstractKafkaSource extends BaseSource implements OffsetCommitter {
  private static final Logger LOG = LoggerFactory.getLogger(HighLevelAbstractKafkaSource.class);
  private static final String DOT = ".";

  public enum Groups implements Label {
    KAFKA
    ;

    public String getLabel() {
      return "Kafka";
    }
  }

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "localhost:2181",
      label = "Zookeeper Connection String",
      description = "List of the Zookeeper HOST:PORT used by the Kafka brokers, comma separated",
      displayPosition = 10,
      group = "KAFKA"
  )
  public String zookeeperConnect;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "streamsetsDataCollector",
      label = "Consumer Group",
      description = "The Consumer Group columnName",
      displayPosition = 20,
      group = "KAFKA"
  )
  public String consumerGroup;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "topicName",
      label = "Topic",
      description = "The Kafka topic from which the messages must be read",
      displayPosition = 30,
      group = "KAFKA"
  )
  public String topic;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      label = "Payload Type",
      description = "Type of data sent as kafka message payload",
      displayPosition = 40,
      group = "KAFKA"
  )
  @ValueChooser(type = ChooserMode.PROVIDED, chooserValues = ConsumerPayloadTypeChooserValues.class)
  public ConsumerPayloadType consumerPayloadType;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.INTEGER,
      defaultValue = "1000",
      label = "Max Batch Size",
      description = "The maximum number of messages to be read from Kafka in a batch.",
      displayPosition = 50,
      group = "KAFKA"
  )
  public int maxBatchSize;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.INTEGER,
      defaultValue = "1000",
      label = "Batch Duration Time (millisecs)",
      description = "The maximum time to wait to fill a batch.",
      displayPosition = 60,
      group = "KAFKA"
  )
  public int maxWaitTime;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.MAP,
      defaultValue = "",
      label = "Kafka Configuration",
      description = "Additional configuration properties which will be used by the underlying Kafka consumer.",
      displayPosition = 70,
      group = "KAFKA"
  )
  public Map<String, String> kafkaConsumerConfigs;


  private HighLevelKafkaConsumer kafkaConsumer;

  @Override
  public void init() throws StageException {
    if(getContext().isPreview()) {
      //set fixed batch duration time of 1 second for preview.
      maxWaitTime = 1000;
    }
    kafkaConsumer = new HighLevelKafkaConsumer(zookeeperConnect, topic, consumerGroup, maxBatchSize, maxWaitTime,
      kafkaConsumerConfigs, getContext());
    kafkaConsumer.init();
    LOG.info("Successfully initialized Kafka Consumer");
  }

  @Override
  public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    LOG.debug("Reading messages from kafka.");
    int recordCounter = 0;
    int batchSize = this.maxBatchSize > maxBatchSize ? maxBatchSize : this.maxBatchSize;
    long startTime = System.currentTimeMillis();
    while(recordCounter < batchSize && (startTime + maxWaitTime) > System.currentTimeMillis()) {
      MessageAndOffset message = kafkaConsumer.read();
      if(message != null) {
        List<Record> records = createRecords(message, recordCounter);
        recordCounter += records.size();
        for(Record record : records) {
          batchMaker.addRecord(record);
        }
      }
    }
    LOG.info("Produced {} records in this batch.", recordCounter);
    return lastSourceOffset;
  }

  @Override
  public void destroy() {
    LOG.info("Destroying Kafka Consumer");
    kafkaConsumer.destroy();
    super.destroy();
  }

  @Override
  public void commit(String offset) throws StageException {
    LOG.info("Committing offset for topic.");
    kafkaConsumer.commit();
  }

  protected abstract List<Record> createRecords(MessageAndOffset message, int currentRecordCount) throws StageException;

}

final TopicNameExtractor <String, CompletedOrder> orderTopicNameExtractor = (key, completedOrder, recordContext) -> {
      final String compositeId = completedOrder.getId();
      final String skuPart = compositeId.substring(compositeId.indexOf('-') + 1, 5);
      final String outTopic;
      if (skuPart.equals("QUA")) {
           outTopic = specialOrderOutput;
      } else {
           outTopic = orderOutputTopic;
      }
      return outTopic;
};

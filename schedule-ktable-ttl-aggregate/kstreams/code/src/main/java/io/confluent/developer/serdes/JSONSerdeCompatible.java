package io.confluent.developer.serdes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.confluent.developer.AggregateObject;
import io.confluent.developer.ValueWrapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AggregateObject.class, name = "aggregateObject"),
  @JsonSubTypes.Type(value = ValueWrapper.class, name = "valueWrapper")
})
public abstract class JSONSerdeCompatible {

}

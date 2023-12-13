package io.confluent.developer;

import io.confluent.developer.serdes.JSONSerdeCompatible;

public class ValueWrapper extends JSONSerdeCompatible {

  Object value;
  boolean deleted = false;

  public ValueWrapper() {}

  public ValueWrapper(Object value) {
    this.value = value;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }



}

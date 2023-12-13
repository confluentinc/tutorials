package io.confluent.developer;

public class TestRecord {
    private String name;
    private int count;
    private long number;

    public TestRecord(String name, int count, long number) {
        this.name = name;
        this.count = count;
        this.number = number;
    }

    public TestRecord() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestRecord)) return false;
        TestRecord that = (TestRecord) o;
        if (getCount() != that.getCount()) return false;
        if (getNumber() != that.getNumber()) return false;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        int result = getName().hashCode();
        result = 31 * result + getCount();
        result = 31 * result + (int) (getNumber() ^ (getNumber() >>> 32));
        return result;
    }
}

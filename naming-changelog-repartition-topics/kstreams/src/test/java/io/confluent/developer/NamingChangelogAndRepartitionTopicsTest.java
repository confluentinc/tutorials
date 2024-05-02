package io.confluent.developer;

import org.apache.kafka.streams.Topology;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;



public class NamingChangelogAndRepartitionTopicsTest {

    private final NamingChangelogAndRepartitionTopics instance = new NamingChangelogAndRepartitionTopics();

    @Test
    public void shouldUpdateNamesOfStoresAndRepartitionTopics() {

        Properties properties = new Properties();
        properties.put("add.filter", "false");
        Topology topology = instance.buildTopology(properties);

        final String firstTopologyNoFilter = topology.describe().toString();

        // Names of auto-generated state store and repartition topic in original topology
        final String initialStateStoreName = "KSTREAM-AGGREGATE-STATE-STORE-0000000002";
        final String initialAggregationRepartition = "KSTREAM-AGGREGATE-STATE-STORE-0000000002-repartition";

        // Names of auto-generated state store and repartition topic after adding an operator upstream
        // notice that the number in the names is incremented by 1 reflecting the addition of a
        // new operation
        final String stateStoreNameWithFilterAdded = "KSTREAM-AGGREGATE-STATE-STORE-0000000003";
        final String aggregationRepartitionWithFilterAdded = "KSTREAM-AGGREGATE-STATE-STORE-0000000003-repartition";

        assertThat(firstTopologyNoFilter.indexOf(initialStateStoreName), greaterThan(0));
        assertThat(firstTopologyNoFilter.indexOf(initialAggregationRepartition), greaterThan(0));
        assertThat(firstTopologyNoFilter.indexOf(stateStoreNameWithFilterAdded), is(-1));
        assertThat(firstTopologyNoFilter.indexOf(aggregationRepartitionWithFilterAdded), is(-1));

        properties.put("add.filter", "true");
        topology = instance.buildTopology(properties);
        final String topologyWithFilter = topology.describe().toString();

        assertThat(topologyWithFilter.indexOf(initialStateStoreName), is(-1));
        assertThat(topologyWithFilter.indexOf(initialAggregationRepartition), is(-1));
        assertThat(topologyWithFilter.indexOf(stateStoreNameWithFilterAdded), greaterThan(0));
        assertThat(topologyWithFilter.indexOf(aggregationRepartitionWithFilterAdded), greaterThan(0));
    }

    @Test
    public void shouldNotUpdateNamesOfStoresAndRepartitionTopics() {
        Properties properties = new Properties();
        properties.put("add.names", "true");

        Topology topology = instance.buildTopology(properties);
        final String firstTopologyNoFilter = topology.describe().toString();

        final String initialStateStoreName = "the-counting-store";
        final String initialAggregationRepartition = "count-repartition";

        assertThat(firstTopologyNoFilter.indexOf(initialStateStoreName), greaterThan(0));
        assertThat(firstTopologyNoFilter.indexOf(initialAggregationRepartition), greaterThan(0));


        properties.put("add.filter", "true");
        topology = instance.buildTopology(properties);
        final String topologyWithFilter = topology.describe().toString();

        assertThat(topologyWithFilter.indexOf(initialStateStoreName), greaterThan(0));
        assertThat(topologyWithFilter.indexOf(initialAggregationRepartition), greaterThan(0));
    }
}

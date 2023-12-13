package io.confluent.developer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.util.Properties;
import org.apache.kafka.streams.Topology;
import org.junit.Before;
import org.junit.Test;


public class NamingChangelogAndRepartitionTopicsTest {

    private static final String TEST_CONFIG_FILE = "configuration/test.properties";
    private NamingChangelogAndRepartitionTopics namingChangelogAndRepartitionTopics;
    private Properties envProps;

    @Before
    public void setUp() throws IOException {
        namingChangelogAndRepartitionTopics = new NamingChangelogAndRepartitionTopics();
        envProps = namingChangelogAndRepartitionTopics.loadEnvProperties(TEST_CONFIG_FILE);
    }

    @Test
    public void shouldUpdateNamesOfStoresAndRepartitionTopics() {

        envProps.put("add.filter", "false");
        Topology topology = namingChangelogAndRepartitionTopics.buildTopology(envProps);

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

        envProps.put("add.filter", "true");
        topology = namingChangelogAndRepartitionTopics.buildTopology(envProps);
        final String topologyWithFilter = topology.describe().toString();

        assertThat(topologyWithFilter.indexOf(initialStateStoreName), is(-1));
        assertThat(topologyWithFilter.indexOf(initialAggregationRepartition), is(-1));
        assertThat(topologyWithFilter.indexOf(stateStoreNameWithFilterAdded), greaterThan(0));
        assertThat(topologyWithFilter.indexOf(aggregationRepartitionWithFilterAdded), greaterThan(0));
    }

    @Test
    public void shouldNotUpdateNamesOfStoresAndRepartitionTopics() {
        envProps.put("add.names", "true");

        Topology topology = namingChangelogAndRepartitionTopics.buildTopology(envProps);
        final String firstTopologyNoFilter = topology.describe().toString();

        final String initialStateStoreName = "the-counting-store";
        final String initialAggregationRepartition = "count-repartition";

        assertThat(firstTopologyNoFilter.indexOf(initialStateStoreName), greaterThan(0));
        assertThat(firstTopologyNoFilter.indexOf(initialAggregationRepartition), greaterThan(0));


        envProps.put("add.filter", "true");
        topology = namingChangelogAndRepartitionTopics.buildTopology(envProps);
        final String topologyWithFilter = topology.describe().toString();

        assertThat(topologyWithFilter.indexOf(initialStateStoreName), greaterThan(0));
        assertThat(topologyWithFilter.indexOf(initialAggregationRepartition), greaterThan(0));
    }
}

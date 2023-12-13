package io.confluent.developer;

import static org.junit.Assert.*;
import org.junit.Test;

public class VwapUdfTest {

    @Test
    public void testVwapAllInts() {
        assertEquals(100D,
                new VwapUdf().vwap(95, 100, 105, 100),
               0D);
    }
    @Test
    public void testVwap() {
        assertEquals(100D,
                new VwapUdf().vwap(95D, 100, 105D, 100),
                0D);
    }
}

package io.confluent.developer;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VwapUdfTest {
    @Test
    public void testVwap() {
        assertEquals(100D,
                new VwapUdf().vwap(95D, 100, 105D, 100),
                0D);
    }
}

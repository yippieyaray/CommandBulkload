// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: numeric beta comparison for migration boundaries.
package commandbulkload;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseVersionTest {
    @ParameterizedTest @CsvSource({"1.0.0-BETA.9,1.0.0-BETA.10,-1", "1.0.0-BETA.99,1.0.0,-1",
        "1.0.0,1.0.0,0", "1.0.0,1.0.1-BETA.1,-1", "1.9.0,1.10.0,-1", "2.0.0,1.99.99,1"})
    void ordersNumerically(String a, String b, int expected) {
        assertEquals(expected, Integer.signum(ReleaseVersion.parse(a).compareTo(ReleaseVersion.parse(b))));
    }
    @ParameterizedTest @ValueSource(strings = {"", "1", "1.0", "1.0.0-beta.1", "1.0.0-BETA.-1", "1.0.0-extra"})
    void rejectsInvalidVersions(String version) { assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse(version)); }
}

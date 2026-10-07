// SPDX-License-Identifier: GPL-3.0-or-later
// Added on 2026-10-07: independent numeric ordering of release and beta versions.
package commandbulkload;

import java.math.BigInteger;
import java.util.regex.Pattern;

record ReleaseVersion(BigInteger major, BigInteger minor, BigInteger patch, BigInteger beta)
        implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-BETA\\.([0-9]+))?");
    static ReleaseVersion parse(String value) {
        if (value == null) throw new IllegalArgumentException("Missing config-version.");
        var match = FORMAT.matcher(value);
        if (!match.matches()) throw new IllegalArgumentException("Version must be major.minor.patch[-BETA.number].");
        return new ReleaseVersion(new BigInteger(match.group(1)), new BigInteger(match.group(2)),
                new BigInteger(match.group(3)), match.group(4) == null ? null : new BigInteger(match.group(4)));
    }
    @Override public int compareTo(ReleaseVersion other) {
        int result = major.compareTo(other.major);
        if (result == 0) result = minor.compareTo(other.minor);
        if (result == 0) result = patch.compareTo(other.patch);
        if (result != 0) return result;
        if (beta == null) return other.beta == null ? 0 : 1;
        return other.beta == null ? -1 : beta.compareTo(other.beta);
    }
}

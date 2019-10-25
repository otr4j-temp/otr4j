/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.crypto.ed448;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public final class ScalarTestUtils {

    private ScalarTestUtils() {
        // No need to instantiate utility class.
    }

    @Nonnull
    public static Scalar fromBigInteger(final BigInteger value) {
        return Scalar.fromBigInteger(value);
    }
}

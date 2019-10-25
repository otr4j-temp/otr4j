/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.api;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Utilities for OtrPolicy.
 *
 * @author Danny van Heumen
 */
public final class OtrPolicys {

    private OtrPolicys() {
        // utility class, should not be instantiated
    }

    /**
     * Determine list of allowed OTR versions based on the provided OTR policy.
     *
     * @param policy The active OTR policy.
     * @return Returns list of allowed OTR versions.
     */
    @Nonnull
    public static Set<Integer> allowedVersions(final OtrPolicy policy) {
        final HashSet<Integer> versions = new HashSet<>();
        if (policy.isAllowV2()) {
            versions.add(Session.Version.TWO);
        }
        if (policy.isAllowV3()) {
            versions.add(Session.Version.THREE);
        }
        if (policy.isAllowV4()) {
            versions.add(Session.Version.FOUR);
        }
        return versions;
    }
}

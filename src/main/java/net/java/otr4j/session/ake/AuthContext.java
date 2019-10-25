/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.session.ake;

import net.java.otr4j.api.InstanceTag;
import net.java.otr4j.crypto.DSAKeyPair;

import javax.annotation.Nonnull;
import java.security.SecureRandom;

/**
 * Context required for authentication state implementations.
 *
 * @author Danny van Heumen
 */
public interface AuthContext {

    /**
     * Acquire SecureRandom instance.
     *
     * @return SecureRandom instance
     */
    @Nonnull
    SecureRandom secureRandom();

    /**
     * Get sender tag.
     *
     * @return Sender instance tag.
     */
    @Nonnull
    InstanceTag getSenderInstanceTag();

    /**
     * Get receiver tag.
     *
     * @return Receiver instance tag.
     */
    @Nonnull
    InstanceTag getReceiverInstanceTag();

    /**
     * Get local OTRv3 long-term DSA key pair.
     *
     * @return DSA key pair
     */
    @Nonnull
    DSAKeyPair getLocalKeyPair();

    /**
     * Update AKE state in context.
     *
     * @param state The new AKE state.
     */
    void setAuthState(AuthState state);
}

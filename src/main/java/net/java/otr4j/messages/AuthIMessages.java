/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.messages;

import net.java.otr4j.api.ClientProfile;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.crypto.ed448.Point;

import java.math.BigInteger;

import static net.java.otr4j.crypto.OtrCryptoEngine4.ringVerify;
import static net.java.otr4j.messages.MysteriousT4.Purpose.AUTH_I;
import static net.java.otr4j.messages.MysteriousT4.encode;
import static net.java.otr4j.messages.Validators.validateEquals;

/**
 * Utility class for AuthIMessage.
 */
public final class AuthIMessages {

    private AuthIMessages() {
        // No need to instantiate utility class.
    }

    /**
     * Validate an Auth-I message.
     *
     * @param message                    the Auth-I message to be validated
     * @param ourProfilePayload          our client profile (non-validated, as payload)
     * @param ourProfile                 our client profile
     * @param profileBobPayload          other party's client profile (as payload)
     * @param profileBob                 other party's client profile, validated
     * @param x                          ephemeral ECDH public key 'X'
     * @param y                          ephemeral ECDH public key 'Y'
     * @param a                          ephemeral DH public key 'A'
     * @param b                          ephemeral DH public key 'B'
     * @param senderAccountID            sender account ID
     * @param receiverAccountID          receiver account ID
     * @param senderFirstECDHPublicKey   the sender's first ECDH public key to use after DAKE completes
     * @param senderFirstDHPublicKey     the sender's first DH public key to use after DAKE completes
     * @param receiverFirstECDHPublicKey the receiver's first ECDH public key to use after DAKE completes
     * @param receiverFirstDHPublicKey   the receiver's first DH public key to use after DAKE completes
     * @throws ValidationException In case validation fails.
     */
    public static void validate(final AuthIMessage message, final ClientProfilePayload ourProfilePayload,
            final ClientProfile ourProfile, final ClientProfilePayload profileBobPayload, final ClientProfile profileBob,
            final Point x, final Point y, final BigInteger a, final BigInteger b, final Point senderFirstECDHPublicKey,
            final BigInteger senderFirstDHPublicKey, final Point receiverFirstECDHPublicKey,
            final BigInteger receiverFirstDHPublicKey, final String senderAccountID, final String receiverAccountID)
            throws ValidationException {
        validateEquals(message.senderTag, profileBob.getInstanceTag(), "Sender instance tag does not match with owner instance tag in client profile.");
        // We don't do extra verification of points here, as these have been verified upon receiving the Identity
        // message. This was the previous message that was sent. So we can assume points are trustworthy.
        final byte[] t = encode(AUTH_I, ourProfilePayload, profileBobPayload, x, y, a, b,
                senderFirstECDHPublicKey, senderFirstDHPublicKey, receiverFirstECDHPublicKey, receiverFirstDHPublicKey,
                message.senderTag, message.receiverTag, senderAccountID, receiverAccountID);
        try {
            ringVerify(profileBob.getLongTermPublicKey(), ourProfile.getForgingKey(), x, message.sigma, t);
        } catch (final OtrCryptoException e) {
            throw new ValidationException("Ring signature verification failed.", e);
        }
    }
}

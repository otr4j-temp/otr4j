package net.java.otr4j.io.messages;

import net.java.otr4j.api.InstanceTag;
import net.java.otr4j.api.Session;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.profile.ClientProfile;
import nl.dannyvanheumen.joldilocks.Point;

import javax.annotation.Nonnull;
import java.math.BigInteger;

import static net.java.otr4j.crypto.DHKeyPairs.verifyDHPublicKey;
import static net.java.otr4j.crypto.ECDHKeyPairs.verifyECDHPublicKey;
import static net.java.otr4j.crypto.OtrCryptoEngine4.ringVerify;

/**
 * Utility class for AuthRMessage. (Auth-R messages)
 */
// FIXME write unit tests
public final class AuthRMessages {

    private AuthRMessages() {
        // No need to instantiate utility class.
    }

    /**
     * Validate an AuthRMessage, using additional parameters to provide required data.
     *
     * @param message                 the AUTH_R message
     * @param ourClientProfilePayload our ClientProfile instance
     * @param senderTag               the sender's instance tag
     * @param receiverTag             the receiver's instance tag
     * @param senderAccountID         the sender's account ID
     * @param receiverAccountID       the Receiver's account ID
     * @param receiverECDHPublicKey   the receiver's ECDH public key
     * @param receiverDHPublicKey     the receiver's DH public key
     * @param queryTag                the query tag
     * @throws ClientProfilePayload.ValidationException In case user profile validation has failed.
     * @throws OtrCryptoException                             In case any cryptographic verification failed, such as ephemeral
     *                                                        public keys or the ring signature.
     */
    public static void validate(@Nonnull final AuthRMessage message, @Nonnull final ClientProfilePayload ourClientProfilePayload,
                                @Nonnull final InstanceTag senderTag, @Nonnull final InstanceTag receiverTag,
                                @Nonnull final String senderAccountID, @Nonnull final String receiverAccountID,
                                @Nonnull final Point receiverECDHPublicKey, @Nonnull final BigInteger receiverDHPublicKey,
                                @Nonnull final String queryTag) throws OtrCryptoException, ClientProfilePayload.ValidationException {
        if (message.getType() != AuthRMessage.MESSAGE_AUTH_R) {
            throw new IllegalStateException("Auth-R message should not have any other type than 0x91.");
        }
        if (message.protocolVersion != Session.OTRv.FOUR) {
            throw new IllegalStateException("Auth-R message should not have any other protocol version than 4.");
        }
        // FIXME Check that the receiver's instance tag matches your sender's instance tag. (Really needed? I would expect this to happen earlier.)
        // FIXME fix validation, this is non-functional work-around code.
        final ClientProfile theirProfile = message.getClientProfile().validate();
//        ClientProfiles.validate(message.getClientProfile());
        verifyECDHPublicKey(message.getX());
        verifyDHPublicKey(message.getA());
        final byte[] t = MysteriousT4.encode(message.getClientProfile(), ourClientProfilePayload, message.getX(),
            receiverECDHPublicKey, message.getA(), receiverDHPublicKey, senderTag, receiverTag, queryTag,
            senderAccountID, receiverAccountID);
        // FIXME fix validation, this is non-functional work-around code.
        final ClientProfile ourClientProfile = ourClientProfilePayload.validate();
        // "Verify the sigma with Ring Signature Authentication, that is sigma == RVrf({H_b, H_a, Y}, t)."
        ringVerify(ourClientProfile.getLongTermPublicKey(), theirProfile.getLongTermPublicKey(),
            receiverECDHPublicKey, message.getSigma(), t);
    }
}
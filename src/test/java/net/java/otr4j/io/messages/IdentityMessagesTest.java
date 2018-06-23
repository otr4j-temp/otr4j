package net.java.otr4j.io.messages;

import net.java.otr4j.api.InstanceTag;
import net.java.otr4j.crypto.DHKeyPair;
import net.java.otr4j.crypto.ECDHKeyPair;
import net.java.otr4j.crypto.EdDSAKeyPair;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.profile.ClientProfile;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Collections;

import static net.java.otr4j.io.messages.IdentityMessages.validate;

@SuppressWarnings("ConstantConditions")
public final class IdentityMessagesTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EdDSAKeyPair longTermKeyPair = EdDSAKeyPair.generate(RANDOM);
    private final ECDHKeyPair ecdhKeyPair = ECDHKeyPair.generate(RANDOM);
    private final DHKeyPair dhKeyPair = DHKeyPair.generate(RANDOM);

    @Test(expected = NullPointerException.class)
    public void testValidateNull() throws OtrCryptoException, IdentityMessages.ValidationException {
        validate(null);
    }

    @Test
    public void testValidateIdentity() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(4, InstanceTag.SMALLEST_VALUE,
            InstanceTag.HIGHEST_VALUE, profilePayload, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey());
        validate(message);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateIdentityBadProtocolVersion() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(3, InstanceTag.SMALLEST_VALUE,
            InstanceTag.HIGHEST_VALUE, profilePayload, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey());
        validate(message);
    }

    // TODO should instance tag be verified here or earlier in the process?
    @Test(expected = IdentityMessages.ValidationException.class)
    public void testValidateIdentityBadSenderInstanceTag() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(4, 3, InstanceTag.HIGHEST_VALUE,
            profilePayload, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey());
        validate(message);
    }

    // TODO should instance tag be verified here or earlier in the process?
    @Test(expected = IdentityMessages.ValidationException.class)
    public void testValidateIdentityBadReceiverInstanceTag() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(4, InstanceTag.SMALLEST_VALUE, 5,
            profilePayload, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey());
        validate(message);
    }

    @Test(expected = NullPointerException.class)
    public void testValidateIdentityNullClientProfile() throws OtrCryptoException, IdentityMessages.ValidationException {
        final IdentityMessage message = new IdentityMessage(4, InstanceTag.HIGHEST_VALUE,
            InstanceTag.SMALLEST_VALUE, null, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey());
        validate(message);
    }

    @Test(expected = NullPointerException.class)
    public void testValidateIdentityNullEcdhPublicKey() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(4, InstanceTag.HIGHEST_VALUE,
            InstanceTag.SMALLEST_VALUE, profilePayload, null, dhKeyPair.getPublicKey());
        validate(message);
    }

    @Test(expected = NullPointerException.class)
    public void testValidateIdentityNullDhPublicKey() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(4, InstanceTag.HIGHEST_VALUE,
            InstanceTag.SMALLEST_VALUE, profilePayload, ecdhKeyPair.getPublicKey(), null);
        validate(message);
    }

    @Test(expected = IdentityMessages.ValidationException.class)
    public void testValidateIdentityInconsistentInstanceTag() throws OtrCryptoException, IdentityMessages.ValidationException {
        final ClientProfile profile = new ClientProfile(InstanceTag.SMALLEST_VALUE, longTermKeyPair.getPublicKey(),
            Collections.singleton(4), System.currentTimeMillis() / 1000 + 86400);
        final ClientProfilePayload profilePayload = ClientProfilePayload.sign(profile, null, longTermKeyPair);
        final IdentityMessage message = new IdentityMessage(4, InstanceTag.HIGHEST_VALUE,
            InstanceTag.SMALLEST_VALUE, profilePayload, ecdhKeyPair.getPublicKey(), dhKeyPair.getPublicKey());
        validate(message);
    }
}

/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package net.java.otr4j.messages;

import net.java.otr4j.api.InstanceTag;
import net.java.otr4j.api.Session;
import net.java.otr4j.api.Session.Version;
import net.java.otr4j.io.OtrEncodable;
import net.java.otr4j.io.OtrOutputStream;

import javax.crypto.interfaces.DHPublicKey;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static net.java.otr4j.messages.DataMessage.MESSAGE_DATA;
import static net.java.otr4j.util.ByteArrays.constantTimeEquals;

/**
 * MysteriousT represents the T_a composite as described in "Exchanging Data" section.
 */
public final class MysteriousT implements OtrEncodable {

    /**
     * The protocol version.
     */
    public final int protocolVersion;
    /**
     * The sender instance tag.
     */
    public final InstanceTag senderInstanceTag;
    /**
     * The receiver instance tag.
     */
    public final InstanceTag receiverInstanceTag;
    /**
     * The message type.
     */
    public final int messageType;
    /**
     * The message flags.
     */
    public final byte flags;
    /**
     * The sender key ID.
     */
    public final int senderKeyID;
    /**
     * The receiver key ID.
     */
    public final int recipientKeyID;
    /**
     * The next DH public key.
     */
    public final DHPublicKey nextDH;
    /**
     * The counter value used in the message.
     */
    public final byte[] ctr;
    /**
     * The encrypted message content.
     */
    public final byte[] encryptedMessage;

    /**
     * Constructor for MysteriousT.
     *
     * @param protocolVersion     the protocol version
     * @param senderInstanceTag   the sender instance tag
     * @param receiverInstanceTag the receiver instance tag
     * @param flags               message flags
     * @param senderKeyID         the sender key ID
     * @param recipientKeyID      the receiver key ID
     * @param nextDH              the next DH public key
     * @param ctr                 the counter value used in the message
     * @param encryptedMessage    the encrypted message content
     */
    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    public MysteriousT(final int protocolVersion, final InstanceTag senderInstanceTag,
            final InstanceTag receiverInstanceTag, final byte flags, final int senderKeyID, final int recipientKeyID,
            final DHPublicKey nextDH, final byte[] ctr, final byte[] encryptedMessage) {
        if (protocolVersion < Session.Version.TWO || protocolVersion > Session.Version.THREE) {
            throw new IllegalArgumentException("Illegal protocol version specified.");
        }
        this.protocolVersion = protocolVersion;
        this.senderInstanceTag = requireNonNull(senderInstanceTag);
        this.receiverInstanceTag = requireNonNull(receiverInstanceTag);
        this.messageType = MESSAGE_DATA;
        this.flags = flags;
        this.senderKeyID = senderKeyID;
        this.recipientKeyID = recipientKeyID;
        this.nextDH = nextDH;
        this.ctr = ctr;
        this.encryptedMessage = encryptedMessage;
    }

    // Methods.
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(ctr);
        result = prime * result + Arrays.hashCode(encryptedMessage);
        result = prime * result + flags;
        result = prime * result + messageType;
        result = prime * result + nextDH.hashCode();
        result = prime * result + protocolVersion;
        result = prime * result + recipientKeyID;
        result = prime * result + senderKeyID;
        result = prime * result + senderInstanceTag.hashCode();
        result = prime * result + receiverInstanceTag.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MysteriousT other = (MysteriousT) obj;
        if (!constantTimeEquals(ctr, other.ctr)) {
            return false;
        }
        if (!constantTimeEquals(encryptedMessage, other.encryptedMessage)) {
            return false;
        }
        if (flags != other.flags) {
            return false;
        }
        if (messageType != other.messageType) {
            return false;
        }
        if (!nextDH.equals(other.nextDH)) {
            return false;
        }
        if (protocolVersion != other.protocolVersion) {
            return false;
        }
        if (recipientKeyID != other.recipientKeyID) {
            return false;
        }
        if (senderKeyID != other.senderKeyID) {
            return false;
        }
        if (!senderInstanceTag.equals(other.senderInstanceTag)) {
            return false;
        }
        return receiverInstanceTag.equals(other.receiverInstanceTag);
    }

    @Override
    public void writeTo(final OtrOutputStream out) {
        out.writeShort(this.protocolVersion);
        out.writeByte(this.messageType);
        if (this.protocolVersion == Version.THREE) {
            out.writeInstanceTag(this.senderInstanceTag);
            out.writeInstanceTag(this.receiverInstanceTag);
        }
        out.writeByte(this.flags);
        out.writeInt(this.senderKeyID);
        out.writeInt(this.recipientKeyID);
        out.writeDHPublicKey(this.nextDH);
        out.writeCtr(this.ctr);
        out.writeData(this.encryptedMessage);
    }
}

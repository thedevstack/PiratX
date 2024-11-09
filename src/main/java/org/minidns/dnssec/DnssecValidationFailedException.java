/*
 * Copyright 2015-2024 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package org.minidns.dnssec;

import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.Data;
import org.minidns.record.DelegatingDnssecRR;
import org.minidns.record.Record;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Locale;

public class DnssecValidationFailedException extends IOException {
    private static final long serialVersionUID = 5413184667629832742L;

    public DnssecValidationFailedException(Question question, String reason) {
        super("Validation of request to " + question + " failed: " + reason);
    }

    public DnssecValidationFailedException(String message) {
        super(message);
    }

    public DnssecValidationFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public DnssecValidationFailedException(Record<? extends Data> record, String reason) {
        super("Validation of record " + record + " failed: " + reason);
    }

    public DnssecValidationFailedException(List<Record<? extends Data>> records, String reason) {
        super("Validation of " + records.size() + " " + records.get(0).type + " record" + (records.size() > 1 ? "s" : "") + " failed: " + reason);
    }

    public static class DataMalformedException extends DnssecValidationFailedException {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        private final byte[] data;

        public DataMalformedException(IOException exception, byte[] data) {
            super("Malformed data", exception);
            this.data = data;
        }

        public DataMalformedException(String message, IOException exception, byte[] data) {
            super(message, exception);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class DnssecInvalidKeySpecException extends DnssecValidationFailedException {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public DnssecInvalidKeySpecException(InvalidKeySpecException exception) {
            super("Invalid key spec", exception);
        }

        public DnssecInvalidKeySpecException(String message, InvalidKeySpecException exception, byte[] data) {
            super(message, exception);
        }

    }

    public static class AuthorityDoesNotContainSoa extends DnssecValidationFailedException {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private final DnsMessage response;

        public AuthorityDoesNotContainSoa(DnsMessage response) {
            super("Autority does not contain SOA");
            this.response = response;
        }

        public DnsMessage getResponse() {
            return response;
        }
    }

    public static final class DigestComparisonFailedException extends DnssecValidationFailedException {

        /**
        *
        */
       private static final long serialVersionUID = 1L;

       private final Record<? extends Data> record;
       private final DelegatingDnssecRR ds;
       private final byte[] digest;
       private final String digestHex;

       private DigestComparisonFailedException(String message, Record<? extends Data> record, DelegatingDnssecRR ds, byte[] digest, String digestHex) {
           super(message);
           this.record = record;
           this.ds = ds;
           this.digest = digest;
           this.digestHex = digestHex;
       }

       public Record<? extends Data> getRecord() {
           return record;
       }

       public DelegatingDnssecRR getDelegaticDnssecRr() {
           return ds;
       }

       public byte[] getDigest() {
           return digest.clone();
       }

       public String getDigestHex() {
           return digestHex;
       }

       public static DigestComparisonFailedException from(Record<? extends Data> record, DelegatingDnssecRR ds, byte[] digest) {
           BigInteger digestBigInteger = new BigInteger(1, digest);
           String digestHex = digestBigInteger.toString(16).toUpperCase(Locale.ROOT);

           String message = "Digest for " + record + " does not match. Digest of delegating DNSSEC RR " + ds + " is '"
                   + ds.getDigestHex() + "' while we calculated '" + digestHex + "'";
           return new DigestComparisonFailedException(message, record, ds, digest, digestHex);
       }
    }
}

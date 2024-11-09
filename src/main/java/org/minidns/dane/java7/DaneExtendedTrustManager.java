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
package org.minidns.dane.java7;

import org.minidns.dane.DaneVerifier;
import org.minidns.dane.X509TrustManagerUtil;
import org.minidns.dnssec.DnssecClient;
import org.minidns.util.InetAddressUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

public class DaneExtendedTrustManager extends X509ExtendedTrustManager {
    private static final Logger LOGGER = Logger.getLogger(DaneExtendedTrustManager.class.getName());

    private final X509TrustManager base;
    private final DaneVerifier verifier;

    public static void inject() {
        inject(new DaneExtendedTrustManager());
    }

    public static void inject(DaneExtendedTrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustManager}, null);
            SSLContext.setDefault(sslContext);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public DaneExtendedTrustManager() {
        this(X509TrustManagerUtil.getDefault());
    }

    public DaneExtendedTrustManager(DnssecClient client) {
        this(client, X509TrustManagerUtil.getDefault());
    }

    public DaneExtendedTrustManager(X509TrustManager base) {
        this(new DaneVerifier(), base);
    }

    public DaneExtendedTrustManager(DnssecClient client, X509TrustManager base) {
        this(new DaneVerifier(client), base);
    }

    public DaneExtendedTrustManager(DaneVerifier verifier, X509TrustManager base) {
        this.verifier = verifier;
        this.base = base;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        if (base == null) {
            LOGGER.warning("DaneExtendedTrustManager invalidly used for client certificate check and no fallback X509TrustManager specified");
            return;
        }

        LOGGER.info("DaneExtendedTrustManager invalidly used for client certificate check forwarding request to fallback X509TrustManage");
        if (base instanceof X509ExtendedTrustManager) {
            ((X509ExtendedTrustManager) base).checkClientTrusted(chain, authType, socket);
        } else {
            base.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        boolean verificationSuccessful = false;

        if (socket instanceof SSLSocket) {
            final SSLSocket sslSocket = (SSLSocket) socket;
            final String hostname = sslSocket.getHandshakeSession().getPeerHost();

            if (hostname == null) {
                LOGGER.warning("Hostname returned by sslSocket.getHandshakeSession().getPeerHost() is null");
            } else if (InetAddressUtil.isIpAddress(hostname)) {
                LOGGER.warning(
                        "Hostname returned by sslSocket.getHandshakeSession().getPeerHost() '" + hostname
                                + "' is an IP address");
            } else {
                final int port = socket.getPort();
                verificationSuccessful = verifier.verifyCertificateChain(chain, hostname, port);
            }
        } else {
            throw new IllegalStateException("The provided socket '" + socket + "' is not of type SSLSocket");
        }

        if (verificationSuccessful) {
            // Verification successful, no need to delegate to base trust manager.
            return;
        }

        if (base instanceof X509ExtendedTrustManager) {
            ((X509ExtendedTrustManager) base).checkServerTrusted(chain, authType, socket);
        } else {
            base.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        if (base == null) {
            LOGGER.warning("DaneExtendedTrustManager invalidly used for client certificate check and no fallback X509TrustManager specified");
            return;
        }

        LOGGER.info("DaneExtendedTrustManager invalidly used for client certificate check, forwarding request to fallback X509TrustManage");
        if (base instanceof X509ExtendedTrustManager) {
            ((X509ExtendedTrustManager) base).checkClientTrusted(chain, authType, engine);
        } else {
            base.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        if (verifier.verifyCertificateChain(chain, engine.getPeerHost(), engine.getPeerPort())) {
            // Verification successful, no need to delegate to base trust manager.
            return;
        }

        if (base instanceof X509ExtendedTrustManager) {
            ((X509ExtendedTrustManager) base).checkServerTrusted(chain, authType, engine);
        } else {
            base.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (base == null) {
            LOGGER.warning("DaneExtendedTrustManager invalidly used for client certificate check and no fallback X509TrustManager specified");
            return;
        }

        LOGGER.info("DaneExtendedTrustManager invalidly used for client certificate check, forwarding request to fallback X509TrustManage");
        base.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        LOGGER.info("DaneExtendedTrustManager cannot be used without hostname information, forwarding request to fallback X509TrustManage");
        base.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return base.getAcceptedIssuers();
    }
}

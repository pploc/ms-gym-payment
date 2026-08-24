package com.gym.payment.config;

import io.grpc.Grpc;
import io.grpc.ServerCall;

import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

final class PeerCertificateIdentity {

    private PeerCertificateIdentity() {}

    static boolean hasAllowedSan(ServerCall<?, ?> call, Set<String> allowedSans) {
        SSLSession sslSession = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        if (sslSession == null || allowedSans == null || allowedSans.isEmpty()) {
            return false;
        }
        try {
            var certificates = sslSession.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) {
                return false;
            }
            var subjectAlternativeNames = certificate.getSubjectAlternativeNames();
            if (subjectAlternativeNames == null) {
                return false;
            }
            for (List<?> san : subjectAlternativeNames) {
                if (san.size() >= 2
                        && san.get(0) instanceof Integer type
                        && (type == 2 || type == 6)
                        && san.get(1) instanceof String value
                        && allowedSans.contains(value)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}

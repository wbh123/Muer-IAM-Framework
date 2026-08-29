package com.wust.iam.persistence;

import com.wust.iam.core.model.IamPrincipal;
import com.wust.iam.session.TokenRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;

final class TokenRecordCodec {
    String encode(TokenRecord record) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeUTF(record.sessionId());
                var principal = record.principal();
                output.writeLong(principal.userId());
                output.writeUTF(principal.identityId());
                output.writeUTF(principal.identityDomain());
                writeNullableLong(output, principal.activeProfileId());
                writeNullableLong(output, principal.templateVersionId());
                output.writeUTF(principal.clientType());
                output.writeLong(principal.authorizationVersion());
                output.writeLong(record.expiresAt().toEpochMilli());
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("unable to encode token record", exception);
        }
    }

    TokenRecord decode(String encoded) {
        try (var input = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            String sessionId = input.readUTF();
            var principal = new IamPrincipal(input.readLong(), input.readUTF(), input.readUTF(),
                    readNullableLong(input), readNullableLong(input), input.readUTF(), input.readLong());
            return new TokenRecord(sessionId, principal, Instant.ofEpochMilli(input.readLong()));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("unable to decode token record", exception);
        }
    }

    private static void writeNullableLong(DataOutputStream output, Long value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) output.writeLong(value);
    }

    private static Long readNullableLong(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readLong() : null;
    }
}

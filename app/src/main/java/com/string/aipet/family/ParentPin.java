package com.string.aipet.family;

import java.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class ParentPin {
    private ParentPin() {}
    public static void set(FamilyState state, String pin) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        state.pinSalt = Base64.getEncoder().encodeToString(salt);
        state.pinHash = hash(pin, salt);
    }
    public static boolean verify(FamilyState state, String pin) {
        return MessageDigest.isEqual(state.pinHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                hash(pin, Base64.getDecoder().decode(state.pinSalt)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String hash(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120_000, 256);
        try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        catch (Exception e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
}

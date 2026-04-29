package sn.mixx.expresso.service.bureau;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement AES-256-GCM pour les codes PIN/MPIN.
 * Format de stockage: Base64(IV[12 bytes] || CipherText + AuthTag)
 */
@Slf4j
@Service
public class MpinEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int AUTH_TAG_BIT_LENGTH = 128;
    private static final int KEY_LENGTH = 32;

    private final byte[] key;

    public MpinEncryptionService(@Value("${mpin.encryption.secret}") String base64Secret) {
        byte[] decoded = Base64.getDecoder().decode(base64Secret);
        if (decoded.length < KEY_LENGTH) {
            throw new IllegalArgumentException("La clé AES-256 doit faire au minimum 32 octets");
        }
        this.key = Arrays.copyOf(decoded, KEY_LENGTH);
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(AUTH_TAG_BIT_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] combined = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chiffrement", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;

        if (encryptedText.matches("\\d{4,6}")) {
            log.debug("PIN en clair détecté (migration), retour tel quel");
            return encryptedText;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(AUTH_TAG_BIT_LENGTH, iv));
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du déchiffrement. Vérifiez la clé de chiffrement.", e);
        }
    }
}
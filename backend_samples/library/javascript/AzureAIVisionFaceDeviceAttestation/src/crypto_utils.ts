import crypto from 'crypto';
import { trackException } from './logging';

/**
 * Tink ECIES format: single base64-encoded blob containing
 * [ephemeral_public_key_point || ciphertext+auth_tag]
 * This matches Google Tink's EciesAeadHkdfHybridEncrypt serialization
 */
export type TinkECIESCiphertext = string; // Base64-encoded Tink ECIES blob

/**
 * Generate P-256 (secp256r1) EC key pair for server-side encryption
 * @returns Object with publicKey and privateKey in PEM format, or null on error
 */
export function generateServerKeyPairEC(): { publicKey: string; privateKey: string } | null {
  try {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', {
      namedCurve: 'prime256v1',
      publicKeyEncoding: {
        type: 'spki',
        format: 'pem',
      },
      privateKeyEncoding: {
        type: 'pkcs8',
        format: 'pem',
      },
    });

    return { publicKey, privateKey };
  } catch (error) {
    trackException(error, { source: 'generateServerKeyPairEC' });
    return null;
  }
}

/**
 * Verify ECDSA signature with SHA256
 * @param data - Original string data
 * @param signature - Base64-encoded signature
 * @param publicKeyPem - EC public key in PEM format
 * @returns True if signature is valid, false otherwise
 */
export function verifySignatureEC(data: string, signature: string, publicKeyPem: string): boolean {
  try {
    const isValid = crypto.verify(
      'sha256',
      Buffer.from(data, 'utf8'),
      {
        key: publicKeyPem,
        dsaEncoding: 'der',
      },
      Buffer.from(signature, 'base64')
    );
    return isValid;
  } catch (error) {
    trackException(error, { source: 'verifySignatureEC' });
    return false;
  }
}

/**
 * Encrypt data using ECIES in Tink format (ECDH + HKDF + AES-256-GCM)
 * Returns Tink-compatible serialization: ephemeral_public_key_point || iv || ciphertext || auth_tag
 * @param data - String data to encrypt
 * @param publicKeyPem - Recipient's EC public key in PEM format
 * @returns Base64-encoded Tink ECIES blob or null on error
 */
export function encryptWithPublicKeyEC(data: string, publicKeyPem: string): TinkECIESCiphertext | null {
  try {
    // 1. Generate ephemeral keypair
    const ephemeral = generateServerKeyPairEC();
    if (!ephemeral) return null;

    // 2. Load keys as KeyObjects
    const ephemeralPrivateKeyObj = crypto.createPrivateKey(ephemeral.privateKey);
    const recipientPublicKeyObj = crypto.createPublicKey(publicKeyPem);

    // 3. Extract ephemeral public key as uncompressed point (65 bytes: 0x04 || X || Y)
    const ephemeralPublicKeyObj = crypto.createPublicKey(ephemeral.publicKey);
    const ephemeralJwk = ephemeralPublicKeyObj.export({ format: 'jwk' });
    if (ephemeralJwk.kty !== 'EC' || ephemeralJwk.crv !== 'P-256' || !ephemeralJwk.x || !ephemeralJwk.y) {
      throw new Error('Invalid ephemeral public key point format');
    }
    const ephemeralPoint = Buffer.concat([
      Buffer.from([0x04]), Buffer.from(ephemeralJwk.x, 'base64url'), Buffer.from(ephemeralJwk.y, 'base64url'),
    ]);

    // 4. Compute shared secret using modern diffieHellman API
    const sharedSecret = crypto.diffieHellman({
      privateKey: ephemeralPrivateKeyObj,
      publicKey: recipientPublicKeyObj,
    });

    // 5. Derive AES-256 key using HKDF-SHA256 with empty salt (Tink format)
    // Tink uses: HKDF(hash=SHA256, ikm=ephemeralPoint||sharedSecret, salt=empty, info=empty, length=32)
    // Following Shoup's recommendation: include ephemeral public key in KDF
    const ikm = Buffer.concat([ephemeralPoint, sharedSecret]);
    const aesKey = Buffer.from(crypto.hkdfSync('sha256', ikm, Buffer.alloc(0), Buffer.alloc(0), 32));

    // 6. Generate random IV (12 bytes for GCM)
    const iv = crypto.randomBytes(12);

    // 7. Encrypt with AES-256-GCM
    const cipher = crypto.createCipheriv('aes-256-gcm', aesKey, iv);
    const encrypted = Buffer.concat([cipher.update(data, 'utf8'), cipher.final()]);
    const authTag = cipher.getAuthTag();

    // 8. Serialize in Tink format: ephemeral_point || iv || ciphertext || authTag
    const tinkBlob = Buffer.concat([ephemeralPoint, iv, encrypted, authTag]);

    return tinkBlob.toString('base64');
  } catch (error) {
    trackException(error, { source: 'encryptWithPublicKeyEC' });
    return null;
  }
}

/**
 * Decrypt data using ECIES in Tink format (ECDH + HKDF + AES-256-GCM)
 * Expects Tink serialization: ephemeral_public_key_point || iv || ciphertext || auth_tag
 * @param tinkCiphertext - Base64-encoded Tink ECIES blob
 * @param privateKeyPem - Recipient's EC private key in PEM format
 * @returns Decrypted string or null on error
 */
export function decryptWithPrivateKeyEC(tinkCiphertext: TinkECIESCiphertext, privateKeyPem: string): string | null {
  try {
    // 1. Decode Tink blob from base64
    const tinkBlob = Buffer.from(tinkCiphertext, 'base64');

    // 2. Parse Tink format: ephemeral_point(65) || iv(12) || ciphertext || authTag(16)
    if (tinkBlob.length < 65 + 12 + 16) {
      throw new Error('Tink blob too short');
    }

    const ephemeralPoint = tinkBlob.subarray(0, 65);
    const iv = tinkBlob.subarray(65, 65 + 12);
    const ciphertextWithTag = tinkBlob.subarray(65 + 12);
    const authTag = ciphertextWithTag.subarray(-16);
    const ciphertext = ciphertextWithTag.subarray(0, -16);

    // Validate ephemeral point format
    if (ephemeralPoint[0] !== 0x04) {
      throw new Error('Invalid ephemeral public key point format');
    }

    const privateKeyObj = crypto.createPrivateKey(privateKeyPem);
    const ephemeralPublicKeyObj = crypto.createPublicKey({
      key: {
        kty: 'EC', crv: 'P-256',
        x: ephemeralPoint.subarray(1, 33).toString('base64url'),
        y: ephemeralPoint.subarray(33, 65).toString('base64url'),
      },
      format: 'jwk',
    });

    // 5. Compute shared secret using modern diffieHellman API
    const sharedSecret = crypto.diffieHellman({
      privateKey: privateKeyObj,
      publicKey: ephemeralPublicKeyObj,
    });

    // 6. Derive same AES-256 key using HKDF-SHA256 with empty salt (Tink format)
    // Tink uses: HKDF(hash=SHA256, ikm=ephemeralPoint||sharedSecret, salt=empty, info=empty, length=32)
    // Following Shoup's recommendation: include ephemeral public key in KDF
    const ikm = Buffer.concat([ephemeralPoint, sharedSecret]);
    const aesKey = Buffer.from(crypto.hkdfSync('sha256', ikm, Buffer.alloc(0), Buffer.alloc(0), 32));

    // 7. Decrypt with AES-256-GCM
    const decipher = crypto.createDecipheriv('aes-256-gcm', aesKey, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);

    return decrypted.toString('utf8');
  } catch (error) {
    trackException(error, { source: 'decryptWithPrivateKeyEC' });
    return null;
  }
}


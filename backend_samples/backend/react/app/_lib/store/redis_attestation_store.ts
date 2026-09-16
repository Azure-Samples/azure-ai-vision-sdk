import { randomUUID } from 'node:crypto';
import { createRedisClient, trackRedisOp } from './redis_client';
import { trackException } from '../app_insights_server';
import type { ClusterStore, SessionRecord, CertificateData, Snapshot, UpdateResult } from '@azure/ai-vision-face-deviceattestation';
import { StorageError } from '@azure/ai-vision-face-deviceattestation';

const CAS_SCRIPT = `
local current = redis.call('GET', KEYS[1])
if not current or redis.call('PTTL', KEYS[1]) <= 0 then return 'missingOrExpired' end
if cjson.decode(current).version ~= ARGV[1] then return 'conflict' end
redis.call('SET', KEYS[1], ARGV[2], 'XX', 'KEEPTTL')
return 'applied'
`;

type RedisClient = NonNullable<Awaited<ReturnType<typeof createRedisClient>>>;

export class RedisAttestationStore implements ClusterStore {
  getSession(sid: string): Promise<Snapshot<SessionRecord> | null> {
    return this.read(`attestation:v2/${sid}`);
  }
  setSession(sid: string, record: SessionRecord): Promise<boolean> {
    return this.create(`attestation:v2/${sid}`, record, Number(process.env.SESSION_TOKEN_TTL || '600'));
  }
  updateSession(sid: string, version: string, record: SessionRecord): Promise<UpdateResult> {
    return this.update(`attestation:v2/${sid}`, version, record);
  }
  getCertificate(thumbprint: string): Promise<Snapshot<CertificateData> | null> {
    return this.read(`cert_thumb:v2:${thumbprint}`);
  }
  setCertificate(thumbprint: string, record: CertificateData): Promise<boolean> {
    return this.create(`cert_thumb:v2:${thumbprint}`, record, Number(process.env.CERT_TTL || '604800'));
  }
  updateCertificate(thumbprint: string, version: string, record: CertificateData): Promise<UpdateResult> {
    return this.update(`cert_thumb:v2:${thumbprint}`, version, record);
  }
  private read<T>(key: string): Promise<Snapshot<T> | null> {
    return this.execute('GET', key, async client => {
      const raw = await client.get(key);
      if (!raw) return null;
      const snapshot = JSON.parse(raw) as Snapshot<T>;
      if (typeof snapshot.version !== 'string' || !snapshot.version || !snapshot.value) {
        throw new Error('Invalid versioned attestation record');
      }
      return snapshot;
    });
  }
  private create<T>(key: string, value: T, ttl: number): Promise<boolean> {
    return this.execute('SET NX', key, async client => {
      if (!Number.isSafeInteger(ttl) || ttl <= 0) throw new Error('Invalid attestation TTL');
      return (await client.set(key, JSON.stringify({ value, version: randomUUID() }), { NX: true, EX: ttl })) === 'OK';
    });
  }
  private update<T>(key: string, expectedVersion: string, value: T): Promise<UpdateResult> {
    return this.execute('EVAL CAS', key, async client => {
      const result = await client.eval(CAS_SCRIPT, {
        keys: [key], arguments: [expectedVersion, JSON.stringify({ value, version: randomUUID() })],
      });
      if (result !== 'applied' && result !== 'conflict' && result !== 'missingOrExpired') throw new Error('Unexpected CAS result');
      return result;
    });
  }
  private async execute<T>(command: string, key: string, action: (client: RedisClient) => Promise<T>): Promise<T> {
    const start = Date.now();
    let client: RedisClient | null = null;
    try {
      client = await createRedisClient();
      if (!client) throw new StorageError('Attestation storage unavailable');
      client.on('error', error => trackException(error, { source: 'attestationStore', command }));
      await client.connect();
      const result = await action(client);
      trackRedisOp({ command, key, duration: Date.now() - start, success: true });
      return result;
    } catch (error) {
      trackRedisOp({ command, key, duration: Date.now() - start, success: false });
      trackException(error, { source: 'attestationStore', command });
      throw new StorageError('Attestation storage unavailable', { cause: error });
    } finally {
      if (client?.isOpen) {
        try { await client.disconnect(); } catch (error) {
          trackException(error, { source: 'attestationStore.disconnect' });
        }
      }
    }
  }
}

let defaultStore: ClusterStore | null = null;
export function getAttestationStore(): ClusterStore {
  return defaultStore ??= new RedisAttestationStore();
}
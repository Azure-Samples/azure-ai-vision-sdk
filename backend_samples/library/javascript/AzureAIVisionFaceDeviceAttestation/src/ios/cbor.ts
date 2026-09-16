import { Decoder } from 'cbor';
import type { CborValue } from './types';

/** Decode one App Attest CBOR item and return its ending offset. */
export function decodeCbor(buf: Buffer, pos = 0, depth = 0): [CborValue, number] {
  if (!Number.isInteger(pos) || pos < 0 || pos >= buf.length) throw new Error('CBOR: invalid offset');
  if (depth > 16) throw new Error('CBOR: max nesting depth exceeded');
  const options = { preferMap: true, max_depth: 16 - depth, extendedResults: true };
  const decoder = new Decoder(options);
  let result: { value: unknown; length: number } | undefined;
  decoder.on('error', () => {});
  decoder.on('start', (major: number, length: unknown) => {
    if (major === 6 || typeof length === 'symbol') throw new Error('CBOR: tags and indefinite encodings are unsupported');
  });
  decoder.on('more-bytes', (major: number) => {
    if (major === 7) throw new Error('CBOR: simple values and floats are unsupported');
  });
  decoder.on('value', validateValue);
  decoder.on('data', value => {
    result = value;
    decoder.close();
  });
  try {
    decoder.write(buf.subarray(pos));
    if (!result) throw new Error('CBOR: malformed or unsupported input');
    return [validateValue(result.value), pos + result.length];
  } finally {
    decoder.destroy();
  }
}

function validateValue(value: unknown): CborValue {
  if (typeof value === 'string' || Buffer.isBuffer(value)) return value;
  if (typeof value === 'number' && Number.isSafeInteger(value)) return value;
  if (Array.isArray(value)) return value.map(validateValue);
  if (value instanceof Map) {
    for (const [key, item] of value) {
      if (typeof key !== 'string' && (typeof key !== 'number' || !Number.isSafeInteger(key))) {
        throw new Error('CBOR: unsupported map key type');
      }
      validateValue(item);
    }
    return value;
  }
  throw new Error('CBOR: unsupported value');
}

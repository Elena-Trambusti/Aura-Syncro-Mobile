import type { AuraNative, BridgeResponse } from './aura-bridge';
import { onNativeReady } from './aura-bridge';

export function getNativeSync(): AuraNative | null {
  if (typeof window === 'undefined') return null;
  return window.AuraSyncro?.getNative() ?? null;
}

export function withNative<T>(
  fn: (native: AuraNative) => BridgeResponse<T>,
): Promise<BridgeResponse<T>> {
  return new Promise((resolve) => {
    onNativeReady((native) => {
      if (!native.isAvailable) {
        resolve({ ok: false, error: 'Disponibile solo su app Android Aura Syncro' });
        return;
      }
      resolve(fn(native));
    });
  });
}

export async function withNativeData<T>(
  fn: (native: AuraNative) => BridgeResponse<T>,
  fallback: T,
): Promise<T> {
  const result = await withNative(fn);
  return result.ok && result.data !== undefined ? result.data : fallback;
}

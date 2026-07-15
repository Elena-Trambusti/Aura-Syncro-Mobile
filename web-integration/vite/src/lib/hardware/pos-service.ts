import type { PaymentResult } from './aura-bridge';
import { isAndroidTablet, onNativeReady } from './aura-bridge';

export async function payWithConfiguredPos(
  amountEuro: number,
  orderId: string,
  currency = 'EUR',
): Promise<{ ok: boolean; pending?: boolean; error?: string }> {
  if (!isAndroidTablet()) {
    return { ok: false, error: 'Pagamento POS disponibile solo su tablet Android' };
  }

  return new Promise((resolve) => {
    onNativeReady((native) => {
      const amountCents = Math.round(amountEuro * 100);
      const result = native.openConfiguredPosPayment(amountCents, orderId, currency);
      if (!result.ok) {
        resolve({ ok: false, error: result.error });
        return;
      }
      resolve({ ok: true, pending: true });
    });
  });
}

export async function confirmPaymentManually(
  orderId: string,
  status: 'ok' | 'cancelled' | 'error',
  txId?: string,
): Promise<PaymentResult | null> {
  if (!isAndroidTablet()) return null;

  return new Promise((resolve) => {
    onNativeReady((native) => {
      const result = native.confirmPayment(orderId, status, txId);
      resolve(result.ok ? (result.data ?? null) : null);
    });
  });
}

export function listenForPaymentResults(
  onResult: (result: PaymentResult) => void,
  onResumePending?: (orderId: string, amountCents?: number) => void,
): () => void {
  if (typeof window === 'undefined' || !isAndroidTablet() || !window.AuraSyncro) {
    return () => {};
  }

  const handlePayment = (event: Event) => {
    onResult((event as CustomEvent<PaymentResult>).detail);
  };

  const handleResume = (event: Event) => {
    const detail = (event as CustomEvent<{
      pendingPayment: boolean;
      payment?: { orderId?: string; amountCents?: number };
    }>).detail;
    if (detail.pendingPayment && detail.payment?.orderId && onResumePending) {
      onResumePending(detail.payment.orderId, detail.payment.amountCents);
    }
  };

  window.addEventListener('aurasyncro-payment-result', handlePayment);
  window.addEventListener('aurasyncro-app-resumed', handleResume);

  return () => {
    window.removeEventListener('aurasyncro-payment-result', handlePayment);
    window.removeEventListener('aurasyncro-app-resumed', handleResume);
  };
}

'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { PaymentConfirmDialog } from './PaymentConfirmDialog';
import type { PaymentResult } from '@/lib/hardware/aura-bridge';
import { confirmPaymentManually, listenForPaymentResults } from '@/lib/hardware/pos-service';

interface AuraHardwareProviderProps {
  children: ReactNode;
  onPaymentConfirmed?: (result: PaymentResult) => void | Promise<void>;
}

export function AuraHardwareProvider({ children, onPaymentConfirmed }: AuraHardwareProviderProps) {
  const [pendingOrderId, setPendingOrderId] = useState<string | null>(null);
  const [pendingAmountEuro, setPendingAmountEuro] = useState<number | undefined>(undefined);

  const closeDialog = useCallback(() => {
    setPendingOrderId(null);
    setPendingAmountEuro(undefined);
  }, []);

  useEffect(() => {
    return listenForPaymentResults(
      async (result) => {
        closeDialog();
        await onPaymentConfirmed?.(result);
      },
      (orderId, amountCents) => {
        setPendingOrderId(orderId);
        setPendingAmountEuro(
          amountCents !== undefined ? amountCents / 100 : undefined,
        );
      },
    );
  }, [closeDialog, onPaymentConfirmed]);

  async function handleManualConfirm(status: 'ok' | 'cancelled' | 'error') {
    if (!pendingOrderId) return;
    const result = await confirmPaymentManually(pendingOrderId, status);
    closeDialog();
    if (result) await onPaymentConfirmed?.(result);
  }

  return (
    <>
      {children}
      <PaymentConfirmDialog
        open={Boolean(pendingOrderId)}
        orderId={pendingOrderId ?? ''}
        amountEuro={pendingAmountEuro}
        onConfirm={handleManualConfirm}
      />
    </>
  );
}

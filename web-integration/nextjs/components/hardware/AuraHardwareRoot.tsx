'use client';

import type { ReactNode } from 'react';
import { AuraHardwareProvider } from '@/components/hardware/AuraHardwareProvider';
import type { PaymentResult } from '@/lib/hardware/aura-bridge';

async function handlePaymentConfirmed(result: PaymentResult) {
  if (result.status !== 'ok') return;
  // Collega al tuo backend ordini:
  // await fetch(`/api/orders/${result.orderId}/pay`, {
  //   method: 'POST',
  //   headers: { 'Content-Type': 'application/json' },
  //   body: JSON.stringify(result),
  // });
  console.log('Pagamento confermato', result);
}

export function AuraHardwareRoot({ children }: { children: ReactNode }) {
  return (
    <AuraHardwareProvider onPaymentConfirmed={handlePaymentConfirmed}>
      {children}
    </AuraHardwareProvider>
  );
}

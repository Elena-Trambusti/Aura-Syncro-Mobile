'use client';

import styles from './hardware-settings.module.css';

interface PaymentConfirmDialogProps {
  open: boolean;
  orderId: string;
  amountEuro?: number;
  onConfirm: (status: 'ok' | 'cancelled' | 'error') => void;
}

export function PaymentConfirmDialog({
  open,
  orderId,
  amountEuro,
  onConfirm,
}: PaymentConfirmDialogProps) {
  if (!open) return null;

  const amountLabel =
    amountEuro !== undefined ? ` (${amountEuro.toFixed(2)} EUR)` : '';

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true">
      <div className={styles.dialog}>
        <h2 className={styles.dialogTitle}>Conferma pagamento</h2>
        <p className={styles.dialogText}>
          Ordine <strong>{orderId}</strong>
          {amountLabel}. Il pagamento sul POS esterno è andato a buon fine?
        </p>
        <div className={styles.dialogActions}>
          <button type="button" className={styles.btn} onClick={() => onConfirm('cancelled')}>
            Annullato
          </button>
          <button type="button" className={`${styles.btn} ${styles.btnDanger}`} onClick={() => onConfirm('error')}>
            Errore
          </button>
          <button type="button" className={`${styles.btn} ${styles.btnPrimary}`} onClick={() => onConfirm('ok')}>
            Pagato
          </button>
        </div>
      </div>
    </div>
  );
}

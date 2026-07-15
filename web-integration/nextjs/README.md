# Integrazione Next.js — OBSOLETO

> **Il gestionale è React + Vite.** Usa `web-integration/vite/` e `web-integration/COSA_FARE_SUL_SITO.txt`.

## Installazione rapida

### 1. Copia i file

```
web-integration/nextjs/public/aura-android-bridge.js  →  public/
web-integration/nextjs/lib/                           →  lib/
web-integration/nextjs/hooks/                         →  hooks/
web-integration/nextjs/components/                    →  components/
web-integration/nextjs/app/dashboard/settings/hardware/ → app/dashboard/settings/hardware/
```

### 2. Layout root

Unisci `app/layout.example.tsx` con il tuo `app/layout.tsx`:

- Carica `/aura-android-bridge.js`
- Avvolgi l'app con `<AuraHardwareRoot>` (dialog pagamento globale)

### 3. Route impostazioni

Pagina pronta: **`/dashboard/settings/hardware`**

Aggiungi un link nel menu impostazioni del gestionale.

---

## Cosa fa ogni pezzo

| File | Ruolo |
|---|---|
| `public/aura-android-bridge.js` | Ponte JS ↔ Android |
| `hooks/useAuraHardware.ts` | Hook React per config stampanti/POS |
| `components/hardware/HardwareSettings.tsx` | UI impostazioni completa |
| `components/hardware/AuraHardwareRoot.tsx` | Provider + callback pagamento |
| `components/hardware/PaymentConfirmDialog.tsx` | Conferma manuale al ritorno dal POS |
| `lib/hardware/*` | API stampa, POS, config |

---

## Flusso ristorante (marca agnostica)

### Stampanti
1. Apri **Impostazioni → Hardware** sul tablet
2. Richiedi permessi Bluetooth
3. Scansiona **oppure** inserisci IP Wi-Fi (`192.168.x.x:9100`)
4. Salva, testa, imposta predefinita

### POS
1. Cerca l'app installata (SumUp, Nexi, qualsiasi...)
2. Seleziona e salva
3. Deep link **opzionale** — lascia vuoto se non lo conosci
4. In cassa: `payWithConfiguredPos()` → torna in app → dialog "Pagato?"

---

## Uso in cassa

```typescript
import { payWithConfiguredPos } from '@/lib/hardware/pos-service';
import { printKitchenOrder } from '@/lib/hardware/print-service';

await printKitchenOrder(order.id, order.table, order.items);
await payWithConfiguredPos(order.total, order.id);
// AuraHardwareRoot gestisce conferma automatica o manuale
```

---

## tsconfig

Assicurati che `@/*` punti alla root del progetto:

```json
{
  "compilerOptions": {
    "paths": { "@/*": ["./*"] }
  }
}
```

---

## Limitazioni note (non bloccanti)

| Voce | Stato |
|---|---|
| USB stampanti | Non implementato (solo BT + LAN) |
| Discovery LAN automatica | IP manuale (TCP 9100 funziona) |
| Stripe Terminal SDK | Richiede integrazione nativa dedicata |
| Deep link POS | Dipende da ogni app — fallback manuale sempre disponibile |

---

## Deploy

1. Deploy sito con questi file su aurasyncro.com
2. Rebuild APK Android (`assembleDebug` o release)
3. Sul tablet: apri app → Impostazioni hardware → configura

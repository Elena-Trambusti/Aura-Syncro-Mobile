# Integrazione React + Vite — modulo hardware Aura Syncro

Modulo **drop-in** per il gestionale **aurasyncro.com** (React + Vite).

> La cartella `web-integration/nextjs/` è obsoleta. Usa solo questa (`vite/`).

## Installazione

### 1. Copia automatica

```powershell
cd "C:\Users\Elena\Documents\Aura Syncro Mobile"
.\scripts\copia-modulo-sito.ps1 -Destinazione "C:\PERCORSO\TUO-GESTIONALE-VITE"
```

### 2. Modifica `index.html`

In `index.html`, **prima** di `main.tsx`:

```html
<script src="/aura-android-bridge.js"></script>
```

### 3. Modifica `src/App.tsx`

```tsx
import { AuraHardwareRoot } from '@/components/hardware/AuraHardwareRoot';

export default function App() {
  return (
    <AuraHardwareRoot>
      {/* router e resto dell'app */}
    </AuraHardwareRoot>
  );
}
```

### 4. Aggiungi la route

```tsx
import { HardwareSettingsPage } from '@/pages/settings/HardwareSettingsPage';

<Route path="/dashboard/settings/hardware" element={<HardwareSettingsPage />} />
```

### 5. Alias `@` (se non c'è già)

Vedi `vite.config.alias.example.ts`

### 6. Build e deploy

```bash
npm run build
```

Pubblica su aurasyncro.com, poi apri l'app Android sul tablet.

## Struttura copiata

```
public/aura-android-bridge.js
src/lib/hardware/
src/hooks/useAuraHardware.ts
src/components/hardware/
src/pages/settings/HardwareSettingsPage.tsx
```

## Uso in cassa

```ts
import { printKitchenOrder } from '@/lib/hardware/print-service';
import { payWithConfiguredPos } from '@/lib/hardware/pos-service';

await printKitchenOrder(order.id, order.table, order.items);
await payWithConfiguredPos(order.total, order.id);
```

`AuraHardwareRoot` gestisce il dialog "Pagato?" al ritorno dal POS.

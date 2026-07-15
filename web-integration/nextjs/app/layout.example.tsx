/**
 * Esempio layout root — unisci con il tuo app/layout.tsx
 */
import Script from 'next/script';
import { AuraHardwareRoot } from '@/components/hardware/AuraHardwareRoot';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="it">
      <body>
        <Script src="/aura-android-bridge.js" strategy="beforeInteractive" />
        <AuraHardwareRoot>{children}</AuraHardwareRoot>
      </body>
    </html>
  );
}

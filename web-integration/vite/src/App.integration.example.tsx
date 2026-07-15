/**
 * Esempio: unisci con il tuo src/App.tsx
 */
import { AuraHardwareRoot } from '@/components/hardware/AuraHardwareRoot';
// import { BrowserRouter, Routes, Route } from 'react-router-dom';
// import { HardwareSettingsPage } from '@/pages/settings/HardwareSettingsPage';

export default function App() {
  return (
    <AuraHardwareRoot>
      {/* Il tuo router e le tue route esistenti */}
      {/* <BrowserRouter>
        <Routes>
          ...
          <Route path="/dashboard/settings/hardware" element={<HardwareSettingsPage />} />
        </Routes>
      </BrowserRouter> */}
    </AuraHardwareRoot>
  );
}

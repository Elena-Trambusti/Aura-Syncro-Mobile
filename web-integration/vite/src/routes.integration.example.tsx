/**
 * Aggiungi questa route nel tuo router React (react-router-dom).
 */
import { HardwareSettingsPage } from '@/pages/settings/HardwareSettingsPage';

// Esempio dentro <Routes>:
// <Route path="/dashboard/settings/hardware" element={<HardwareSettingsPage />} />

export const hardwareSettingsRoute = {
  path: '/dashboard/settings/hardware',
  element: <HardwareSettingsPage />,
};

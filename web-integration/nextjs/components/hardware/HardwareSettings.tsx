'use client';

import { useEffect, useState } from 'react';
import styles from './hardware-settings.module.css';
import { useAuraHardware } from '@/hooks/useAuraHardware';
import { createPrinterId } from '@/lib/hardware/printer-config';

export function HardwareSettings() {
  const {
    loading,
    isAndroid,
    config,
    permissionsGranted,
    bluetoothEnabled,
    scannedPrinters,
    posApps,
    message,
    error,
    requestPermissions,
    scanPrinters,
    savePrinter,
    deletePrinter,
    markDefaultPrinter,
    testPrinter,
    testConnection,
    loadPosApps,
    savePos,
    openBluetoothSettings,
  } = useAuraHardware();

  const [printerLabel, setPrinterLabel] = useState('Cucina');
  const [printerType, setPrinterType] = useState<'bluetooth' | 'wifi'>('bluetooth');
  const [printerAddress, setPrinterAddress] = useState('');
  const [selectedScan, setSelectedScan] = useState('');
  const [posQuery, setPosQuery] = useState('');
  const [posPackage, setPosPackage] = useState('');
  const [posLabel, setPosLabel] = useState('');
  const [deepLinkTemplate, setDeepLinkTemplate] = useState('');

  useEffect(() => {
    if (isAndroid) loadPosApps('');
  }, [isAndroid, loadPosApps]);

  useEffect(() => {
    if (config.pos) {
      setPosPackage(config.pos.packageName);
      setPosLabel(config.pos.label);
      setDeepLinkTemplate(config.pos.deepLinkTemplate ?? '');
    }
  }, [config.pos]);

  if (loading) {
    return <div className={styles.hw}>Caricamento impostazioni hardware...</div>;
  }

  if (!isAndroid) {
    return (
      <div className={styles.hw}>
        <div className={`${styles.banner} ${styles.bannerWarn}`}>
          <strong>Modalità browser</strong>
          <p className={styles.sectionHint}>
            Stampanti e POS fisici si configurano dall&apos;app Android Aura Syncro Mobile sul tablet del
            ristorante.
          </p>
        </div>
      </div>
    );
  }

  async function handleSavePrinter() {
    const address =
      printerType === 'bluetooth' && selectedScan
        ? selectedScan
        : printerAddress.trim();

    if (!address) return;

    await savePrinter(
      {
        id: createPrinterId(printerLabel),
        label: printerLabel.trim() || 'Stampante',
        type: printerType,
        address,
      },
      config.printers.length === 0,
    );
  }

  async function handleSavePos() {
    if (!posPackage.trim()) return;
    await savePos({
      packageName: posPackage.trim(),
      label: posLabel.trim() || posPackage.trim(),
      deepLinkTemplate: deepLinkTemplate.trim() || undefined,
    });
  }

  return (
    <div className={styles.hw}>
      <header className={styles.header}>
        <h1 className={styles.title}>Hardware ristorante</h1>
        <p className={styles.subtitle}>
          Configura stampanti e POS al momento: ogni locale può usare marche diverse.
        </p>
      </header>

      {message && (
        <div className={`${styles.banner} ${styles.bannerOk}`}>{message}</div>
      )}
      {error && (
        <div className={`${styles.banner} ${styles.bannerError}`}>{error}</div>
      )}

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Permessi</h2>
        <p className={styles.sectionHint}>
          Necessari per Bluetooth. Wi-Fi/LAN usa la rete locale del tablet.
        </p>
        <div className={styles.row}>
          <button type="button" className={styles.btn} onClick={() => requestPermissions()}>
            Richiedi permessi
          </button>
          <button type="button" className={styles.btn} onClick={() => openBluetoothSettings()}>
            Impostazioni Bluetooth
          </button>
          <span className={styles.chip}>
            Permessi: {permissionsGranted ? 'OK' : 'Mancanti'}
          </span>
          <span className={styles.chip}>
            Bluetooth: {bluetoothEnabled ? 'Attivo' : 'Spento'}
          </span>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Stampanti ESC/POS</h2>
        <p className={styles.sectionHint}>
          Bluetooth: scansiona e seleziona. Wi-Fi: inserisci IP e porta (default 9100).
        </p>

        <div className={styles.row}>
          <button type="button" className={styles.btn} onClick={() => scanPrinters()}>
            Scansiona Bluetooth
          </button>
        </div>

        {scannedPrinters.length > 0 && (
          <div className={styles.chips} style={{ marginBottom: 12 }}>
            {scannedPrinters.map((item) => (
              <button
                key={item.address}
                type="button"
                className={`${styles.chip} ${selectedScan === item.address ? styles.chipActive : ''}`}
                onClick={() => {
                  setSelectedScan(item.address);
                  setPrinterType('bluetooth');
                  setPrinterAddress(item.address);
                  if (!printerLabel || printerLabel === 'Cucina') {
                    setPrinterLabel(item.name);
                  }
                }}
              >
                {item.name} ({item.address})
              </button>
            ))}
          </div>
        )}

        <div className={styles.row}>
          <label className={styles.field}>
            <span className={styles.label}>Nome</span>
            <input
              className={styles.input}
              value={printerLabel}
              onChange={(e) => setPrinterLabel(e.target.value)}
              placeholder="Cucina, Bar..."
            />
          </label>
          <label className={styles.field}>
            <span className={styles.label}>Tipo</span>
            <select
              className={styles.select}
              value={printerType}
              onChange={(e) => setPrinterType(e.target.value as 'bluetooth' | 'wifi')}
            >
              <option value="bluetooth">Bluetooth</option>
              <option value="wifi">Wi-Fi / LAN</option>
            </select>
          </label>
          <label className={styles.field}>
            <span className={styles.label}>Indirizzo</span>
            <input
              className={styles.input}
              value={printerAddress}
              onChange={(e) => setPrinterAddress(e.target.value)}
              placeholder={printerType === 'wifi' ? '192.168.1.50:9100' : 'MAC Bluetooth'}
            />
          </label>
        </div>

        <div className={styles.row}>
          <button type="button" className={`${styles.btn} ${styles.btnPrimary}`} onClick={handleSavePrinter}>
            Salva stampante
          </button>
          <button
            type="button"
            className={styles.btn}
            onClick={() =>
              testConnection(printerType, printerAddress.trim(), printerLabel.trim())
            }
          >
            Test connessione
          </button>
        </div>

        <div className={styles.list} style={{ marginTop: 16 }}>
          {config.printers.length === 0 && (
            <p className={styles.empty}>Nessuna stampante configurata.</p>
          )}
          {config.printers.map((printer) => (
            <article key={printer.id} className={styles.card}>
              <div className={styles.cardHead}>
                <div>
                  <h3 className={styles.cardTitle}>
                    {printer.label}
                    {config.defaultPrinterId === printer.id ? ' · predefinita' : ''}
                  </h3>
                  <p className={styles.cardMeta}>
                    {printer.type} — {printer.address}
                  </p>
                </div>
              </div>
              <div className={styles.cardActions}>
                <button type="button" className={styles.btn} onClick={() => testPrinter(printer.id)}>
                  Test stampa
                </button>
                <button type="button" className={styles.btn} onClick={() => markDefaultPrinter(printer.id)}>
                  Predefinita
                </button>
                <button
                  type="button"
                  className={`${styles.btn} ${styles.btnDanger}`}
                  onClick={() => deletePrinter(printer.id)}
                >
                  Elimina
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>App POS</h2>
        <p className={styles.sectionHint}>
          Scegli qualsiasi app di pagamento installata. Il deep link è opzionale: senza, confermi manualmente al ritorno.
        </p>

        <div className={styles.row}>
          <label className={styles.field}>
            <span className={styles.label}>Cerca app</span>
            <input
              className={styles.input}
              value={posQuery}
              onChange={(e) => setPosQuery(e.target.value)}
              placeholder="sumup, nexi, pay..."
            />
          </label>
          <button type="button" className={styles.btn} onClick={() => loadPosApps(posQuery)}>
            Cerca
          </button>
        </div>

        {posApps.length > 0 && (
          <div className={styles.chips} style={{ marginBottom: 12 }}>
            {posApps.slice(0, 24).map((app) => (
              <button
                key={app.packageName}
                type="button"
                className={`${styles.chip} ${posPackage === app.packageName ? styles.chipActive : ''}`}
                onClick={() => {
                  setPosPackage(app.packageName);
                  setPosLabel(app.label);
                }}
              >
                {app.label}
              </button>
            ))}
          </div>
        )}

        <div className={styles.row}>
          <label className={styles.field}>
            <span className={styles.label}>Package Android</span>
            <input
              className={styles.input}
              value={posPackage}
              onChange={(e) => setPosPackage(e.target.value)}
              placeholder="com.esempio.pos"
            />
          </label>
          <label className={styles.field}>
            <span className={styles.label}>Etichetta</span>
            <input
              className={styles.input}
              value={posLabel}
              onChange={(e) => setPosLabel(e.target.value)}
              placeholder="Nome mostrato in cassa"
            />
          </label>
        </div>

        <label className={styles.field}>
          <span className={styles.label}>Deep link template (opzionale)</span>
          <textarea
            className={styles.textarea}
            value={deepLinkTemplate}
            onChange={(e) => setDeepLinkTemplate(e.target.value)}
            placeholder="scheme://pay?amount={{amount}}&currency={{currency}}&callback={{callback}}"
          />
        </label>

        <div className={styles.row}>
          <button type="button" className={`${styles.btn} ${styles.btnPrimary}`} onClick={handleSavePos}>
            Salva POS
          </button>
          <button type="button" className={styles.btn} onClick={() => savePos(null)}>
            Rimuovi POS
          </button>
        </div>
      </section>
    </div>
  );
}

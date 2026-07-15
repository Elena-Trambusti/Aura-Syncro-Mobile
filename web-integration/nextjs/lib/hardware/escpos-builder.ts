export interface ReceiptLine {
  name: string;
  qty: number;
  price: number;
}

const ESC = 0x1b;
const GS = 0x1d;

function cmd(...bytes: number[]): Uint8Array {
  return new Uint8Array(bytes);
}

function text(str: string): Uint8Array {
  const bytes = new Uint8Array(str.length);
  for (let i = 0; i < str.length; i++) {
    bytes[i] = str.charCodeAt(i) & 0xff;
  }
  return bytes;
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}

export function buildKitchenTicket(
  orderId: string,
  table: string,
  lines: ReceiptLine[],
): Uint8Array {
  const header = concat(
    cmd(ESC, 0x40),
    cmd(ESC, 0x61, 0x01),
    cmd(ESC, 0x45, 0x01),
    text(`COMANDA #${orderId}\n`),
    cmd(ESC, 0x45, 0x00),
    text(`Tavolo: ${table}\n`),
    text(`${new Date().toLocaleTimeString('it-IT')}\n`),
    text('--------------------------------\n'),
    cmd(ESC, 0x61, 0x00),
  );

  const items = lines.map((line) => {
    const label = `${line.qty}x ${line.name}`;
    const price = `EUR ${(line.qty * line.price).toFixed(2)}`;
    const padding = ' '.repeat(Math.max(1, 32 - label.length - price.length));
    return text(`${label}${padding}${price}\n`);
  });

  const footer = concat(
    text('--------------------------------\n'),
    cmd(ESC, 0x61, 0x01),
    text('\n\n'),
    cmd(GS, 0x56, 0x01),
  );

  return concat(header, ...items, footer);
}

export function toBase64(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

import type { ReactNode } from 'react';
import './styles.css';

export const metadata = {
  title: 'StoreTD Play Admin',
  description: 'Panel administrativo para clientes, listas y reportes',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}

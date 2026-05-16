import './globals.css'

export const metadata = {
  title: 'Antigravity Chat',
  description: 'Chat avec l\'assistant IA pour le projet MCP GitHub',
}

export default function RootLayout({ children }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  )
}

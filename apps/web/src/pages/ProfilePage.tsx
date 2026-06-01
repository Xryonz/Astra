import { Navigate } from 'react-router-dom'

/**
 * Perfil foi unificado dentro de Settings → seção "Perfil".
 * Redireciona pra preservar links antigos (/app/profile → /app/settings#profile).
 */
export default function ProfilePage() {
  return <Navigate to="/app/settings#profile" replace />
}

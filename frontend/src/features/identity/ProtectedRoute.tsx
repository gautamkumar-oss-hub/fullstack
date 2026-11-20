import { Alert, CircularProgress, Container } from '@mui/material'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Role } from './types'

export function ProtectedRoute({ roles, allowPasswordChange = false }: { roles?: Role[]; allowPasswordChange?: boolean }) {
  const { account, isRestoring } = useAuth()
  const location = useLocation()
  if (isRestoring) return <Container sx={{ py: 8, textAlign: 'center' }}><CircularProgress aria-label="Restoring your session" /></Container>
  if (!account) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (account.forcePasswordChange && !allowPasswordChange) return <Navigate to="/account/password" replace />
  if (roles && !roles.includes(account.role)) {
    return <Container sx={{ py: 6 }}><Alert severity="error">You do not have permission to access this page.</Alert></Container>
  }
  return <Outlet />
}

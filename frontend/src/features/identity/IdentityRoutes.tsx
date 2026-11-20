import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AccountLayout } from './AccountLayout'
import { AccountPage } from './AccountPage'
import { ChangePasswordPage } from './ChangePasswordPage'
import { LoginPage } from './LoginPage'
import { MfaPage } from './MfaPage'
import { ProtectedRoute } from './ProtectedRoute'
import { RecoveryPage } from './RecoveryPage'
import { RegisterPage } from './RegisterPage'
import { VerificationPage } from './VerificationPage'
import { DirectoryPage } from '../scheduling/DirectoryPage'
import { SchedulePage } from '../scheduling/SchedulePage'
import { AvailabilityPage } from '../scheduling/AvailabilityPage'
import { ConfigurationPage } from '../scheduling/ConfigurationPage'
import { RoleLandingPage } from '../scheduling/RoleLandingPage'

export function IdentityRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/mfa" element={<MfaPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify" element={<VerificationPage />} />
      <Route path="/recover" element={<RecoveryPage />} />
      <Route element={<ProtectedRoute allowPasswordChange />}>
        <Route element={<AccountLayout><Outlet /></AccountLayout>}>
          <Route path="/account/password" element={<ChangePasswordPage />} />
        </Route>
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AccountLayout><Outlet /></AccountLayout>}>
          <Route path="/account" element={<AccountPage />} />
          <Route path="/workspace" element={<RoleLandingPage />} />
        </Route>
      </Route>
      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route element={<AccountLayout><Outlet /></AccountLayout>}>
          <Route path="/admin/directory" element={<DirectoryPage />} />
          <Route path="/admin/schedules" element={<SchedulePage />} />
          <Route path="/admin/configuration" element={<ConfigurationPage />} />
        </Route>
      </Route>
      <Route element={<ProtectedRoute roles={['ADMIN', 'STAFF']} />}>
        <Route element={<AccountLayout><Outlet /></AccountLayout>}>
          <Route path="/scheduling/availability" element={<AvailabilityPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

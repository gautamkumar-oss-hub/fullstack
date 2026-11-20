import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/identity/AuthContext'
import { IdentityRoutes } from './features/identity/IdentityRoutes'
import { SystemOverview } from './features/system/SystemOverview'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<SystemOverview />} />
          <Route path="/*" element={<IdentityRoutes />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}


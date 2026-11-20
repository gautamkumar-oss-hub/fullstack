import { Box, Button, Chip, Container, Divider, Stack, Typography } from '@mui/material'
import { ShieldCheck, Stethoscope } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { FormError } from './FormControls'

const roleLabels = { ADMIN: 'Administrator', STAFF: 'Staff', PATIENT: 'Patient' }

export function AccountLayout({ children }: { children: React.ReactNode }) {
  const { account, logout } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<unknown>(null)
  if (!account) return null

  const signOut = async () => {
    setError(null)
    try { await logout(); navigate('/login', { replace: true }) } catch (reason) { setError(reason) }
  }

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 6 } }}>
      <Stack spacing={4}>
        <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }} spacing={2}>
          <Stack direction="row" spacing={1.5} component={Link} to="/" sx={{ alignItems: 'center', color: 'inherit', textDecoration: 'none' }}>
            <Box className="brand-mark" aria-hidden="true"><Stethoscope size={24} /></Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>Hospital appointments</Typography>
          </Stack>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip icon={<ShieldCheck size={16} />} label={roleLabels[account.role]} color="primary" variant="outlined" />
            {!account.forcePasswordChange && <><Button component={Link} to="/workspace">Workspace</Button><Button component={Link} to="/account">My account</Button>
              {account.role === 'ADMIN' && <><Button component={Link} to="/admin/directory">Directory</Button><Button component={Link} to="/admin/schedules">Schedules</Button><Button component={Link} to="/admin/configuration">Configuration</Button></>}
              {account.role !== 'PATIENT' && <Button component={Link} to="/scheduling/availability">Availability</Button>}</>}
            <Button component={Link} to="/account/password">Change password</Button>
            <Button variant="outlined" onClick={() => void signOut()}>Sign out</Button>
          </Stack>
        </Stack>
        <Divider />
        <FormError error={error} />
        {children}
      </Stack>
    </Container>
  )
}

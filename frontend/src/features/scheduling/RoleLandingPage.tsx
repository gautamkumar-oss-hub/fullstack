import { Alert, Button, Paper, Stack, Typography } from '@mui/material'
import { Link } from 'react-router-dom'
import { useAuth } from '../identity/AuthContext'

export function RoleLandingPage() {
  const { account } = useAuth()
  if (!account) return null
  const staff = account.role === 'ADMIN' || account.role === 'STAFF'
  return <Stack spacing={3}>
    <Stack spacing={1}><Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>{account.role === 'ADMIN' ? 'Administrator workspace' : account.role === 'STAFF' ? 'Staff workspace' : 'Patient workspace'}</Typography>
      <Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Welcome, {account.firstName}.</Typography></Stack>
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 } }}><Stack spacing={2}>
      {staff ? <><Typography variant="h5" component="h2">Directory and scheduling</Typography><Typography color="text.secondary">Review active doctors and projected fixed-slot availability. {account.role === 'ADMIN' ? 'You can also administer directory records, schedules, and clinic configuration.' : 'Schedule and configuration mutations remain administrator-only.'}</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>{account.role === 'ADMIN' && <Button component={Link} to="/admin/directory" variant="contained">Manage directory</Button>}<Button component={Link} to="/scheduling/availability" variant="outlined">View availability</Button></Stack></>
        : <><Typography variant="h5" component="h2">Your account</Typography><Typography color="text.secondary">Patient appointment requests and booking are not part of Phase 3. Your identity and account security remain available.</Typography><Alert severity="info">No scheduling, proposal, or allocation workflow is available for patients yet.</Alert><BoxLink /></>}
    </Stack></Paper>
  </Stack>
}

function BoxLink() { return <Button component={Link} to="/account" variant="outlined">View my account</Button> }

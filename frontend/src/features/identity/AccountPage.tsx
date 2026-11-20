import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { currentAccount } from './service'
import { FormError } from './FormControls'
import { AccountManagement } from './AccountManagement'

export function AccountPage() {
  const { account } = useAuth()
  const { data, error, isPending } = useQuery({ queryKey: ['account', 'me'], queryFn: ({ signal }) => currentAccount(signal), enabled: !!account, retry: false })
  const profile = data ?? account
  if (!profile) return null

  return (
    <Stack spacing={3}>
      <Stack spacing={1}>
        <Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>Secure account access</Typography>
        <Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Welcome, {profile.firstName}.</Typography>
        <Typography color="text.secondary">Your identity and access details for the hospital appointment platform.</Typography>
      </Stack>
      <FormError error={error} />
      {isPending && <Alert severity="info">Refreshing your account details…</Alert>}
      <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, maxWidth: 700 }}>
        <Stack spacing={2}>
          <Typography variant="h5" component="h2">Account details</Typography>
          <Detail label="Name" value={`${profile.firstName} ${profile.lastName}`} />
          <Detail label="Email" value={profile.email} />
          <Detail label="Role" value={profile.role} />
          <Detail label="Status" value={profile.status.replaceAll('_', ' ').toLowerCase()} />
          <Detail label="Email verification" value={profile.emailVerified ? 'Verified' : 'Pending'} />
        </Stack>
      </Paper>
      {profile.role === 'ADMIN' && <AccountManagement />}
      {profile.role === 'STAFF' && <Alert severity="info">Use the staff workspace to view active doctor directories and projected availability.</Alert>}
      {profile.role === 'PATIENT' && <Alert severity="info">Patient appointment requests and booking are not part of Phase 3. Your identity and account security are available here.</Alert>}
      <Box><Button component={Link} to="/workspace" variant="outlined">Open workspace</Button></Box>
    </Stack>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="body2" color="text.secondary">{label}</Typography><Typography sx={{ overflowWrap: 'anywhere' }}>{value}</Typography></Box>
}

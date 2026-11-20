import { useQuery } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Divider,
  Paper,
  Stack,
  Typography,
} from '@mui/material'
import { Calendar, Shield, Stethoscope } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../identity/AuthContext'
import { getSystemInfo } from './api'

export function SystemOverview() {
  const { account } = useAuth()
  const { data, error, isPending } = useQuery({
    queryKey: ['system', 'info'],
    queryFn: ({ signal }) => getSystemInfo(signal),
    retry: 1,
  })

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 4, md: 8 } }}>
      <Stack spacing={5}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' } }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Box className="brand-mark" aria-hidden="true"><Stethoscope size={24} /></Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>Hospital appointments</Typography>
          </Stack>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Chip label="Directory & scheduling" color="primary" variant="outlined" size="small" />
            <Button component={Link} to={account?.forcePasswordChange ? '/account/password' : account ? '/account' : '/login'} variant="outlined">
              {account ? 'My account' : 'Sign in'}
            </Button>
          </Stack>
        </Stack>

        <Stack spacing={2} sx={{ maxWidth: 700 }}>
          <Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>Care coordination, clearly organized</Typography>
          <Typography variant="h2" component="h1" sx={{ fontWeight: 700 }}>
            A better starting point for every appointment.
          </Typography>
          <Typography variant="h6" color="text.secondary" sx={{ fontWeight: 400 }}>
            Secure access, the clinical directory, recurring availability, and scheduling configuration are ready. Patient appointment booking and allocation workflows remain outside Phase 3.
          </Typography>
        </Stack>

        <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, maxWidth: 700 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Calendar size={22} aria-hidden="true" />
              <Typography variant="h6">Scheduling configuration</Typography>
            </Stack>
            <Divider />
            {isPending && <CircularProgress aria-label="Loading scheduling configuration" />}
            {error instanceof Error && <Alert severity="info">{error.message} Start the local API to display its configuration.</Alert>}
            {data && (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4}>
                <Configuration label="Clinic time zone" value={data.clinicTimeZone} />
                <Configuration label="Booking horizon" value={`${data.bookingHorizonDays} days`} />
                <Configuration label="Proposal hold" value={`${data.proposalHoldHours} hours`} />
              </Stack>
            )}
          </Stack>
        </Paper>

        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'text.secondary' }}>
          <Shield size={18} aria-hidden="true" />
          <Typography variant="body2">Educational MVP · Synthetic data only</Typography>
        </Stack>
      </Stack>
    </Container>
  )
}

function Configuration({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{value}</Typography>
    </Box>
  )
}

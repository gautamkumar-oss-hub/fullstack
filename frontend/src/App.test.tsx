import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ApiError } from './features/identity/http'
import * as identity from './features/identity/service'
import { theme } from './theme'

vi.mock('./features/system/api', () => ({
  getSystemInfo: vi.fn().mockResolvedValue({
    applicationName: 'Hospital Appointment Platform',
    clinicTimeZone: 'UTC',
    bookingHorizonDays: 90,
    proposalHoldHours: 24,
    patientChangeCutoffHours: 24,
  }),
}))

describe('App', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders the foundation overview and API configuration', async () => {
    vi.spyOn(identity, 'refreshSession').mockRejectedValue(new ApiError('No session', 401))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <ThemeProvider theme={theme}>
        <QueryClientProvider client={queryClient}><App /></QueryClientProvider>
      </ThemeProvider>,
    )

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('A better starting point')
    expect(await screen.findByText('UTC')).toBeInTheDocument()
    expect(screen.getByText('90 days')).toBeInTheDocument()
  })
})

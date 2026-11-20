import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as identity from './service'
import { AuthProvider } from './AuthContext'
import { IdentityRoutes } from './IdentityRoutes'
import type { Account, Session } from './types'

const account = (role: Account['role'] = 'PATIENT', forcePasswordChange = false): Account => ({
  id: '17e57e82-bb0c-4a8b-9e9c-1b9110f54c60', email: `${role.toLowerCase()}@example.test`, role, status: 'ACTIVE',
  firstName: 'Test', lastName: 'User', emailVerified: true, forcePasswordChange, authzVersion: 1, version: 2,
})
const session = (value: Account): Session => ({ accessToken: 'test-token', expiresIn: 600, account: value })
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function renderRoutes(path: string) {
  document.cookie = 'XSRF-TOKEN=test-csrf; path=/'
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[path]}><AuthProvider><IdentityRoutes /></AuthProvider></MemoryRouter>
  </QueryClientProvider>)
}

function expectObject(mock: ReturnType<typeof vi.spyOn>, expected: Record<string, unknown>): void {
  expect(mock).toHaveBeenCalledWith(expect.objectContaining(expected))
}

describe('identity routes', () => {
  beforeEach(() => {
    identity.clearSession()
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(json({ detail: 'No session' }, 401))))
  })
  afterEach(() => {
    identity.clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it('completes MFA and redirects forced-password staff to password change', async () => {
    const user = userEvent.setup()
    const staff = account('STAFF', true)
    vi.mocked(fetch).mockImplementation((input) => Promise.resolve(String(input).endsWith('/login')
      ? json({ mfaRequired: true }) : json(session(staff))))
    renderRoutes('/login')
    await user.type(await screen.findByRole('textbox', { name: /^Email address/ }), staff.email)
    await user.type(screen.getByLabelText(/^Password/), 'not-a-real-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { name: 'Confirm it’s you' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /^Email address/ })).toHaveValue(staff.email)
    await user.type(screen.getByRole('textbox', { name: /^Six-digit code/ }), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify and sign in' }))
    expect(await screen.findByRole('heading', { name: 'Change your password' })).toBeInTheDocument()
    const mfaRequest = vi.mocked(fetch).mock.calls.find(([input]) => String(input).endsWith('/mfa/verify'))
    expect(JSON.parse(String(mfaRequest?.[1]?.body))).toEqual({ email: staff.email, code: '123456' })
    expect(screen.queryByRole('heading', { name: 'Account administration' })).not.toBeInTheDocument()
  })

  it('shows administration only to administrators', async () => {
    const admin = account('ADMIN')
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(json(session(admin))))
    renderRoutes('/account')
    expect(await screen.findByRole('heading', { name: 'Account administration' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeInTheDocument()
  })

  it('hides account administration from patients', async () => {
    const patient = account()
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(json(session(patient))))
    renderRoutes('/account')
    expect(await screen.findByRole('heading', { name: 'Account details' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Account administration' })).not.toBeInTheDocument()
  })

  it('gives staff availability access without administrative mutations', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/refresh')) return Promise.resolve(json(session(account('STAFF'))))
      if (url.includes('/directory/doctors')) return Promise.resolve(json([]))
      return Promise.resolve(json({ detail: 'Unexpected request' }, 500))
    })
    renderRoutes('/scheduling/availability')
    expect(await screen.findByRole('heading', { name: 'Doctor availability' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Directory' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Configuration' })).not.toBeInTheDocument()
  })

  it('rejects patients from staff scheduling and administrative routes', async () => {
    vi.mocked(fetch).mockImplementation(() => Promise.resolve(json(session(account()))))
    renderRoutes('/admin/directory')
    expect(await screen.findByText('You do not have permission to access this page.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Doctors and specializations' })).not.toBeInTheDocument()
  })

  it('exposes administrative directory access to administrators', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/refresh')) return Promise.resolve(json(session(account('ADMIN'))))
      if (url.includes('/directory/')) return Promise.resolve(json([]))
      return Promise.resolve(json({ detail: 'Unexpected request' }, 500))
    })
    renderRoutes('/admin/directory')
    expect(await screen.findByRole('heading', { name: 'Doctors and specializations' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create doctor' })).toBeInTheDocument()
  })

  it('registers a patient and hands their email to verification', async () => {
    const user = userEvent.setup()
    const register = vi.spyOn(identity, 'register').mockResolvedValue({ message: 'Registration accepted.' })
    renderRoutes('/register')
    await user.type(await screen.findByRole('textbox', { name: /^First name/ }), 'Test')
    await user.type(screen.getByRole('textbox', { name: /^Last name/ }), 'Patient')
    await user.type(screen.getByRole('textbox', { name: /^Email address/ }), 'patient@example.test')
    await user.type(screen.getByLabelText(/^Password/), 'example-password-1')
    await user.click(screen.getByRole('button', { name: 'Create patient account' }))
    expect(await screen.findByText('Registration accepted.')).toBeInTheDocument()
    expectObject(register, { email: 'patient@example.test', firstName: 'Test', lastName: 'Patient' })
    await user.click(screen.getByRole('button', { name: 'Verify email' }))
    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /^Email address/ })).toHaveValue('patient@example.test')
  })

  it('submits the email with verification and recovery codes', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(identity, 'confirmVerification').mockResolvedValue({ message: 'Email verified.' })
    renderRoutes('/verify')
    await user.type(await screen.findByRole('textbox', { name: /^Email address/ }), 'patient@example.test')
    await user.type(screen.getByRole('textbox', { name: /^Six-digit code/ }), '123456')
    await user.click(screen.getByRole('button', { name: 'Verify email' }))
    expect(await screen.findByText('Email verified.')).toBeInTheDocument()
    expect(confirm).toHaveBeenCalledWith('patient@example.test', '123456')
  })

  it('recovers an account using the requested email', async () => {
    const user = userEvent.setup()
    vi.spyOn(identity, 'requestRecovery').mockResolvedValue({ message: 'If eligible, a code was sent.' })
    const confirm = vi.spyOn(identity, 'confirmRecovery').mockResolvedValue({ message: 'Password changed.' })
    renderRoutes('/recover')
    await user.type(await screen.findByRole('textbox', { name: /^Email address/ }), 'patient@example.test')
    await user.click(screen.getByRole('button', { name: 'Send recovery code' }))
    const codeField = await screen.findByRole('textbox', { name: /^Six-digit recovery code/ })
    await user.type(codeField, '123456')
    await user.type(screen.getByLabelText(/^New password/), 'example-password-2')
    await user.click(screen.getByRole('button', { name: 'Set new password' }))
    expect(await screen.findByText('Password changed.')).toBeInTheDocument()
    expect(confirm).toHaveBeenCalledWith('patient@example.test', '123456', 'example-password-2')
  })

  it('redirects an unauthenticated protected-page request to sign in', async () => {
    renderRoutes('/account')
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })
})

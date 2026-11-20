import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'
import { clearSession, currentAccount, login, refreshSession, sessionStore } from './service'
import type { Account, Session } from './types'

const account: Account = {
  id: '17e57e82-bb0c-4a8b-9e9c-1b9110f54c60', email: 'patient@example.test', role: 'PATIENT', status: 'ACTIVE',
  firstName: 'Test', lastName: 'Patient', emailVerified: true, forcePasswordChange: false, authzVersion: 1, version: 2,
}
const session = (accessToken: string): Session => ({ accessToken, expiresIn: 600, account })
const tokenResponse = (accessToken: string) => ({ ...session(accessToken), account })
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function mockFetch(handler: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response> | Response) {
  const fetchMock = vi.fn(handler)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function establishSession(accessToken = 'original-test-token') {
  mockFetch(() => json(tokenResponse(accessToken)))
  const result = await login(account.email, 'not-a-real-password')
  expect(result.mfaRequired).toBe(false)
}

describe('identity session boundary', () => {
  beforeEach(() => {
    clearSession()
    localStorage.clear()
    sessionStorage.clear()
    document.cookie = 'XSRF-TOKEN=test-csrf; path=/'
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it('keeps access tokens out of browser storage and clears them on logout', async () => {
    const fetchMock = mockFetch((input) => String(input).endsWith('/logout') ? new Response(null, { status: 204 }) : json(tokenResponse('memory-only-test-token')))
    await login(account.email, 'not-a-real-password')
    expect(localStorage).toHaveLength(0)
    expect(sessionStorage).toHaveLength(0)
    const seen: Array<Session | null> = []
    const unsubscribe = sessionStore.subscribe((value) => seen.push(value))
    const { logout } = await import('./service')
    await logout()
    unsubscribe()
    expect(seen.at(-1)).toBeNull()
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/refresh'))).toBe(false)
    await expect(currentAccount()).rejects.toMatchObject({ status: 401 })
  })

  it('shares one refresh across concurrent authenticated 401 responses', async () => {
    await establishSession()
    let releaseRefresh!: (value: Response) => void
    const refreshed = new Promise<Response>((resolve) => { releaseRefresh = resolve })
    const fetchMock = mockFetch((input, init) => {
      const path = String(input)
      if (path.endsWith('/accounts/me')) {
        if (!init) throw new Error('The authenticated request must include request initialization.')
        return (init.headers as Record<string, string>).Authorization === 'Bearer replacement-test-token'
          ? json(account) : json({ detail: 'Expired' }, 401)
      }
      if (path.endsWith('/refresh')) return refreshed
      throw new Error(`Unexpected request: ${path}`)
    })
    const first = currentAccount()
    const second = currentAccount()
    const settled = Promise.all([first, second])
    await vi.waitFor(() => expect(fetchMock.mock.calls.filter(([input]) => String(input).endsWith('/refresh'))).toHaveLength(1))
    releaseRefresh(json(tokenResponse('replacement-test-token')))
    await expect(settled).resolves.toEqual([account, account])
    expect(fetchMock.mock.calls.filter(([input]) => String(input).endsWith('/accounts/me'))).toHaveLength(4)
    expect(localStorage).toHaveLength(0)
    expect(sessionStorage).toHaveLength(0)
  })

  it('never refreshes after an authorization failure', async () => {
    await establishSession()
    const fetchMock = mockFetch(() => json({ detail: 'Forbidden' }, 403))
    await expect(currentAccount()).rejects.toMatchObject({ status: 403 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/refresh'))).toBe(false)
  })

  it('uses an explicit MFA response without creating a token session', async () => {
    const fetchMock = mockFetch(() => json({ mfaRequired: true }))
    await expect(login('staff@example.test', 'not-a-real-password')).resolves.toEqual({ mfaRequired: true, developmentCode: undefined })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    await expect(currentAccount()).rejects.toMatchObject({ status: 401 })
  })
})

function SessionProbe() {
  const { account: current, isRestoring, logout } = useAuth()
  return <><span>{isRestoring ? 'restoring' : current?.email ?? 'signed out'}</span><button onClick={() => void logout()}>Log out</button></>
}

describe('AuthProvider', () => {
  beforeEach(() => { clearSession(); document.cookie = 'XSRF-TOKEN=test-csrf; path=/' })
  afterEach(() => { clearSession(); vi.unstubAllGlobals(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/' })

  it('restores a cookie session and clears query state on logout', async () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['account', 'me'], account)
    mockFetch((input) => String(input).endsWith('/refresh') ? json(tokenResponse('restored-test-token')) : new Response(null, { status: 204 }))
    render(<QueryClientProvider client={queryClient}><AuthProvider><SessionProbe /></AuthProvider></QueryClientProvider>)
    expect(await screen.findByText(account.email)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))
    expect(await screen.findByText('signed out')).toBeInTheDocument()
    expect(queryClient.getQueryData(['account', 'me'])).toBeUndefined()
    await act(async () => { await Promise.resolve() })
  })

  it('finishes restoration after a failed refresh', async () => {
    mockFetch(() => json({ detail: 'No session' }, 401))
    render(<QueryClientProvider client={new QueryClient()}><AuthProvider><SessionProbe /></AuthProvider></QueryClientProvider>)
    expect(await screen.findByText('signed out')).toBeInTheDocument()
    await expect(refreshSession()).rejects.toMatchObject({ status: 401 })
  })
})

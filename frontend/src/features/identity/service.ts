import type { components } from '../../api/generated'
import { ApiError, responseBody } from './http'
import type { Account, LoginResult, ManagedRole, MessageResult, Session } from './types'

type LoginResponse = components['schemas']['LoginResponse']
type MfaRequiredResponse = components['schemas']['MfaRequiredResponse']
type TokenResponse = components['schemas']['TokenResponse']
type MessageResponse = components['schemas']['MessageResponse']
type ManagedAccountRequest = components['schemas']['ManagedAccountRequest']
type UpdateAccountRequest = components['schemas']['UpdateAccountRequest']

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: object
  signal?: AbortSignal
}

let accessToken: string | null = null
let csrfToken: string | null = null
let refreshRequest: Promise<Session> | null = null
const listeners = new Set<(session: Session | null) => void>()

function notify(session: Session | null) {
  accessToken = session?.accessToken ?? null
  listeners.forEach((listener) => listener(session))
}

function subscribe(listener: (session: Session | null) => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

function accountFrom(value: components['schemas']['AccountResponse'] | undefined): Account {
  if (!value || typeof value.id !== 'string' || typeof value.email !== 'string'
    || typeof value.firstName !== 'string' || typeof value.lastName !== 'string'
    || !['ADMIN', 'STAFF', 'PATIENT'].includes(value.role ?? '')
    || !['PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED'].includes(value.status ?? '')
    || typeof value.emailVerified !== 'boolean' || typeof value.forcePasswordChange !== 'boolean'
    || typeof value.authzVersion !== 'number' || typeof value.version !== 'number') {
    throw new Error('The server returned an invalid account session.')
  }
  return value as Account
}

function acceptSession(value: TokenResponse | LoginResponse): Session {
  if (typeof value.accessToken !== 'string' || typeof value.expiresIn !== 'number' || !value.account) {
    throw new Error('The server returned an invalid access token response.')
  }
  const session = { accessToken: value.accessToken, expiresIn: value.expiresIn, account: accountFrom(value.account) }
  notify(session)
  return session
}

function cookieValue(name: string): string | null {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find((value) => value.startsWith(prefix))
  if (!entry) return null
  try {
    return decodeURIComponent(entry.slice(prefix.length))
  } catch {
    return null
  }
}

async function ensureCsrf(signal?: AbortSignal): Promise<string> {
  const existing = cookieValue('XSRF-TOKEN') ?? csrfToken
  if (existing) {
    csrfToken = existing
    return existing
  }
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include', headers: { Accept: 'application/json' }, signal })
  const body = await responseBody<components['schemas']['CsrfResponse']>(response)
  if (typeof body.csrfToken !== 'string' || !body.csrfToken) throw new Error('The server did not provide a CSRF token.')
  csrfToken = body.csrfToken
  return csrfToken
}

async function request<T>(path: string, options: RequestOptions = {}, token?: string): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await ensureCsrf(options.signal)
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(path, {
    method,
    credentials: 'include',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })
  return responseBody<T>(response)
}

/** Retry only an authenticated 401. A 403 is an authorization failure, never a refresh trigger. */
export async function authorized<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!accessToken) throw new ApiError('Your session has expired. Sign in again.', 401)
  try {
    return await request<T>(path, options, accessToken)
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error
    const session = await refreshSession()
    return request<T>(path, options, session.accessToken)
  }
}

/** All concurrent callers share one cookie rotation request. */
export function refreshSession(): Promise<Session> {
  if (refreshRequest) return refreshRequest
  refreshRequest = request<TokenResponse>('/api/v1/auth/refresh', { method: 'POST' })
    .then(acceptSession)
    .catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 401) notify(null)
      throw error
    })
    .finally(() => { refreshRequest = null })
  return refreshRequest
}

export function clearSession() {
  notify(null)
}

export async function login(email: string, password: string): Promise<LoginResult> {
  const result = await request<LoginResponse | MfaRequiredResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password } })
  if ('mfaRequired' in result) return { mfaRequired: true, developmentCode: result.developmentCode }
  return { mfaRequired: false, session: acceptSession(result) }
}

export async function verifyMfa(email: string, code: string): Promise<Session> {
  return acceptSession(await request<TokenResponse>('/api/v1/auth/mfa/verify', { method: 'POST', body: { email, code } }))
}

export async function logout(): Promise<void> {
  try {
    await request<void>('/api/v1/auth/logout', { method: 'POST' })
  } finally {
    notify(null)
  }
}

export async function register(input: components['schemas']['RegisterRequest']): Promise<MessageResult> {
  return message(await request<MessageResponse>('/api/v1/auth/register', { method: 'POST', body: input }))
}

export async function requestVerification(email: string): Promise<MessageResult> {
  return message(await request<MessageResponse>('/api/v1/auth/verification/request', { method: 'POST', body: { email } }))
}

export async function confirmVerification(email: string, code: string): Promise<MessageResult> {
  return message(await request<MessageResponse>('/api/v1/auth/verification/confirm', { method: 'POST', body: { email, code } }))
}

export async function requestRecovery(email: string): Promise<MessageResult> {
  return message(await request<MessageResponse>('/api/v1/auth/recovery/request', { method: 'POST', body: { email } }))
}

export async function confirmRecovery(email: string, code: string, newPassword: string): Promise<MessageResult> {
  return message(await request<MessageResponse>('/api/v1/auth/recovery/confirm', { method: 'POST', body: { email, code, newPassword } }))
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<MessageResult> {
  const result = message(await authorized<MessageResponse>('/api/v1/auth/change-password', {
    method: 'POST', body: { currentPassword, newPassword },
  }))
  notify(null)
  return result
}

export function currentAccount(signal?: AbortSignal): Promise<Account> {
  return authorized<components['schemas']['AccountResponse']>('/api/v1/accounts/me', { signal }).then(accountFrom)
}

export function createManagedAccount(input: Omit<ManagedAccountRequest, 'role'> & { role: ManagedRole }): Promise<Account> {
  return authorized<components['schemas']['AccountResponse']>('/api/v1/accounts', { method: 'POST', body: input }).then(accountFrom)
}

export function updateManagedAccount(accountId: string, input: UpdateAccountRequest): Promise<Account> {
  return authorized<components['schemas']['AccountResponse']>(`/api/v1/accounts/${encodeURIComponent(accountId)}`, {
    method: 'PATCH', body: input,
  }).then(accountFrom)
}

function message(value: MessageResponse): MessageResult {
  if (typeof value.message !== 'string') throw new Error('The server returned an invalid response.')
  return { message: value.message, developmentCode: value.developmentCode }
}

export const sessionStore = { subscribe }

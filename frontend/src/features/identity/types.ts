import type { components } from '../../api/generated'

export type Account = Required<components['schemas']['AccountResponse']>
export type Role = Account['role']
export type ManagedRole = Extract<Role, 'STAFF' | 'PATIENT'>

export interface Session {
  accessToken: string
  expiresIn: number
  account: Account
}

export type LoginResult =
  | { mfaRequired: true; developmentCode?: string }
  | { mfaRequired: false; session: Session }

export interface MessageResult {
  message: string
  developmentCode?: string
}

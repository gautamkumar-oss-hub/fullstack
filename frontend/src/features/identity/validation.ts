import { z } from 'zod'

export const email = z.string().trim().email('Enter a valid email address.').max(320)
export const password = z.string()
  .min(12, 'Use at least 12 characters.')
  .max(128)
  .regex(/[A-Za-z]/, 'Include at least one letter.')
  .regex(/[0-9]/, 'Include at least one number.')
export const code = z.string().trim().regex(/^[0-9]{6}$/, 'Enter the six-digit code.')
export const personName = z.string().trim().min(1, 'This field is required.').max(100)
export const phone = z.string().trim().max(40).optional()

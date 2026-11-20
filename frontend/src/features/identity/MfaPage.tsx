import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { DevelopmentCode, FormError, FormField } from './FormControls'
import { code, email } from './validation'

const schema = z.object({ email, code })
type Values = z.infer<typeof schema>

export function MfaPage() {
  const { verifyMfa } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { email?: string; developmentCode?: string } | null
  const [error, setError] = useState<unknown>(null)
  const methods = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: state?.email ?? '', code: '' } })
  if (!state?.email) return <Navigate to="/login" replace />

  const submit = methods.handleSubmit(async (values) => {
    setError(null)
    try {
      const session = await verifyMfa(values.email, values.code)
      navigate(session.account.forcePasswordChange ? '/account/password' : '/account', { replace: true })
    } catch (reason) {
      setError(reason)
    }
  })

  return (
    <AuthLayout title="Confirm it’s you" description="Enter the six-digit code sent to your email. It expires after five minutes.">
      <FormProvider {...methods}>
        <Stack component="form" onSubmit={submit} noValidate spacing={2.5}>
          <Typography color="text.secondary">{state.email}</Typography>
          <DevelopmentCode>{state.developmentCode}</DevelopmentCode>
          <FormError error={error} />
          <FormField<Values> name="email" label="Email address" type="email" required fullWidth slotProps={{ htmlInput: { readOnly: true } }} />
          <FormField<Values> name="code" label="Six-digit code" inputMode="numeric" autoComplete="one-time-code" required fullWidth autoFocus
            slotProps={{ htmlInput: { maxLength: 6, pattern: '[0-9]{6}' } }} />
          <Button type="submit" variant="contained" size="large" disabled={methods.formState.isSubmitting}>
            {methods.formState.isSubmitting ? 'Verifying…' : 'Verify and sign in'}
          </Button>
        </Stack>
      </FormProvider>
    </AuthLayout>
  )
}

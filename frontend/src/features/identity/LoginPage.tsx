import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Link as MuiLink, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { FormError, FormField } from './FormControls'
import { email } from './validation'

const schema = z.object({ email, password: z.string().min(1, 'Enter your password.').max(128) })
type Values = z.infer<typeof schema>

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<unknown>(null)
  const methods = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })
  const emailValue = methods.watch('email')

  const submit = methods.handleSubmit(async (values) => {
    setError(null)
    try {
      const result = await login(values.email, values.password)
      if (result.mfaRequired) {
        navigate('/mfa', { state: { email: values.email, developmentCode: result.developmentCode } })
      } else if (result.session.account.forcePasswordChange) {
        navigate('/account/password', { replace: true })
      } else {
        navigate('/workspace', { replace: true })
      }
    } catch (reason) {
      setError(reason)
    }
  })

  return (
    <AuthLayout title="Welcome back" description="Sign in to securely access your hospital account."
      footer={<><MuiLink component={Link} to="/register">Create a patient account</MuiLink><MuiLink component={Link} to="/recover">Forgot your password?</MuiLink></>}>
      <FormProvider {...methods}>
        <Stack component="form" onSubmit={submit} noValidate spacing={2.5} aria-busy={methods.formState.isSubmitting}>
          <FormError error={error} />
          <FormField<Values> name="email" label="Email address" type="email" autoComplete="email" required fullWidth />
          <FormField<Values> name="password" label="Password" type="password" autoComplete="current-password" required fullWidth />
          <Button type="submit" variant="contained" size="large" disabled={methods.formState.isSubmitting}>
            {methods.formState.isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
          <MuiLink component={Link} to="/verify" state={{ email: emailValue }}>Verify your email address</MuiLink>
          <Typography variant="body2" color="text.secondary">Staff and administrators complete an additional six-digit verification step.</Typography>
        </Stack>
      </FormProvider>
    </AuthLayout>
  )
}

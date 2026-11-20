import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link as MuiLink, Stack } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { AuthLayout } from './AuthLayout'
import { DevelopmentCode, FormError, FormField } from './FormControls'
import { register } from './service'
import { email, password, personName, phone } from './validation'

const schema = z.object({ email, password, firstName: personName, lastName: personName, phone })
type Values = z.infer<typeof schema>

export function RegisterPage() {
  const navigate = useNavigate()
  const [result, setResult] = useState<{ message: string; developmentCode?: string } | null>(null)
  const [error, setError] = useState<unknown>(null)
  const methods = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '', firstName: '', lastName: '', phone: '' } })
  const submit = methods.handleSubmit(async (values) => {
    setError(null)
    try { setResult(await register(values)) } catch (reason) { setError(reason) }
  })

  return (
    <AuthLayout title="Create your patient account" description="Register, verify your email, and sign in securely."
      footer={<MuiLink component={Link} to="/login">Already registered? Sign in</MuiLink>}>
      {result ? (
        <Stack spacing={2.5}>
          <Alert severity="success">{result.message}</Alert>
          <DevelopmentCode>{result.developmentCode}</DevelopmentCode>
          <Button variant="contained" onClick={() => navigate('/verify', { state: { email: methods.getValues('email'), developmentCode: result.developmentCode } })}>Verify email</Button>
        </Stack>
      ) : (
        <FormProvider {...methods}>
          <Stack component="form" onSubmit={submit} noValidate spacing={2.5}>
            <FormError error={error} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <FormField<Values> name="firstName" label="First name" autoComplete="given-name" required fullWidth />
              <FormField<Values> name="lastName" label="Last name" autoComplete="family-name" required fullWidth />
            </Stack>
            <FormField<Values> name="email" label="Email address" type="email" autoComplete="email" required fullWidth />
            <FormField<Values> name="phone" label="Phone (optional)" type="tel" autoComplete="tel" fullWidth />
            <FormField<Values> name="password" label="Password" type="password" autoComplete="new-password" required fullWidth helperText="At least 12 characters, including a letter and a number." />
            <Button type="submit" variant="contained" size="large" disabled={methods.formState.isSubmitting}>Create patient account</Button>
          </Stack>
        </FormProvider>
      )}
    </AuthLayout>
  )
}

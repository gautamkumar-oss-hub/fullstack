import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link as MuiLink, Stack } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Link, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { AuthLayout } from './AuthLayout'
import { DevelopmentCode, FormError, FormField } from './FormControls'
import { confirmVerification, requestVerification } from './service'
import { code, email } from './validation'

const schema = z.object({ email, code })
type Values = z.infer<typeof schema>

export function VerificationPage() {
  const location = useLocation()
  const state = location.state as { email?: string; developmentCode?: string } | null
  const [message, setMessage] = useState<string>()
  const [developmentCode, setDevelopmentCode] = useState(state?.developmentCode)
  const [error, setError] = useState<unknown>(null)
  const [verified, setVerified] = useState(false)
  const methods = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: state?.email ?? '', code: '' } })

  const submit = methods.handleSubmit(async (values) => {
    setError(null)
    try {
      const result = await confirmVerification(values.email, values.code)
      setMessage(result.message)
      setVerified(true)
    } catch (reason) { setError(reason) }
  })

  const resend = async () => {
    if (!await methods.trigger('email')) return
    setError(null)
    try {
      const result = await requestVerification(methods.getValues('email'))
      setMessage(result.message)
      setDevelopmentCode(result.developmentCode)
    } catch (reason) { setError(reason) }
  }

  return (
    <AuthLayout title="Verify your email" description="Use the six-digit verification code sent to your email address."
      footer={<MuiLink component={Link} to="/login">Return to sign in</MuiLink>}>
      {verified ? <Alert severity="success">{message}</Alert> : (
        <FormProvider {...methods}>
          <Stack component="form" onSubmit={submit} noValidate spacing={2.5}>
            {message && <Alert severity="info">{message}</Alert>}
            <DevelopmentCode>{developmentCode}</DevelopmentCode>
            <FormError error={error} />
            <FormField<Values> name="email" label="Email address" type="email" autoComplete="email" required fullWidth />
            <FormField<Values> name="code" label="Six-digit code" inputMode="numeric" autoComplete="one-time-code" required fullWidth />
            <Button type="submit" variant="contained" disabled={methods.formState.isSubmitting}>Verify email</Button>
            <Button type="button" variant="text" onClick={() => void resend()}>Send another code</Button>
          </Stack>
        </FormProvider>
      )}
    </AuthLayout>
  )
}

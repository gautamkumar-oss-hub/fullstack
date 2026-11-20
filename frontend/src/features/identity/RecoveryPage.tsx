import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link as MuiLink, Stack } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { AuthLayout } from './AuthLayout'
import { DevelopmentCode, FormError, FormField } from './FormControls'
import { confirmRecovery, requestRecovery } from './service'
import { code, email, password } from './validation'

const requestSchema = z.object({ email })
const resetSchema = z.object({ email, code, newPassword: password })
type RequestValues = z.infer<typeof requestSchema>
type ResetValues = z.infer<typeof resetSchema>

export function RecoveryPage() {
  const [requestedEmail, setRequestedEmail] = useState<string>()
  const [result, setResult] = useState<{ message: string; developmentCode?: string } | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [complete, setComplete] = useState(false)
  const requestForm = useForm<RequestValues>({ resolver: zodResolver(requestSchema), defaultValues: { email: '' } })
  const resetForm = useForm<ResetValues>({ resolver: zodResolver(resetSchema), defaultValues: { email: '', code: '', newPassword: '' } })

  const requestCode = requestForm.handleSubmit(async (values) => {
    setError(null)
    try {
      const response = await requestRecovery(values.email)
      setRequestedEmail(values.email)
      setResult(response)
      resetForm.setValue('email', values.email)
    } catch (reason) { setError(reason) }
  })
  const reset = resetForm.handleSubmit(async (values) => {
    setError(null)
    try { setResult(await confirmRecovery(values.email, values.code, values.newPassword)); setComplete(true) } catch (reason) { setError(reason) }
  })

  return (
    <AuthLayout title="Recover your account" description="Request a recovery code, then choose a new password."
      footer={<MuiLink component={Link} to="/login">Return to sign in</MuiLink>}>
      {result && <Alert severity={complete ? 'success' : 'info'}>{result.message}</Alert>}
      <DevelopmentCode>{result?.developmentCode}</DevelopmentCode>
      <FormError error={error} />
      {!requestedEmail ? (
        <FormProvider {...requestForm}>
          <Stack component="form" onSubmit={requestCode} noValidate spacing={2.5}>
            <FormField<RequestValues> name="email" label="Email address" type="email" autoComplete="email" required fullWidth />
            <Button type="submit" variant="contained" disabled={requestForm.formState.isSubmitting}>Send recovery code</Button>
          </Stack>
        </FormProvider>
      ) : !complete ? (
        <FormProvider {...resetForm}>
          <Stack component="form" onSubmit={reset} noValidate spacing={2.5}>
            <FormField<ResetValues> name="email" label="Email address" required fullWidth slotProps={{ htmlInput: { readOnly: true } }} />
            <FormField<ResetValues> name="code" label="Six-digit recovery code" inputMode="numeric" autoComplete="one-time-code" required fullWidth />
            <FormField<ResetValues> name="newPassword" label="New password" type="password" autoComplete="new-password" required fullWidth helperText="At least 12 characters, including a letter and a number." />
            <Button type="submit" variant="contained" disabled={resetForm.formState.isSubmitting}>Set new password</Button>
          </Stack>
        </FormProvider>
      ) : <Button component={Link} to="/login" variant="contained">Sign in with your new password</Button>}
    </AuthLayout>
  )
}

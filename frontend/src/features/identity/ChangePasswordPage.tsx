import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Paper, Stack, Typography } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from './AuthContext'
import { FormError, FormField } from './FormControls'
import { changePassword } from './service'
import { password } from './validation'

const schema = z.object({ currentPassword: z.string().min(1, 'Enter your current password.').max(128), newPassword: password })
type Values = z.infer<typeof schema>

export function ChangePasswordPage() {
  const { account } = useAuth()
  const queryClient = useQueryClient()
  const [result, setResult] = useState<string>()
  const [error, setError] = useState<unknown>(null)
  const methods = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { currentPassword: '', newPassword: '' } })
  const submit = methods.handleSubmit(async (values) => {
    setError(null)
    try {
      const response = await changePassword(values.currentPassword, values.newPassword)
      queryClient.clear()
      setResult(response.message)
    } catch (reason) { setError(reason) }
  })

  return (
    <Stack spacing={3}>
      <Stack spacing={1}>
        <Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Change your password</Typography>
        <Typography color="text.secondary">{account?.forcePasswordChange ? 'A password change is required before you can access your account.' : 'Choose a new password for your account.'}</Typography>
      </Stack>
      <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, maxWidth: 600 }}>
        {result ? <Stack spacing={2}><Alert severity="success">{result} All existing sessions have been revoked.</Alert><Button component={Link} to="/login" variant="contained">Sign in again</Button></Stack> : (
          <FormProvider {...methods}>
            <Stack component="form" onSubmit={submit} noValidate spacing={2.5}>
              <FormError error={error} />
              <FormField<Values> name="currentPassword" label="Current password" type="password" autoComplete="current-password" required fullWidth />
              <FormField<Values> name="newPassword" label="New password" type="password" autoComplete="new-password" required fullWidth helperText="At least 12 characters, including a letter and a number." />
              <Button type="submit" variant="contained" disabled={methods.formState.isSubmitting}>Change password</Button>
            </Stack>
          </FormProvider>
        )}
      </Paper>
    </Stack>
  )
}

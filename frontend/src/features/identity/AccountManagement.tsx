import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Divider, MenuItem, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { z } from 'zod'
import { FormError, FormField } from './FormControls'
import { createManagedAccount, updateManagedAccount } from './service'
import { email, password, personName, phone } from './validation'

const createSchema = z.object({ email, password, role: z.enum(['STAFF', 'PATIENT']), firstName: personName, lastName: personName, phone })
const updateSchema = z.object({
  accountId: z.uuid('Enter the account UUID.'), role: z.enum(['STAFF', 'PATIENT']), status: z.enum(['PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED']),
  firstName: personName, lastName: personName, phone, newPassword: z.union([z.literal(''), password]),
  version: z.string().regex(/^[0-9]+$/, 'Enter the current account version.'),
})
type CreateValues = z.infer<typeof createSchema>
type UpdateValues = z.infer<typeof updateSchema>

export function AccountManagement() {
  const [created, setCreated] = useState<{ id: string; version: number } | null>(null)
  const [updated, setUpdated] = useState<string>()
  const [createError, setCreateError] = useState<unknown>(null)
  const [updateError, setUpdateError] = useState<unknown>(null)
  const createForm = useForm<CreateValues>({ resolver: zodResolver(createSchema), defaultValues: { email: '', password: '', role: 'STAFF', firstName: '', lastName: '', phone: '' } })
  const updateForm = useForm<UpdateValues>({ resolver: zodResolver(updateSchema), defaultValues: { accountId: '', role: 'STAFF', status: 'ACTIVE', firstName: '', lastName: '', phone: '', newPassword: '', version: '0' } })

  const create = createForm.handleSubmit(async (values) => {
    setCreateError(null)
    try {
      const account = await createManagedAccount(values)
      if (!account.id || account.version === undefined) throw new Error('The server returned an invalid account.')
      setCreated({ id: account.id, version: account.version })
      updateForm.reset({ ...updateForm.getValues(), accountId: account.id, role: account.role === 'PATIENT' ? 'PATIENT' : 'STAFF', status: account.status, firstName: account.firstName ?? '', lastName: account.lastName ?? '', version: String(account.version) })
    } catch (reason) { setCreateError(reason) }
  })
  const update = updateForm.handleSubmit(async (values) => {
    setUpdateError(null)
    try {
      const account = await updateManagedAccount(values.accountId, {
        role: values.role, status: values.status, firstName: values.firstName, lastName: values.lastName,
        phone: values.phone, newPassword: values.newPassword || undefined, version: Number(values.version),
      })
      updateForm.setValue('version', String(account.version ?? Number(values.version)))
      setUpdated('The account was updated. Existing sessions were revoked if its role, status, or password changed.')
    } catch (reason) { setUpdateError(reason) }
  })

  return (
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <Typography variant="h5" component="h2">Account administration</Typography>
          <Typography color="text.secondary">Create staff or patient accounts. Administrator accounts are created only through the controlled first-admin bootstrap.</Typography>
        </Stack>
        {created && <Alert severity="success">Account created. For later management, its ID is <strong>{created.id}</strong> and its current version is <strong>{created.version}</strong>.</Alert>}
        <FormProvider {...createForm}>
          <Stack component="form" onSubmit={create} noValidate spacing={2} aria-label="Create an account">
            <FormError error={createError} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <FormField<CreateValues> name="firstName" label="First name" required fullWidth />
              <FormField<CreateValues> name="lastName" label="Last name" required fullWidth />
            </Stack>
            <FormField<CreateValues> name="email" label="Email address" type="email" required fullWidth />
            <FormField<CreateValues> name="phone" label="Phone (optional)" fullWidth />
            <FormField<CreateValues> name="role" label="Role" select required fullWidth>
              <MenuItem value="STAFF">Staff</MenuItem><MenuItem value="PATIENT">Patient</MenuItem>
            </FormField>
            <FormField<CreateValues> name="password" label="Initial password" type="password" autoComplete="new-password" required fullWidth helperText="Staff must change this password after their first sign-in." />
            <Button type="submit" variant="contained" disabled={createForm.formState.isSubmitting}>Create account</Button>
          </Stack>
        </FormProvider>
        <Divider />
        <Typography variant="h6" component="h3">Update a known account</Typography>
        <Typography variant="body2" color="text.secondary">Enter the account ID and its current optimistic version. This phase intentionally provides no account directory or clinical records.</Typography>
        {updated && <Alert severity="success">{updated}</Alert>}
        <FormProvider {...updateForm}>
          <Stack component="form" onSubmit={update} noValidate spacing={2} aria-label="Update an account">
            <FormError error={updateError} />
            <FormField<UpdateValues> name="accountId" label="Account UUID" required fullWidth />
            <FormField<UpdateValues> name="version" label="Current version" type="number" required fullWidth slotProps={{ htmlInput: { min: 0, step: 1 } }} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <FormField<UpdateValues> name="firstName" label="First name" required fullWidth />
              <FormField<UpdateValues> name="lastName" label="Last name" required fullWidth />
            </Stack>
            <FormField<UpdateValues> name="phone" label="Phone (optional)" fullWidth />
            <FormField<UpdateValues> name="role" label="Role" select required fullWidth>
              <MenuItem value="STAFF">Staff</MenuItem><MenuItem value="PATIENT">Patient</MenuItem>
            </FormField>
            <FormField<UpdateValues> name="status" label="Status" select required fullWidth>
              <MenuItem value="ACTIVE">Active</MenuItem><MenuItem value="PENDING_VERIFICATION">Pending verification</MenuItem><MenuItem value="SUSPENDED">Suspended</MenuItem>
            </FormField>
            <FormField<UpdateValues> name="newPassword" label="Replacement password (optional)" type="password" autoComplete="new-password" fullWidth helperText="Leave blank to keep the current password." />
            <Button type="submit" variant="outlined" disabled={updateForm.formState.isSubmitting}>Update account</Button>
          </Stack>
        </FormProvider>
      </Stack>
    </Paper>
  )
}

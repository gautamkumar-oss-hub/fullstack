import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, CircularProgress, Paper, Stack, Typography, Button } from '@mui/material'
import { useEffect } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { z } from 'zod'
import { FormError, FormField } from '../identity/FormControls'
import { schedulingApi } from './api'

const schema = z.object({
  bookingHorizonDays: z.coerce.number().int().min(1).max(365),
  proposalHoldHours: z.coerce.number().int().min(1).max(168),
  patientChangeCutoffHours: z.coerce.number().int().min(1).max(720),
})
type Values = z.input<typeof schema>

export function ConfigurationPage() {
  const queryClient = useQueryClient()
  const configuration = useQuery({ queryKey: ['configuration'], queryFn: ({ signal }) => schedulingApi.configuration(signal) })
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { bookingHorizonDays: 90, proposalHoldHours: 24, patientChangeCutoffHours: 24 } })
  useEffect(() => { if (configuration.data) form.reset(configuration.data) }, [configuration.data, form])
  const update = useMutation({
    mutationFn: (values: Values) => schedulingApi.updateConfiguration({ ...configuration.data!, ...values, bookingHorizonDays: Number(values.bookingHorizonDays), proposalHoldHours: Number(values.proposalHoldHours), patientChangeCutoffHours: Number(values.patientChangeCutoffHours) }),
    onSuccess: async (value) => { form.reset(value); await queryClient.invalidateQueries({ queryKey: ['configuration'] }); await queryClient.invalidateQueries({ queryKey: ['system', 'info'] }) },
  })
  return <Stack spacing={3}>
    <Stack spacing={1}><Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>Clinic operations</Typography><Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Scheduling configuration</Typography>
      <Typography color="text.secondary">Changes apply to subsequent operations. The clinic time zone is a validated deployment setting and cannot be changed here.</Typography></Stack>
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, maxWidth: 700 }}><Stack spacing={2}>
      {configuration.isPending && <CircularProgress aria-label="Loading configuration" />}<FormError error={configuration.error} />
      {configuration.data && <><Alert severity="info">Clinic time zone: <strong>{configuration.data.clinicTimeZone}</strong> · Optimistic version {configuration.data.version}</Alert>
        <FormProvider {...form}><Stack component="form" onSubmit={form.handleSubmit((values) => update.mutate(values))} spacing={2} aria-label="Update clinic configuration" noValidate>
          <FormError error={update.error} />
          <FormField<Values> name="bookingHorizonDays" label="Booking horizon (days)" type="number" required fullWidth slotProps={{ htmlInput: { min: 1, max: 365 } }} />
          <FormField<Values> name="proposalHoldHours" label="Proposal hold (hours)" type="number" required fullWidth slotProps={{ htmlInput: { min: 1, max: 168 } }} />
          <FormField<Values> name="patientChangeCutoffHours" label="Patient change cutoff (hours)" type="number" required fullWidth slotProps={{ htmlInput: { min: 1, max: 720 } }} />
          <Button type="submit" variant="contained" disabled={update.isPending}>Save configuration</Button>
        </Stack></FormProvider></>}
    </Stack></Paper>
  </Stack>
}

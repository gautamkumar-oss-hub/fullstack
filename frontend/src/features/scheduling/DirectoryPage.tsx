import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Divider, FormControlLabel, MenuItem, Paper, Stack, Switch, Typography } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { z } from 'zod'
import { FormError, FormField } from '../identity/FormControls'
import { schedulingApi, type Doctor, type Specialization } from './api'

const specializationSchema = z.object({ name: z.string().trim().min(1, 'Enter a specialization name.').max(100, 'Use at most 100 characters.') })
const doctorSchema = z.object({
  specializationId: z.uuid('Select a specialization.'), firstName: z.string().trim().min(1, 'Enter a first name.').max(100),
  lastName: z.string().trim().min(1, 'Enter a last name.').max(100),
  slotDurationMinutes: z.coerce.number().int().min(5, 'At least 5 minutes.').max(180, 'At most 180 minutes.').refine((value) => value % 5 === 0, 'Use five-minute increments.'),
})
type SpecializationValues = z.infer<typeof specializationSchema>
type DoctorValues = z.input<typeof doctorSchema>

export function DirectoryPage() {
  const [activeOnly, setActiveOnly] = useState(true)
  const [specialization, setSpecialization] = useState<Specialization | null>(null)
  const [doctor, setDoctor] = useState<Doctor | null>(null)
  const queryClient = useQueryClient()
  const specializations = useQuery({ queryKey: ['specializations', activeOnly], queryFn: ({ signal }) => schedulingApi.specializations(activeOnly, signal) })
  const doctors = useQuery({ queryKey: ['doctors', activeOnly], queryFn: ({ signal }) => schedulingApi.doctors(activeOnly, signal) })
  const specializationForm = useForm<SpecializationValues>({ resolver: zodResolver(specializationSchema), defaultValues: { name: '' } })
  const doctorForm = useForm<DoctorValues>({ resolver: zodResolver(doctorSchema), defaultValues: { specializationId: '', firstName: '', lastName: '', slotDurationMinutes: 30 } })
  const invalidate = async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['specializations'] }), queryClient.invalidateQueries({ queryKey: ['doctors'] })]) }
  const saveSpecialization = useMutation({
    mutationFn: (values: SpecializationValues) => specialization ? schedulingApi.updateSpecialization({ ...specialization, name: values.name }) : schedulingApi.createSpecialization(values.name),
    onSuccess: async () => { setSpecialization(null); specializationForm.reset({ name: '' }); await invalidate() },
  })
  const saveDoctor = useMutation({
    mutationFn: (values: DoctorValues) => {
      const input = { specializationId: values.specializationId, firstName: values.firstName, lastName: values.lastName, slotDurationMinutes: Number(values.slotDurationMinutes) }
      return doctor ? schedulingApi.updateDoctor({ ...doctor, ...input }) : schedulingApi.createDoctor(input)
    },
    onSuccess: async () => { setDoctor(null); doctorForm.reset(); await invalidate() },
  })
  const deactivate = useMutation<Specialization | Doctor, Error, { type: 'specialization' | 'doctor'; value: Specialization | Doctor }>({
    mutationFn: ({ type, value }) => type === 'specialization'
      ? schedulingApi.deactivateSpecialization(value as Specialization) : schedulingApi.deactivateDoctor(value as Doctor),
    onSuccess: invalidate,
  })
  const editSpecialization = (value: Specialization) => { setSpecialization(value); specializationForm.reset({ name: value.name }) }
  const editDoctor = (value: Doctor) => { setDoctor(value); doctorForm.reset({ specializationId: value.specializationId, firstName: value.firstName, lastName: value.lastName, slotDurationMinutes: value.slotDurationMinutes }) }

  return (
    <Stack spacing={3}>
      <Stack spacing={1}>
        <Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>Clinical directory</Typography>
        <Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Doctors and specializations</Typography>
        <Typography color="text.secondary">Directory records are not user accounts. Each doctor has exactly one specialization and a fixed appointment-slot duration.</Typography>
      </Stack>
      <FormControlLabel control={<Switch checked={activeOnly} onChange={(_, checked) => setActiveOnly(checked)} />} label="Show active records only" />
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={3} sx={{ alignItems: 'flex-start' }}>
        <Paper variant="outlined" sx={{ p: 3, flex: 1, width: '100%' }}>
          <Stack spacing={2}>
            <Typography variant="h5" component="h2">Specializations</Typography>
            <FormProvider {...specializationForm}>
              <Stack component="form" onSubmit={specializationForm.handleSubmit((values) => saveSpecialization.mutate(values))} spacing={2} aria-label={specialization ? 'Update specialization' : 'Create specialization'}>
                <FormError error={saveSpecialization.error} />
                <FormField<SpecializationValues> name="name" label="Specialization name" required fullWidth />
                <Stack direction="row" spacing={1}><Button type="submit" variant="contained" disabled={saveSpecialization.isPending}>{specialization ? 'Update specialization' : 'Create specialization'}</Button>{specialization && <Button onClick={() => { setSpecialization(null); specializationForm.reset({ name: '' }) }}>Cancel</Button>}</Stack>
              </Stack>
            </FormProvider>
            <Divider />
            {specializations.isPending && <CircularProgress aria-label="Loading specializations" />}
            <FormError error={specializations.error} />
            {specializations.data?.length === 0 && <Alert severity="info">No specializations match this filter.</Alert>}
            {specializations.data?.map((value) => <RecordRow key={value.id} title={value.name} detail={`Version ${value.version}`} active={value.active}
              onEdit={() => editSpecialization(value)} onDeactivate={() => deactivate.mutate({ type: 'specialization', value })} />)}
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ p: 3, flex: 1.4, width: '100%' }}>
          <Stack spacing={2}>
            <Typography variant="h5" component="h2">Doctors</Typography>
            <FormProvider {...doctorForm}>
              <Stack component="form" onSubmit={doctorForm.handleSubmit((values) => saveDoctor.mutate(values))} spacing={2} aria-label={doctor ? 'Update doctor' : 'Create doctor'}>
                <FormError error={saveDoctor.error} />
                <FormField<DoctorValues> name="specializationId" label="Specialization" select required fullWidth>
                  {specializations.data?.filter((item) => item.active).map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
                </FormField>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><FormField<DoctorValues> name="firstName" label="First name" required fullWidth /><FormField<DoctorValues> name="lastName" label="Last name" required fullWidth /></Stack>
                <FormField<DoctorValues> name="slotDurationMinutes" label="Fixed slot duration (minutes)" type="number" required fullWidth slotProps={{ htmlInput: { min: 5, max: 180, step: 5 } }} />
                <Stack direction="row" spacing={1}><Button type="submit" variant="contained" disabled={saveDoctor.isPending}>{doctor ? 'Update doctor' : 'Create doctor'}</Button>{doctor && <Button onClick={() => { setDoctor(null); doctorForm.reset() }}>Cancel</Button>}</Stack>
              </Stack>
            </FormProvider>
            <Divider />
            {doctors.isPending && <CircularProgress aria-label="Loading doctors" />}
            <FormError error={doctors.error} />
            {doctors.data?.length === 0 && <Alert severity="info">No doctors match this filter.</Alert>}
            {doctors.data?.map((value) => <RecordRow key={value.id} title={`${value.firstName} ${value.lastName}`} detail={`${specializations.data?.find((item) => item.id === value.specializationId)?.name ?? 'Specialization'} · ${value.slotDurationMinutes} min · Version ${value.version}`} active={value.active}
              onEdit={() => editDoctor(value)} onDeactivate={() => deactivate.mutate({ type: 'doctor', value })} />)}
            <FormError error={deactivate.error} />
          </Stack>
        </Paper>
      </Stack>
    </Stack>
  )
}

function RecordRow({ title, detail, active, onEdit, onDeactivate }: { title: string; detail: string; active: boolean; onEdit: () => void; onDeactivate: () => void }) {
  return <Box sx={{ py: 1 }}><Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
    <Box><Typography sx={{ fontWeight: 600 }}>{title}</Typography><Typography variant="body2" color="text.secondary">{detail}</Typography></Box>
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}><Chip label={active ? 'Active' : 'Inactive'} color={active ? 'success' : 'default'} size="small" />
      {active && <><Button size="small" onClick={onEdit} aria-label={`Edit ${title}`}>Edit</Button><Button size="small" color="error" onClick={onDeactivate} aria-label={`Deactivate ${title}`}>Deactivate</Button></>}
    </Stack></Stack><Divider sx={{ mt: 1 }} /></Box>
}

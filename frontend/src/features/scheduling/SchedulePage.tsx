import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, CircularProgress, Divider, MenuItem, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { z } from 'zod'
import { FormError, FormField } from '../identity/FormControls'
import { schedulingApi, type ScheduleOverride, type WeeklyRule } from './api'

const weekdays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const
const localTime = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Enter a clinic-local time.')
const weeklySchema = z.object({ weekday: z.enum(weekdays), startLocal: localTime, endLocal: localTime, effectiveFrom: z.iso.date(), effectiveTo: z.iso.date() })
  .refine((value) => value.startLocal < value.endLocal, { message: 'Start time must be before end time.', path: ['endLocal'] })
  .refine((value) => value.effectiveFrom <= value.effectiveTo, { message: 'Effective start must not be after its end.', path: ['effectiveTo'] })
const overrideSchema = z.object({ date: z.iso.date(), type: z.enum(['OPEN', 'CLOSED', 'LEAVE']), startLocal: z.string(), endLocal: z.string(), reason: z.string().max(500) })
  .superRefine((value, context) => {
    if (value.type === 'OPEN' && (!localTime.safeParse(value.startLocal).success || !localTime.safeParse(value.endLocal).success)) context.addIssue({ code: 'custom', message: 'OPEN overrides require valid start and end times.', path: ['startLocal'] })
    if ((value.startLocal || value.endLocal) && (!localTime.safeParse(value.startLocal).success || !localTime.safeParse(value.endLocal).success)) context.addIssue({ code: 'custom', message: 'Provide both bounds or leave both blank.', path: ['endLocal'] })
    if (value.startLocal && value.endLocal && value.startLocal >= value.endLocal) context.addIssue({ code: 'custom', message: 'Start time must be before end time.', path: ['endLocal'] })
  })
type WeeklyValues = z.infer<typeof weeklySchema>
type OverrideValues = z.infer<typeof overrideSchema>

export function SchedulePage() {
  const [doctorId, setDoctorId] = useState('')
  const [weekly, setWeekly] = useState<WeeklyRule | null>(null)
  const [override, setOverride] = useState<ScheduleOverride | null>(null)
  const queryClient = useQueryClient()
  const doctors = useQuery({ queryKey: ['doctors', true], queryFn: ({ signal }) => schedulingApi.doctors(true, signal) })
  const rules = useQuery({ queryKey: ['schedule', doctorId], queryFn: ({ signal }) => schedulingApi.weeklyRules(doctorId, signal), enabled: !!doctorId })
  const overrides = useQuery({ queryKey: ['overrides', doctorId], queryFn: ({ signal }) => schedulingApi.overrides(doctorId, signal), enabled: !!doctorId })
  const weeklyForm = useForm<WeeklyValues>({ resolver: zodResolver(weeklySchema), defaultValues: { weekday: 'MONDAY', startLocal: '09:00', endLocal: '12:00', effectiveFrom: '', effectiveTo: '' } })
  const overrideForm = useForm<OverrideValues>({ resolver: zodResolver(overrideSchema), defaultValues: { date: '', type: 'CLOSED', startLocal: '', endLocal: '', reason: '' } })
  const invalidate = async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['schedule', doctorId] }), queryClient.invalidateQueries({ queryKey: ['overrides', doctorId] })]) }
  const saveWeekly = useMutation({
    mutationFn: (values: WeeklyValues) => weekly ? schedulingApi.updateWeekly(doctorId, { ...weekly, ...values }) : schedulingApi.createWeekly(doctorId, values),
    onSuccess: async () => { setWeekly(null); weeklyForm.reset(); await invalidate() },
  })
  const saveOverride = useMutation({
    mutationFn: (values: OverrideValues) => {
      const input = { ...values, startLocal: values.startLocal || undefined, endLocal: values.endLocal || undefined, reason: values.reason || undefined }
      return override ? schedulingApi.updateOverride(doctorId, { ...override, ...input }) : schedulingApi.createOverride(doctorId, input)
    },
    onSuccess: async () => { setOverride(null); overrideForm.reset(); await invalidate() },
  })
  const selectDoctor = (value: string) => { setDoctorId(value); setWeekly(null); setOverride(null); weeklyForm.reset(); overrideForm.reset() }

  return <Stack spacing={3}>
    <Stack spacing={1}><Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>Scheduling administration</Typography><Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Weekly availability and overrides</Typography>
      <Typography color="text.secondary">All entered times are clinic-local. Gaps and ambiguous repeated times are rejected; effective periods and time ranges cannot overlap for the same schedule.</Typography></Stack>
    <Alert severity="info">Schedule changes are preview-only with respect to existing allocations. Allocation conflict enforcement is unavailable until allocation records are introduced in Phase 5.</Alert>
    <FormFieldSelect label="Doctor" value={doctorId} onChange={selectDoctor} options={doctors.data?.map((doctor) => ({ value: doctor.id, label: `${doctor.firstName} ${doctor.lastName}` })) ?? []} loading={doctors.isPending} />
    <FormError error={doctors.error} />
    {!doctorId && <Alert severity="info">Select an active doctor to manage schedule rules.</Alert>}
    {doctorId && <Stack direction={{ xs: 'column', lg: 'row' }} spacing={3} sx={{ alignItems: 'flex-start' }}>
      <Paper variant="outlined" sx={{ p: 3, flex: 1, width: '100%' }}><Stack spacing={2}>
        <Typography variant="h5" component="h2">Recurring weekly rules</Typography>
        <FormProvider {...weeklyForm}><Stack component="form" onSubmit={weeklyForm.handleSubmit((values) => saveWeekly.mutate(values))} spacing={2} aria-label={weekly ? 'Update weekly availability' : 'Create weekly availability'} noValidate>
          <FormError error={saveWeekly.error} />
          <FormField<WeeklyValues> name="weekday" label="Weekday" select required fullWidth>{weekdays.map((day) => <MenuItem key={day} value={day}>{day}</MenuItem>)}</FormField>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><FormField<WeeklyValues> name="startLocal" label="Start time" type="time" required fullWidth /><FormField<WeeklyValues> name="endLocal" label="End time" type="time" required fullWidth /></Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><FormField<WeeklyValues> name="effectiveFrom" label="Effective from" type="date" required fullWidth /><FormField<WeeklyValues> name="effectiveTo" label="Effective through" type="date" required fullWidth /></Stack>
          <Stack direction="row" spacing={1}><Button type="submit" variant="contained" disabled={saveWeekly.isPending}>{weekly ? 'Update rule' : 'Add weekly rule'}</Button>{weekly && <Button onClick={() => { setWeekly(null); weeklyForm.reset() }}>Cancel</Button>}</Stack>
        </Stack></FormProvider>
        {rules.isPending && <CircularProgress aria-label="Loading weekly schedule" />}<FormError error={rules.error} />
        {rules.data?.length === 0 && <Alert severity="info">No weekly rules are defined.</Alert>}
        {rules.data?.map((rule) => <ScheduleRow key={rule.id} title={`${rule.weekday} · ${rule.startLocal.slice(0, 5)}–${rule.endLocal.slice(0, 5)}`} detail={`${rule.effectiveFrom} through ${rule.effectiveTo} · Version ${rule.version}`}
          onEdit={() => { setWeekly(rule); weeklyForm.reset({ weekday: rule.weekday, startLocal: rule.startLocal.slice(0, 5), endLocal: rule.endLocal.slice(0, 5), effectiveFrom: rule.effectiveFrom, effectiveTo: rule.effectiveTo }) }} />)}
      </Stack></Paper>
      <Paper variant="outlined" sx={{ p: 3, flex: 1, width: '100%' }}><Stack spacing={2}>
        <Typography variant="h5" component="h2">Date-specific overrides</Typography>
        <Typography variant="body2" color="text.secondary">OPEN replaces the day’s recurring open periods. CLOSED and LEAVE remove time. Multiple non-overlapping OPEN periods are combined.</Typography>
        <FormProvider {...overrideForm}><Stack component="form" onSubmit={overrideForm.handleSubmit((values) => saveOverride.mutate(values))} spacing={2} aria-label={override ? 'Update schedule override' : 'Create schedule override'} noValidate>
          <FormError error={saveOverride.error} />
          <FormField<OverrideValues> name="date" label="Date" type="date" required fullWidth />
          <FormField<OverrideValues> name="type" label="Type" select required fullWidth><MenuItem value="OPEN">Open</MenuItem><MenuItem value="CLOSED">Closed</MenuItem><MenuItem value="LEAVE">Leave</MenuItem></FormField>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><FormField<OverrideValues> name="startLocal" label="Start time (optional for full day)" type="time" fullWidth /><FormField<OverrideValues> name="endLocal" label="End time (optional for full day)" type="time" fullWidth /></Stack>
          <FormField<OverrideValues> name="reason" label="Reason (optional)" multiline minRows={2} fullWidth />
          <Stack direction="row" spacing={1}><Button type="submit" variant="contained" disabled={saveOverride.isPending}>{override ? 'Update override' : 'Add override'}</Button>{override && <Button onClick={() => { setOverride(null); overrideForm.reset() }}>Cancel</Button>}</Stack>
        </Stack></FormProvider>
        {overrides.isPending && <CircularProgress aria-label="Loading schedule overrides" />}<FormError error={overrides.error} />
        {overrides.data?.length === 0 && <Alert severity="info">No date-specific overrides are defined.</Alert>}
        {overrides.data?.map((item) => <ScheduleRow key={item.id} title={`${item.date} · ${item.type}`} detail={`${item.startLocal ? `${item.startLocal.slice(0, 5)}–${item.endLocal?.slice(0, 5)}` : 'Full day'} · Version ${item.version}${item.reason ? ` · ${item.reason}` : ''}`}
          onEdit={() => { setOverride(item); overrideForm.reset({ date: item.date, type: item.type, startLocal: item.startLocal?.slice(0, 5) ?? '', endLocal: item.endLocal?.slice(0, 5) ?? '', reason: item.reason ?? '' }) }} />)}
      </Stack></Paper>
    </Stack>}
  </Stack>
}

function FormFieldSelect({ label, value, onChange, options, loading }: { label: string; value: string; onChange: (value: string) => void; options: { value: string; label: string }[]; loading: boolean }) {
  return <Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={1}><Typography component="label" htmlFor="schedule-doctor" sx={{ fontWeight: 600 }}>{label}</Typography>
    <select id="schedule-doctor" value={value} onChange={(event) => onChange(event.target.value)} disabled={loading} style={{ minHeight: 48, padding: '0 12px', font: 'inherit', maxWidth: 500 }}>
      <option value="">Select a doctor</option>{options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select></Stack></Paper>
}

function ScheduleRow({ title, detail, onEdit }: { title: string; detail: string; onEdit: () => void }) {
  return <Box><Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }} spacing={1}><Box><Typography sx={{ fontWeight: 600 }}>{title}</Typography><Typography variant="body2" color="text.secondary">{detail}</Typography></Box><Button size="small" onClick={onEdit}>Edit</Button></Stack><Divider sx={{ mt: 1 }} /></Box>
}

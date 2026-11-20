import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { FormError } from '../identity/FormControls'
import { schedulingApi } from './api'

export function AvailabilityPage() {
  const [doctorId, setDoctorId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [search, setSearch] = useState<{ doctorId: string; from: string; to: string } | null>(null)
  const doctors = useQuery({ queryKey: ['doctors', true], queryFn: ({ signal }) => schedulingApi.doctors(true, signal) })
  const availability = useQuery({ queryKey: ['availability', search], queryFn: ({ signal }) => schedulingApi.availability(search!.doctorId, search!.from, search!.to, signal), enabled: !!search, retry: false })
  const preview = useQuery({ queryKey: ['conflict-preview', search], queryFn: ({ signal }) => schedulingApi.conflictPreview(search!.doctorId, search!.from, search!.to, signal), enabled: !!search, retry: false })
  const submit = (event: React.FormEvent) => { event.preventDefault(); if (doctorId && from && to) setSearch({ doctorId, from, to }) }
  const zone = availability.data?.clinicTimeZone ?? 'Clinic local'
  return <Stack spacing={3}>
    <Stack spacing={1}><Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>Staff scheduling view</Typography><Typography variant="h3" component="h1" sx={{ fontWeight: 700 }}>Doctor availability</Typography>
      <Typography color="text.secondary">Fixed-duration slots are projected in the clinic’s time zone, returned as UTC instants, and limited by the current booking horizon.</Typography></Stack>
    <Alert severity="info">Availability is informational only and does not reserve a slot. No patient information or appointment allocation is included.</Alert>
    <Paper variant="outlined" sx={{ p: 3 }}><Stack component="form" onSubmit={submit} spacing={2} aria-label="Search doctor availability">
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'flex-end' } }}>
        <NativeField label="Doctor" id="availability-doctor"><select id="availability-doctor" value={doctorId} onChange={(event) => setDoctorId(event.target.value)} required><option value="">Select a doctor</option>{doctors.data?.map((doctor) => <option key={doctor.id} value={doctor.id}>{doctor.firstName} {doctor.lastName} · {doctor.slotDurationMinutes} min</option>)}</select></NativeField>
        <NativeField label="From" id="availability-from"><input id="availability-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></NativeField>
        <NativeField label="Through" id="availability-to"><input id="availability-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} min={from} required /></NativeField>
        <Button type="submit" variant="contained" disabled={!doctorId || !from || !to}>View availability</Button>
      </Stack><FormError error={doctors.error} /></Stack></Paper>
    {(availability.isPending || preview.isPending) && <CircularProgress aria-label="Loading availability" />}
    <FormError error={availability.error} /><FormError error={preview.error} />
    {preview.data && <Alert severity="warning">{preview.data.message}</Alert>}
    {availability.data && <Stack spacing={2}>
      <Typography variant="h5" component="h2">{availability.data.slots.length} available {availability.data.slots.length === 1 ? 'slot' : 'slots'}</Typography>
      {availability.data.slots.length === 0 && <Alert severity="info">No projected slots match this range. The schedule may be closed, outside its effective period, fully overridden, or beyond the current horizon.</Alert>}
      {availability.data.slots.map((slot) => <Paper key={slot.startAt} variant="outlined" sx={{ p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
        <Box><Typography sx={{ fontWeight: 600 }}>{new Intl.DateTimeFormat(undefined, { dateStyle: 'full', timeStyle: 'short', timeZone: availability.data.clinicTimeZone }).format(new Date(slot.startAt))}–{new Intl.DateTimeFormat(undefined, { timeStyle: 'short', timeZone: availability.data.clinicTimeZone }).format(new Date(slot.endAt))}</Typography><Typography variant="body2" color="text.secondary">{zone}</Typography></Box>
        <Chip label={`UTC ${slot.startAt}–${slot.endAt}`} variant="outlined" sx={{ height: 'fit-content', overflowWrap: 'anywhere' }} />
      </Stack></Paper>)}
    </Stack>}
  </Stack>
}

function NativeField({ label, id, children }: { label: string; id: string; children: React.ReactNode }) {
  return <Stack spacing={0.5} sx={{ flex: 1, '& input, & select': { minHeight: 48, padding: '0 12px', font: 'inherit', width: '100%' } }}><Typography component="label" htmlFor={id} variant="body2" sx={{ fontWeight: 600 }}>{label}</Typography>{children}</Stack>
}

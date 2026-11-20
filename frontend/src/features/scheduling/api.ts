import type { components } from '../../api/generated'
import { authorized } from '../identity/service'

export type Specialization = components['schemas']['SpecializationResponse']
export type Doctor = components['schemas']['DoctorResponse']
export type WeeklyRule = components['schemas']['WeeklyResponse']
export type ScheduleOverride = components['schemas']['OverrideResponse']
export type ClinicConfiguration = components['schemas']['ConfigurationResponse']
export type Availability = components['schemas']['AvailabilityResponse']
export type ConflictPreview = components['schemas']['ConflictPreviewResponse']

const id = (value: string) => encodeURIComponent(value)
const version = (value: number) => `?version=${value}`

export const schedulingApi = {
  specializations: (activeOnly: boolean, signal?: AbortSignal) => authorized<Specialization[]>(`/api/v1/directory/specializations?activeOnly=${activeOnly}`, { signal }),
  createSpecialization: (name: string) => authorized<Specialization>('/api/v1/directory/specializations', { method: 'POST', body: { name } }),
  updateSpecialization: (value: Specialization) => authorized<Specialization>(`/api/v1/directory/specializations/${id(value.id)}${version(value.version)}`, { method: 'PATCH', body: { name: value.name } }),
  deactivateSpecialization: (value: Specialization) => authorized<Specialization>(`/api/v1/directory/specializations/${id(value.id)}${version(value.version)}`, { method: 'DELETE' }),
  doctors: (activeOnly: boolean, signal?: AbortSignal) => authorized<Doctor[]>(`/api/v1/directory/doctors?activeOnly=${activeOnly}`, { signal }),
  createDoctor: (body: components['schemas']['DoctorRequest']) => authorized<Doctor>('/api/v1/directory/doctors', { method: 'POST', body }),
  updateDoctor: (value: Doctor) => authorized<Doctor>(`/api/v1/directory/doctors/${id(value.id)}${version(value.version)}`, { method: 'PATCH', body: { specializationId: value.specializationId, firstName: value.firstName, lastName: value.lastName, slotDurationMinutes: value.slotDurationMinutes } }),
  deactivateDoctor: (value: Doctor) => authorized<Doctor>(`/api/v1/directory/doctors/${id(value.id)}${version(value.version)}`, { method: 'DELETE' }),
  weeklyRules: (doctorId: string, signal?: AbortSignal) => authorized<WeeklyRule[]>(`/api/v1/doctors/${id(doctorId)}/schedule`, { signal }),
  createWeekly: (doctorId: string, body: components['schemas']['WeeklyRequest']) => authorized<WeeklyRule>(`/api/v1/doctors/${id(doctorId)}/schedule`, { method: 'POST', body }),
  updateWeekly: (doctorId: string, value: WeeklyRule) => authorized<WeeklyRule>(`/api/v1/doctors/${id(doctorId)}/schedule/${id(value.id)}`, { method: 'PATCH', body: { weekday: value.weekday, startLocal: value.startLocal, endLocal: value.endLocal, effectiveFrom: value.effectiveFrom, effectiveTo: value.effectiveTo, version: value.version } }),
  overrides: (doctorId: string, signal?: AbortSignal) => authorized<ScheduleOverride[]>(`/api/v1/doctors/${id(doctorId)}/overrides`, { signal }),
  createOverride: (doctorId: string, body: components['schemas']['OverrideRequest']) => authorized<ScheduleOverride>(`/api/v1/doctors/${id(doctorId)}/overrides`, { method: 'POST', body }),
  updateOverride: (doctorId: string, value: ScheduleOverride) => authorized<ScheduleOverride>(`/api/v1/doctors/${id(doctorId)}/overrides/${id(value.id)}`, { method: 'PATCH', body: { date: value.date, type: value.type, startLocal: value.startLocal, endLocal: value.endLocal, reason: value.reason, version: value.version } }),
  availability: (doctorId: string, from: string, to: string, signal?: AbortSignal) => authorized<Availability>(`/api/v1/doctors/${id(doctorId)}/availability?from=${from}&to=${to}`, { signal }),
  conflictPreview: (doctorId: string, from: string, to: string, signal?: AbortSignal) => authorized<ConflictPreview>(`/api/v1/doctors/${id(doctorId)}/schedule-conflict-preview?from=${from}&to=${to}`, { signal }),
  configuration: (signal?: AbortSignal) => authorized<ClinicConfiguration>('/api/v1/configuration', { signal }),
  updateConfiguration: (value: ClinicConfiguration) => authorized<ClinicConfiguration>('/api/v1/configuration', { method: 'PATCH', body: { bookingHorizonDays: value.bookingHorizonDays, proposalHoldHours: value.proposalHoldHours, patientChangeCutoffHours: value.patientChangeCutoffHours, version: value.version } }),
}

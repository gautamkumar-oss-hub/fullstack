import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiModule from './api'
import { DirectoryPage } from './DirectoryPage'
import { SchedulePage } from './SchedulePage'
import { AvailabilityPage } from './AvailabilityPage'
import { ConfigurationPage } from './ConfigurationPage'
import { ApiError } from '../identity/http'
import type { Availability, ClinicConfiguration, ConflictPreview, Doctor, Specialization } from './api'

const specialization: Specialization = { id: '17e57e82-bb0c-4a8b-9e9c-1b9110f54c60', name: 'Cardiology', active: true, version: 2 }
const doctor: Doctor = { id: '97a401ce-a21b-4655-91c0-d5fb94bb5a4d', specializationId: specialization.id, firstName: 'Ada', lastName: 'Lovelace', slotDurationMinutes: 30, active: true, version: 3 }
const configuration: ClinicConfiguration = { clinicTimeZone: 'America/New_York', bookingHorizonDays: 90, proposalHoldHours: 24, patientChangeCutoffHours: 24, version: 4 }

function renderPage(children: React.ReactNode) {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{children}</QueryClientProvider>)
}

beforeEach(() => {
  vi.spyOn(apiModule.schedulingApi, 'specializations').mockResolvedValue([specialization])
  vi.spyOn(apiModule.schedulingApi, 'doctors').mockResolvedValue([doctor])
  vi.spyOn(apiModule.schedulingApi, 'weeklyRules').mockResolvedValue([])
  vi.spyOn(apiModule.schedulingApi, 'overrides').mockResolvedValue([])
})
afterEach(() => vi.restoreAllMocks())

describe('scheduling administration', () => {
  it('creates a doctor with one specialization and fixed duration', async () => {
    const user = userEvent.setup()
    const create = vi.spyOn(apiModule.schedulingApi, 'createDoctor').mockResolvedValue(doctor)
    renderPage(<DirectoryPage />)
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: /^First name/ }), 'Ada')
    await user.type(screen.getByRole('textbox', { name: /^Last name/ }), 'Lovelace')
    await user.click(screen.getByRole('combobox', { name: /^Specialization/ }))
    await user.click(screen.getByRole('option', { name: 'Cardiology' }))
    await user.click(screen.getByRole('button', { name: 'Create doctor' }))
    expect(create).toHaveBeenCalledWith({ specializationId: specialization.id, firstName: 'Ada', lastName: 'Lovelace', slotDurationMinutes: 30 })
  })

  it('shows safe optimistic concurrency errors without losing directory history', async () => {
    const user = userEvent.setup()
    vi.spyOn(apiModule.schedulingApi, 'deactivateSpecialization').mockRejectedValue(new ApiError('The resource changed. Refresh and try again.', 409, 'resource_changed'))
    renderPage(<DirectoryPage />)
    await user.click(await screen.findByRole('button', { name: 'Deactivate Cardiology' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The resource changed. Refresh and try again.')
    expect(screen.getByText('Cardiology')).toBeInTheDocument()
  })

  it('validates inverted weekly time and effective ranges', async () => {
    const user = userEvent.setup()
    const create = vi.spyOn(apiModule.schedulingApi, 'createWeekly')
    renderPage(<SchedulePage />)
    await screen.findByRole('option', { name: 'Ada Lovelace' })
    await user.selectOptions(screen.getByLabelText('Doctor'), doctor.id)
    fireEvent.change(screen.getByLabelText(/^Start time/), { target: { value: '12:00' } })
    fireEvent.change(screen.getByLabelText(/^End time/), { target: { value: '09:00' } })
    fireEvent.change(screen.getByLabelText(/^Effective from/), { target: { value: '2027-02-02' } })
    fireEvent.change(screen.getByLabelText(/^Effective through/), { target: { value: '2027-01-01' } })
    await user.click(screen.getByRole('button', { name: 'Add weekly rule' }))
    expect(await screen.findByText('Start time must be before end time.')).toBeInTheDocument()
    expect(screen.getByText('Effective start must not be after its end.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('renders clinic-local and UTC slots with honest reservation and allocation notices', async () => {
    const user = userEvent.setup()
    const availability: Availability = { doctorId: doctor.id, clinicTimeZone: 'America/New_York', bookingNotice: 'Availability is informational only and does not reserve a slot.', slots: [{ startAt: '2026-12-21T14:00:00Z', endAt: '2026-12-21T14:30:00Z' }] }
    const preview: ConflictPreview = { doctorId: doctor.id, from: '2026-12-21', to: '2026-12-21', allocationChecksEnforced: false, message: 'Preview only: allocation conflict enforcement is unavailable until allocation records are introduced in Phase 5.' }
    vi.spyOn(apiModule.schedulingApi, 'availability').mockResolvedValue(availability)
    vi.spyOn(apiModule.schedulingApi, 'conflictPreview').mockResolvedValue(preview)
    renderPage(<AvailabilityPage />)
    await screen.findByRole('option', { name: 'Ada Lovelace · 30 min' })
    await user.selectOptions(screen.getByLabelText('Doctor'), doctor.id)
    await user.type(screen.getByLabelText('From'), '2026-12-21')
    await user.type(screen.getByLabelText('Through'), '2026-12-21')
    await user.click(screen.getByRole('button', { name: 'View availability' }))
    expect(await screen.findByText('1 available slot')).toBeInTheDocument()
    expect(screen.getByText(/UTC 2026-12-21T14:00:00Z/)).toBeInTheDocument()
    expect(screen.getByText(/December 21, 2026/)).toBeInTheDocument()
    expect(screen.getByText(/does not reserve a slot/)).toBeInTheDocument()
    expect(screen.getByText(/allocation conflict enforcement is unavailable/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /create|deactivate|update/i })).not.toBeInTheDocument()
  })

  it('submits configuration with its optimistic version', async () => {
    const user = userEvent.setup()
    vi.spyOn(apiModule.schedulingApi, 'configuration').mockResolvedValue(configuration)
    const update = vi.spyOn(apiModule.schedulingApi, 'updateConfiguration').mockResolvedValue({ ...configuration, version: 5 })
    renderPage(<ConfigurationPage />)
    const horizon = await screen.findByRole('spinbutton', { name: 'Booking horizon (days)' })
    expect(horizon).toHaveValue(90)
    await user.clear(horizon)
    await user.type(horizon, '120')
    await user.click(screen.getByRole('button', { name: 'Save configuration' }))
    expect(update).toHaveBeenCalledWith({ ...configuration, bookingHorizonDays: 120 })
  })
})

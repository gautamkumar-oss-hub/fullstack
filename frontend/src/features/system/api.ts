import type { components } from '../../api/generated'

export type SystemInfo = components['schemas']['SystemInfo']

export async function getSystemInfo(signal?: AbortSignal): Promise<SystemInfo> {
  const response = await fetch('/api/v1/system/info', {
    headers: { Accept: 'application/json' },
    signal,
  })

  if (!response.ok) {
    throw new Error('The appointment service is not available yet.')
  }

  return response.json() as Promise<SystemInfo>
}

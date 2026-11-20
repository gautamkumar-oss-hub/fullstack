export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly fields?: Record<string, string>

  constructor(message: string, status: number, code?: string, fields?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}

interface ProblemBody {
  detail?: unknown
  message?: unknown
  title?: unknown
  code?: unknown
  errors?: unknown
}

export async function responseError(response: Response): Promise<ApiError> {
  let body: ProblemBody | null = null
  try {
    body = await response.json() as ProblemBody
  } catch {
    // An empty or non-JSON error still receives a useful HTTP status.
  }
  const message = [body?.detail, body?.message, body?.title]
    .find((value): value is string => typeof value === 'string' && value.length > 0)
    ?? `The request could not be completed (${response.status}).`
  return new ApiError(
    message,
    response.status,
    typeof body?.code === 'string' ? body.code : undefined,
    body?.errors && typeof body.errors === 'object' ? body.errors as Record<string, string> : undefined,
  )
}

export async function responseBody<T>(response: Response): Promise<T> {
  if (!response.ok) throw await responseError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

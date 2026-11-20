import { Alert, TextField, type TextFieldProps } from '@mui/material'
import { Controller, useFormContext, type FieldValues, type Path } from 'react-hook-form'
import type { ReactNode } from 'react'

export function FormField<T extends FieldValues>({ name, ...props }: TextFieldProps & { name: Path<T> }) {
  const { control } = useFormContext<T>()
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...props}
          {...field}
          value={field.value ?? ''}
          id={props.id ?? name}
          name={name}
          error={fieldState.invalid}
          helperText={fieldState.error?.message ?? props.helperText}
          slotProps={{ ...props.slotProps, htmlInput: { ...props.slotProps?.htmlInput, 'aria-describedby': `${name}-helper-text` } }}
        />
      )}
    />
  )
}

export function FormError({ error }: { error: unknown }) {
  if (!error) return null
  return <Alert severity="error" role="alert">{error instanceof Error ? error.message : 'The request could not be completed.'}</Alert>
}

export function DevelopmentCode({ children }: { children: ReactNode }) {
  if (!children) return null
  return <Alert severity="info">Local development only: your email code is <strong>{children}</strong>. It is never logged or returned by normal profiles.</Alert>
}

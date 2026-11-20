import { Box, Container, Paper, Stack, Typography } from '@mui/material'
import { Stethoscope } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

export function AuthLayout({ title, description, children, footer }: {
  title: string
  description: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <Container maxWidth="sm" sx={{ py: { xs: 3, md: 8 } }}>
      <Stack spacing={3}>
        <Stack direction="row" spacing={1.5} component={Link} to="/" sx={{ alignItems: 'center', color: 'inherit', textDecoration: 'none' }}>
          <Box className="brand-mark" aria-hidden="true"><Stethoscope size={24} /></Box>
          <Typography variant="h6" sx={{ fontWeight: 700 }}>Hospital appointments</Typography>
        </Stack>
        <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack spacing={3} component="section" aria-labelledby="auth-title">
            <Stack spacing={1}>
              <Typography variant="h4" component="h1" id="auth-title" sx={{ fontWeight: 700 }}>{title}</Typography>
              <Typography color="text.secondary">{description}</Typography>
            </Stack>
            {children}
          </Stack>
        </Paper>
        {footer && <Stack direction="row" spacing={1} sx={{ justifyContent: 'center', flexWrap: 'wrap' }}>{footer}</Stack>}
      </Stack>
    </Container>
  )
}

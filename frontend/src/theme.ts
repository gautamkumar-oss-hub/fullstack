import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    primary: { main: '#176b63', light: '#e3f2ef', dark: '#104e48' },
    background: { default: '#f7faf9', paper: '#ffffff' },
    text: { primary: '#18312e', secondary: '#526764' },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    h2: { fontSize: 'clamp(2.25rem, 6vw, 3.75rem)', lineHeight: 1.08, letterSpacing: '-0.045em' },
    button: { textTransform: 'none' },
  },
  components: {
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
  },
})

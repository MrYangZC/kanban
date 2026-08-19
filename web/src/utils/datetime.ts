export function fmtDate(t?: string | null): string {
  if (!t) return ''
  return t.slice(0, 10)
}

export function fmtTime(t?: string | null): string {
  if (!t) return ''
  return t.slice(0, 19).replace('T', ' ')
}

export function fmtDateTime(t?: string | null): string {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

export function fmtHM(t?: string | null): string {
  if (!t) return ''
  return t.slice(11, 16)
}
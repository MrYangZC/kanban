export interface DictOption {
  value: string
  label: string
  color: string
}

export const PRIORITY_OPTIONS: DictOption[] = [
  { value: 'P0', label: '紧急', color: '#F56C6C' },
  { value: 'P1', label: '高', color: '#E6A23C' },
  { value: 'P2', label: '中', color: '#409EFF' },
  { value: 'P3', label: '低', color: '#909399' }
]

export const SEVERITY_OPTIONS: DictOption[] = [
  { value: 'S1', label: '致命', color: '#F56C6C' },
  { value: 'S2', label: '严重', color: '#FA8C16' },
  { value: 'S3', label: '一般', color: '#409EFF' },
  { value: 'S4', label: '轻微', color: '#909399' }
]

export const PRIORITY_LABEL: Record<string, string> = {
  P0: '紧急', P1: '高', P2: '中', P3: '低'
}

export const SEVERITY_LABEL: Record<string, string> = {
  S1: '致命', S2: '严重', S3: '一般', S4: '轻微'
}

export const PRIORITY_COLOR: Record<string, string> = {
  P0: '#F56C6C', P1: '#E6A23C', P2: '#409EFF', P3: '#909399'
}

export const SEVERITY_COLOR: Record<string, string> = {
  S1: '#F56C6C', S2: '#FA8C16', S3: '#409EFF', S4: '#909399'
}

export function priorityLabel(v?: string): string {
  return (v && PRIORITY_LABEL[v]) || v || ''
}

export function severityLabel(v?: string): string {
  return (v && SEVERITY_LABEL[v]) || v || ''
}

export function priorityColor(v?: string): string {
  return (v && PRIORITY_COLOR[v]) || '#909399'
}

export function severityColor(v?: string): string {
  return (v && SEVERITY_COLOR[v]) || '#909399'
}
const AVATAR_COLORS = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C'
]

export function avatarChar(name: string | null | undefined): string {
  return name ? name.charAt(0) : '·'
}

export function avatarColor(name: string | null | undefined): string {
  if (!name) return '#C0C4CC'
  return AVATAR_COLORS[name.charCodeAt(0) % AVATAR_COLORS.length]
}
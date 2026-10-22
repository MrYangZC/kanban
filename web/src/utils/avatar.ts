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

/** 头像上传：与服务端 ImageFileUtil 白名单、5MB 上限保持一致，选完即拦，不必等接口返回 */
export const AVATAR_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp'
export const AVATAR_MAX_BYTES = 5 * 1024 * 1024

/** 校验头像文件，通过返回 null，否则返回提示文案 */
export function validateAvatarFile(file: File): string | null {
  if (!AVATAR_ACCEPT.split(',').includes(file.type)) {
    return '仅支持 jpg / png / gif / webp 格式的图片'
  }
  if (file.size > AVATAR_MAX_BYTES) return '图片大小不能超过 5MB'
  return null
}
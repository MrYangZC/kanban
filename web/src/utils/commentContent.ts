/**
 * 评论正文渲染（评论与动态共用）。
 *
 * 历史上两处评论渲染各写一套：详情页只认 `![alt](url)`，抽屉面板只认原始 `<img>`，
 * 于是同一段内容在两边显示不一致。这里统一成：
 * 1. 白名单 `<img src>`（面板上传图片写入的就是原始 HTML）保留为图片，其余标签转义；
 * 2. `![alt](url)` 语法转图片；
 * 3. 其他内容全部转义，不渲染任意 HTML（防 XSS）。
 */

/** 仅允许 http(s)、站内绝对路径与 /api/ 的图片地址，避免 javascript: 注入 */
const SAFE_IMG_URL = /^(https?:\/\/|\/api\/|\/)/

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function imgTag(url: string, alt: string): string {
  const safeUrl = url.replace(/"/g, '%22')
  const safeAlt = alt.replace(/"/g, '').replace(/</g, '')
  return `<img src="${safeUrl}" alt="${safeAlt}" style="max-width:100%;border-radius:6px;margin:4px 0" />`
}

/**
 * 把评论正文转成可安全 v-html 的 HTML。
 * @param content 评论原文（可能含 `<img>` 或 markdown 图片语法）
 */
export function renderCommentContent(content?: string | null): string {
  if (!content) return ''

  const tokens: string[] = []
  const stash = (html: string) => {
    tokens.push(html)
    return `\u0000${tokens.length - 1}\u0000`
  }

  // 1) 先摘出白名单 <img>（此时还没转义，能读到 src）
  let text = content.replace(/<img\b[^>]*>/gi, (tag) => {
    const m = tag.match(/\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i)
    const src = (m?.[1] || m?.[2] || m?.[3] || '').trim()
    if (!src || !SAFE_IMG_URL.test(src)) return ''
    return stash(imgTag(src, '图片'))
  })

  // 2) 剩余 HTML 全部转义
  text = escapeHtml(text)

  // 3) markdown 图片语法
  text = text.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (raw, alt: string, url: string) => {
    if (!SAFE_IMG_URL.test(url)) return raw
    return stash(imgTag(url, alt))
  })

  // 4) 还原占位符
  return text.replace(/\u0000(\d+)\u0000/g, (_, idx: string) => tokens[Number(idx)] ?? '')
}

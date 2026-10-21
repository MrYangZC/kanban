import type { Options } from '@popperjs/core'

/**
 * 统一的下拉层定位配置。
 *
 * 抽屉、对话框容器会裁剪 relative 定位的 popper，所以统一用 fixed。
 * 这里显式标注 Partial<Options>：否则字面量里的 strategy 会被推断成 string，
 * 与 ElSelect/ElTooltip 要求的 PositioningStrategy 字面量类型不兼容。
 */
export const POPPER_OPTIONS: Partial<Options> = {
  strategy: 'fixed',
  modifiers: [
    {
      name: 'preventOverflow',
      options: { altAxis: true, boundary: 'viewport', rootBoundary: 'viewport', padding: 8 }
    },
    {
      name: 'flip',
      options: { allowedAutoPlacements: ['bottom', 'top'] }
    }
  ]
}

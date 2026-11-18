import type { FormItemRule } from 'element-plus'

/**
 * 表单校验规则工厂。
 *
 * <p>此前全站 `el-form` 都没写 `prop` 和 `:rules`——`required` 只是画了个红星，
 * 校验实际发生在提交函数里的 `if (!form.xx.trim()) { ElMessage.warning(...) }`：
 * 用户点了确定才被告知哪一栏没填，错误也不落在对应输入框上。
 *
 * <p>这里集中定义常用规则，避免每个表单各写一份 message 文案。
 */

/** 必填 */
export function required(label: string): FormItemRule {
  return { required: true, message: `请输入${label}`, trigger: ['blur', 'change'] }
}

/** 必选（下拉/单选这类不是"输入"的字段） */
export function requiredSelect(label: string): FormItemRule {
  return { required: true, message: `请选择${label}`, trigger: 'change' }
}

/** 最大长度 */
export function maxLen(max: number, label: string): FormItemRule {
  return { max, message: `${label}不能超过 ${max} 个字符`, trigger: 'blur' }
}

/** 最小长度 */
export function minLen(min: number, label: string): FormItemRule {
  return { min, message: `${label}至少 ${min} 个字符`, trigger: 'blur' }
}

/** 邮箱格式（空值交给 required 规则判断，这里只管格式） */
export function email(): FormItemRule {
  return {
    type: 'email',
    message: '邮箱格式不正确',
    trigger: ['blur', 'change']
  }
}

/** 字母/数字/下划线，用于编码类字段（角色 code、项目 key） */
export function codePattern(): FormItemRule {
  return {
    pattern: /^[A-Za-z][A-Za-z0-9_-]*$/,
    message: '只能以字母开头，包含字母、数字、下划线或短横线',
    trigger: 'blur'
  }
}

/**
 * 结束日期不能早于开始日期。
 *
 * @param getModel 读取整份表单模型（校验需要跨字段，el-form 单条规则拿不到兄弟字段）
 * @param startKey  开始日期字段名
 * @param endKey    结束日期字段名
 */
export function endNotBeforeStart(
  getModel: () => Record<string, unknown>,
  startKey = 'startDate'
): FormItemRule {
  return {
    validator: (_rule, value, callback) => {
      const model = getModel()
      const start = model[startKey] ? String(model[startKey]) : ''
      const end = value ? String(value) : ''
      // 任一为空时不拦：这类字段通常允许只填一个，是否必填由 required 规则决定
      if (!start || !end) {
        callback()
        return
      }
      if (end < start) {
        callback(new Error('结束日期不能早于开始日期'))
        return
      }
      callback()
    },
    trigger: 'change'
  }
}

/** 日期不能早于今天（截止日期类字段用） */
export function notBeforeToday(): FormItemRule {
  return {
    validator: (_rule, value, callback) => {
      if (!value) {
        callback()
        return
      }
      const today = new Date()
      const pad = (n: number) => String(n).padStart(2, '0')
      const todayStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
      if (String(value) < todayStr) {
        callback(new Error('日期不能早于今天'))
        return
      }
      callback()
    },
    trigger: 'change'
  }
}

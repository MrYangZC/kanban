<template>
  <div class="app-canvas flex h-full flex-col">
    <header class="toolbar">
      <ShieldCheck :size="18" class="text-brand" />
      <h1 class="toolbar-title">权限管理</h1>
      <span class="text-xs text-ink-secondary">维护角色、菜单与权限码，并为项目成员分配角色</span>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <ScopeNav :groups="navGroups" :model-value="tab" @update:model-value="tab = $event as RbacTab" />

      <main class="flex-1 overflow-auto p-6">
        <!-- 角色 -->
        <section v-if="tab === 'roles'">
          <div class="mb-4 flex items-center justify-between">
            <div>
              <h2 class="text-base font-semibold text-ink">角色</h2>
              <p class="mt-1 text-xs text-ink-secondary">角色按项目维度分配给成员，编码需全局唯一</p>
            </div>
            <button class="btn-primary" @click="openRole()"><Plus :size="16" /> 新建角色</button>
          </div>

          <div class="table-wrapper">
            <table class="w-full text-sm">
              <thead class="table-head">
                <tr>
                  <th>名称</th><th>编码</th><th>描述</th><th class="text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in roles" :key="row.id" class="table-row">
                  <td class="px-5 py-3 font-medium text-ink">{{ row.name }}</td>
                  <td class="px-5 py-3">
                    <span class="rounded bg-surface-muted px-2 py-0.5 font-mono text-xs text-ink-600">{{ row.code }}</span>
                  </td>
                  <td class="px-5 py-3 text-ink-regular">{{ row.description || '—' }}</td>
                  <td class="px-5 py-3">
                    <div class="flex items-center justify-end gap-1">
                      <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-surface-hover" title="配置导航菜单" @click="openRoleMenus(row)"><ListTree :size="15" /></button>
                      <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-surface-hover" title="编辑" @click="openRole(row)"><Pencil :size="15" /></button>
                      <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-danger-light" title="删除" @click="delRole(row)"><Trash2 :size="15" /></button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!roles.length">
                  <td colspan="4" class="px-5 py-12 text-center text-sm text-ink-secondary">{{ roleLoading ? '加载中…' : '暂无角色' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 菜单（导航） -->
        <section v-else-if="tab === 'menus'">
          <div class="mb-4 flex items-center justify-between">
            <div>
              <h2 class="text-base font-semibold text-ink">菜单（导航）</h2>
              <p class="mt-1 text-xs text-ink-secondary">菜单树决定左侧导航的可见性，需通过角色分配后生效</p>
            </div>
            <button class="btn-primary" @click="openMenu()"><Plus :size="16" /> 新建菜单</button>
          </div>

          <div v-if="menuLoading" class="py-16 text-center text-sm text-ink-placeholder">加载中…</div>
          <div v-else-if="!menuRows.length" class="py-16 text-center text-sm text-ink-placeholder">暂无菜单</div>
          <div v-else class="glass-panel overflow-hidden rounded-lg">
            <div
              v-for="row in menuRows"
              :key="row.node.id"
              class="flex items-center gap-3 border-b border-line-light py-2.5 pr-4 transition last:border-0 hover:bg-surface-hover"
              :style="{ paddingLeft: `${16 + row.depth * 24}px` }"
            >
              <component :is="row.depth > 0 ? File : Folder" :size="15" class="shrink-0 text-ink-secondary" />
              <span class="text-sm text-ink">{{ row.node.name }}</span>
              <span class="rounded bg-surface-muted px-2 py-0.5 font-mono text-xs text-ink-600">{{ row.node.code }}</span>
              <div class="ml-auto flex items-center gap-1">
                <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-surface-hover" title="编辑" @click="openMenu(row.node)"><Pencil :size="15" /></button>
                <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-danger-light" title="删除" @click="delMenu(row.node)"><Trash2 :size="15" /></button>
              </div>
            </div>
          </div>
        </section>

        <!-- 用户角色 -->
        <section v-else-if="tab === 'members'" class="flex items-start gap-5">
          <!-- 左：项目列表，点击切换右侧成员 -->
          <aside class="w-56 shrink-0">
            <div class="mb-2 px-2 text-xs font-medium text-ink-secondary">项目</div>
            <div class="glass-panel max-h-[calc(100vh_-_13rem)] overflow-y-auto rounded-lg p-1.5">
              <button
                v-for="p in projectOptions"
                :key="p.id"
                class="scope-nav-item"
                :class="userProjectId === p.id ? 'scope-nav-item-active' : 'scope-nav-item-idle'"
                :title="`${p.name} (${p.projectKey})`"
                @click="selectProject(p.id)"
              >
                <FolderKanban :size="15" class="shrink-0" />
                <span class="flex-1 truncate text-left">{{ p.name }}</span>
                <span class="scope-nav-count shrink-0">{{ p.projectKey }}</span>
              </button>
              <div v-if="!projectOptions.length" class="px-3 py-4 text-xs text-ink-placeholder">暂无项目</div>
            </div>
          </aside>

          <!-- 右：所选项目的成员角色 -->
          <div class="min-w-0 flex-1">
            <div class="mb-4">
              <h2 class="text-base font-semibold text-ink">
                成员角色<span v-if="currentProjectName" class="font-normal text-ink-secondary"> · {{ currentProjectName }}</span>
              </h2>
              <p class="mt-1 text-xs text-ink-secondary">选择左侧项目后列出全部成员，可直接调整其角色（保存即生效）</p>
            </div>

            <div class="table-wrapper">
              <table class="w-full text-sm">
                <thead class="table-head">
                  <tr>
                    <th>成员</th><th>用户名</th><th>角色</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in memberRows" :key="row.userId" class="table-row">
                    <td class="px-5 py-3">
                      <div class="flex items-center gap-3">
                        <UserAvatar :name="row.displayName" :src="row.avatarUrl" :size="32" />
                        <span class="font-medium text-ink">{{ row.displayName }}</span>
                      </div>
                    </td>
                    <td class="px-5 py-3 text-ink-regular">{{ row.username }}</td>
                    <td class="px-5 py-3">
                      <el-select v-model="row.roleIds" multiple size="small" style="width: 100%" @change="saveUserRoles(row)">
                        <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
                      </el-select>
                    </td>
                  </tr>
                  <tr v-if="!memberRows.length">
                    <td colspan="3" class="px-5 py-12 text-center text-sm text-ink-secondary">
                      {{ userRoleLoading ? '加载中…' : userProjectId ? '该项目暂无成员' : '请先选择项目' }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <!-- 权限矩阵 -->
        <section v-else-if="tab === 'matrix'" class="flex items-start gap-5">
          <!-- 左：角色列表，点击切换右侧权限 -->
          <aside class="w-56 shrink-0">
            <div class="mb-2 px-2 text-xs font-medium text-ink-secondary">角色</div>
            <div class="glass-panel max-h-[calc(100vh_-_13rem)] overflow-y-auto rounded-lg p-1.5">
              <button
                v-for="r in roles"
                :key="r.id"
                class="scope-nav-item"
                :class="matrixRoleId === r.id ? 'scope-nav-item-active' : 'scope-nav-item-idle'"
                :title="`${r.name} (${r.code})`"
                @click="selectMatrixRole(r.id)"
              >
                <ShieldCheck :size="15" class="shrink-0" />
                <span class="flex-1 truncate text-left">{{ r.name }}</span>
              </button>
              <div v-if="!roles.length" class="px-3 py-4 text-xs text-ink-placeholder">暂无角色</div>
            </div>
          </aside>

          <!-- 右：所选角色的权限码 -->
          <div class="min-w-0 flex-1">
            <div class="mb-4 flex items-center justify-between">
              <div>
                <h2 class="text-base font-semibold text-ink">
                  权限矩阵<span v-if="currentMatrixRoleName" class="font-normal text-ink-secondary"> · {{ currentMatrixRoleName }}</span>
                </h2>
                <p class="mt-1 text-xs text-ink-secondary">按模块勾选权限码，保存后对持有该角色的成员立即生效</p>
              </div>
              <button class="btn-primary" :disabled="!matrixRoleId" @click="saveMatrix">保存</button>
            </div>

            <div v-if="!matrixRoleId" class="py-16 text-center text-sm text-ink-placeholder">请先选择左侧角色</div>
            <div v-else-if="matrixLoading" class="py-16 text-center text-sm text-ink-placeholder">加载中…</div>
            <div v-else-if="!matrixGroups.length" class="py-16 text-center text-sm text-ink-placeholder">暂无可分配权限</div>
            <div v-else class="grid grid-cols-1 gap-4 xl:grid-cols-2">
              <div v-for="g in matrixGroups" :key="g.module" class="glass-panel rounded-lg p-4">
                <div class="mb-3 flex items-center justify-between">
                  <span class="text-sm font-medium text-ink">{{ g.module }}</span>
                  <button class="text-xs text-brand transition hover:opacity-80" @click="toggleModule(g)">
                    {{ isModuleAllChecked(g) ? '取消全选' : '全选' }}
                  </button>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="p in g.items"
                    :key="p.code"
                    class="perm-chip"
                    :class="{ 'perm-chip-on': matrixChecked.includes(p.code) }"
                    :title="p.code"
                    @click="togglePermission(p.code)"
                  >
                    <Check v-if="matrixChecked.includes(p.code)" :size="12" class="shrink-0" />
                    {{ p.name }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>

    <!-- 角色对话框 -->
    <div v-if="roleVisible" class="fixed inset-0 z-40 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/30" @click="roleVisible = false" />
      <div class="glass-pop relative z-50 w-[460px] rounded-lg p-6">
        <h3 class="mb-4 text-base font-semibold text-ink">{{ roleEditing ? '编辑角色' : '新建角色' }}</h3>
        <div class="space-y-3">
          <div>
            <label class="label-base">编码</label>
            <input v-model="roleForm.code" placeholder="如 PROJECT_ADMIN" class="input-base" />
          </div>
          <div>
            <label class="label-base">名称</label>
            <input v-model="roleForm.name" placeholder="如 项目管理员" class="input-base" />
          </div>
          <div>
            <label class="label-base">描述</label>
            <textarea v-model="roleForm.description" rows="2" placeholder="可选" class="input-base resize-none"></textarea>
          </div>
          <div>
            <label class="label-base">排序</label>
            <input v-model.number="roleForm.sortOrder" type="number" min="0" class="input-base" />
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="rounded px-3.5 py-2 text-sm text-ink-secondary transition hover:bg-surface-hover" @click="roleVisible = false">取消</button>
          <button class="btn-primary" @click="submitRole">确定</button>
        </div>
      </div>
    </div>

    <!-- 菜单对话框 -->
    <div v-if="menuVisible" class="fixed inset-0 z-40 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/30" @click="menuVisible = false" />
      <div class="glass-pop relative z-50 w-[460px] rounded-lg p-6">
        <h3 class="mb-4 text-base font-semibold text-ink">{{ menuEditing ? '编辑菜单' : '新建菜单' }}</h3>
        <div class="space-y-3">
          <div>
            <label class="label-base">编码</label>
            <input v-model="menuForm.code" placeholder="如 nav.tasks" class="input-base" />
          </div>
          <div>
            <label class="label-base">名称</label>
            <input v-model="menuForm.name" placeholder="如 任务" class="input-base" />
          </div>
          <div>
            <label class="label-base">父级</label>
            <el-tree-select
              v-model="parentSelectValue"
              :data="parentTreeOptions"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              default-expand-all
              :render-after-expand="false"
              :expand-on-click-node="false"
              class="w-full"
            />
            <p class="mt-1 text-xs text-ink-placeholder">缩进即层级；父级不可选自身及其下级</p>
          </div>
          <div>
            <label class="label-base">排序</label>
            <input v-model.number="menuForm.sortOrder" type="number" min="0" class="input-base" />
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="rounded px-3.5 py-2 text-sm text-ink-secondary transition hover:bg-surface-hover" @click="menuVisible = false">取消</button>
          <button class="btn-primary" @click="submitMenu">确定</button>
        </div>
      </div>
    </div>

    <!-- 角色-菜单分配 -->
    <div v-if="roleMenuVisible" class="fixed inset-0 z-40 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/30" @click="roleMenuVisible = false" />
      <div class="glass-pop relative z-50 w-[480px] rounded-lg p-6">
        <h3 class="mb-4 text-base font-semibold text-ink">配置角色菜单：{{ roleMenuName }}</h3>
        <el-tree
          ref="menuTreeRef"
          :data="menuTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          show-checkbox
          default-expand-all
        />
        <div class="mt-6 flex justify-end gap-2">
          <button class="rounded px-3.5 py-2 text-sm text-ink-secondary transition hover:bg-surface-hover" @click="roleMenuVisible = false">取消</button>
          <button class="btn-primary" @click="submitRoleMenus">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmRemove } from '@/utils/confirm'
import { isCancel, reportError } from '@/utils/error'
import { Check, File, Folder, FolderKanban, KeyRound, ListTree, Menu, Pencil, Plus, ShieldCheck, Trash2, Users } from 'lucide-vue-next'
import ScopeNav, { type ScopeNavGroup } from '@/components/ScopeNav.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import {
  listRoles, createRole, updateRole, deleteRole,
  listMenus, createMenu, updateMenu, deleteMenu,
  listRoleMenus, assignRoleMenus,
  listUserRoles, assignUserRoles, listProjectMembers, listProjects,
  listPermissions, listRolePermissions, assignRolePermissions
} from '@/api'
import type { RbacRole, RbacMenu, PermissionDef, Project } from '@/api/types'

/** 左侧导航对应的面板标识 */
type RbacTab = 'roles' | 'menus' | 'matrix' | 'members'

const tab = ref<RbacTab>('roles')
const roles = ref<RbacRole[]>([])
const menus = ref<RbacMenu[]>([])
const roleLoading = ref(false)
const menuLoading = ref(false)

const navGroups = computed<ScopeNavGroup[]>(() => [
  {
    title: '权限配置',
    items: [
      { key: 'roles', label: '角色', icon: ShieldCheck, count: roles.value.length },
      { key: 'menus', label: '菜单（导航）', icon: Menu, count: menus.value.length },
      { key: 'matrix', label: '权限矩阵', icon: KeyRound }
    ]
  },
  {
    title: '成员授权',
    items: [{ key: 'members', label: '用户角色', icon: Users }]
  }
])

// 角色对话框
const roleVisible = ref(false)
const roleEditing = ref<RbacRole | null>(null)
const roleForm = ref<{ code: string; name: string; description: string; sortOrder: number }>({ code: '', name: '', description: '', sortOrder: 0 })

// 菜单对话框
const menuVisible = ref(false)
const menuEditing = ref<RbacMenu | null>(null)
const menuForm = ref<{ code: string; name: string; parentId: string; sortOrder: number }>({ code: '', name: '', parentId: '', sortOrder: 0 })

// 角色-菜单
const roleMenuVisible = ref(false)
const roleMenuName = ref('')
const roleMenuTarget = ref('')
/** el-tree 仅需勾选读写能力，用结构化类型替代 any（Element Plus 未导出稳定的实例类型） */
interface CheckableTreeRef {
  setCheckedKeys: (keys: string[]) => void
  getCheckedKeys: () => string[]
}
const menuTreeRef = ref<CheckableTreeRef | null>(null)

// 用户角色（选定项目后列出全部成员）
const userProjectId = ref('')
const projectOptions = ref<Project[]>([])
const memberRows = ref<{ userId: string; username: string; displayName: string; avatarUrl: string | null; roleIds: string[] }[]>([])
const userRoleLoading = ref(false)
const currentProjectName = computed(() => projectOptions.value.find((p) => p.id === userProjectId.value)?.name || '')

// 权限矩阵
interface MatrixGroup { module: string; items: PermissionDef[] }
const matrixRoleId = ref('')
const matrixLoading = ref(false)
const allPermissions = ref<PermissionDef[]>([])
const matrixChecked = ref<string[]>([])
const currentMatrixRoleName = computed(() => roles.value.find((r) => r.id === matrixRoleId.value)?.name || '')
const matrixGroups = computed<MatrixGroup[]>(() => {
  const map = new Map<string, PermissionDef[]>()
  allPermissions.value.forEach((p) => {
    if (!map.has(p.module)) map.set(p.module, [])
    map.get(p.module)!.push(p)
  })
  return Array.from(map.entries()).map(([module, items]) => ({
    module,
    items: items.sort((a, b) => a.sortOrder - b.sortOrder)
  }))
})
function togglePermission(code: string) {
  const idx = matrixChecked.value.indexOf(code)
  if (idx >= 0) matrixChecked.value.splice(idx, 1)
  else matrixChecked.value.push(code)
}
function isModuleAllChecked(g: MatrixGroup) {
  return g.items.length > 0 && g.items.every((p) => matrixChecked.value.includes(p.code))
}
function toggleModule(g: MatrixGroup) {
  const codes = g.items.map((p) => p.code)
  matrixChecked.value = isModuleAllChecked(g)
    ? matrixChecked.value.filter((c) => !codes.includes(c))
    : Array.from(new Set([...matrixChecked.value, ...codes]))
}

const menuTree = computed(() => buildTree(menus.value))
const menuRows = computed(() => flattenMenus(menuTree.value))

async function loadRoles() {
  roleLoading.value = true
  try { roles.value = await listRoles() } catch (e: unknown) { reportError(e, '加载角色失败') } finally { roleLoading.value = false }
}
async function loadMenus() {
  menuLoading.value = true
  try { menus.value = await listMenus() } catch (e: unknown) { reportError(e, '加载菜单失败') } finally { menuLoading.value = false }
}

interface MenuTreeNode extends RbacMenu {
  children: MenuTreeNode[]
}

function buildTree(flat: RbacMenu[]): MenuTreeNode[] {
  const map = new Map<string, MenuTreeNode>()
  const roots: MenuTreeNode[] = []
  flat.forEach((m) => map.set(m.id, { ...m, children: [] }))
  flat.forEach((m) => {
    const node = map.get(m.id)!
    const parent = m.parentId ? map.get(m.parentId) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

/** 树形菜单拍平为「节点 + 层级」，便于按缩进渲染纯列表 */
function flattenMenus(nodes: MenuTreeNode[]): { node: MenuTreeNode; depth: number }[] {
  const out: { node: MenuTreeNode; depth: number }[] = []
  const walk = (list: MenuTreeNode[], depth: number) => {
    list.forEach((node) => {
      out.push({ node, depth })
      walk(node.children, depth + 1)
    })
  }
  walk(nodes, 0)
  return out
}

/** 父级选择器的虚拟根：选中它即顶级，避免用空字符串充当 node-key */
const ROOT_PARENT_KEY = '__root__'

/** 父级候选树：剔除自身及其后代，防止把菜单挂到自己的子树下形成环 */
const parentTreeOptions = computed<MenuTreeNode[]>(() => {
  const selfId = menuEditing.value?.id
  const prune = (nodes: MenuTreeNode[]): MenuTreeNode[] =>
    nodes.filter((n) => n.id !== selfId).map((n) => ({ ...n, children: prune(n.children) }))
  return [{ id: ROOT_PARENT_KEY, code: '', name: '无（顶级）', children: prune(menuTree.value) }]
})

/** 表单的 parentId 与树选择器取值互转：空串 ⇄ 虚拟根 */
const parentSelectValue = computed<string>({
  get: () => menuForm.value.parentId || ROOT_PARENT_KEY,
  set: (v) => { menuForm.value.parentId = !v || v === ROOT_PARENT_KEY ? '' : v }
})

function openRole(row?: RbacRole) {
  roleEditing.value = row || null
  roleForm.value = row ? { code: row.code, name: row.name, description: row.description || '', sortOrder: row.sortOrder || 0 }
    : { code: '', name: '', description: '', sortOrder: 0 }
  roleVisible.value = true
}
async function submitRole() {
  if (!roleForm.value.code.trim() || !roleForm.value.name.trim()) { ElMessage.warning('编码和名称必填'); return }
  try {
    if (roleEditing.value) await updateRole(roleEditing.value.id, { ...roleForm.value })
    else await createRole({ ...roleForm.value })
    ElMessage.success('已保存')
    roleVisible.value = false
    await loadRoles()
  } catch (e: unknown) { reportError(e, '保存失败') }
}
async function delRole(row: RbacRole) {
  try {
    await confirmRemove(row.name, '角色')
    await deleteRole(row.id)
    ElMessage.success('已删除')
    await loadRoles()
  } catch (e: unknown) { if (!isCancel(e)) reportError(e, '删除失败') }
}

function openMenu(row?: RbacMenu) {
  menuEditing.value = row || null
  menuForm.value = row ? { code: row.code, name: row.name, parentId: row.parentId || '', sortOrder: row.sortOrder || 0 }
    : { code: '', name: '', parentId: '', sortOrder: 0 }
  menuVisible.value = true
}
async function submitMenu() {
  if (!menuForm.value.code.trim() || !menuForm.value.name.trim()) { ElMessage.warning('编码和名称必填'); return }
  const payload = { ...menuForm.value, type: 'MENU' as const }
  try {
    if (menuEditing.value) await updateMenu(menuEditing.value.id, payload)
    else await createMenu(payload)
    ElMessage.success('已保存')
    menuVisible.value = false
    await loadMenus()
  } catch (e: unknown) { reportError(e, '保存失败') }
}
async function delMenu(row: RbacMenu) {
  try {
    await confirmRemove(row.name)
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    await loadMenus()
  } catch (e: unknown) { if (!isCancel(e)) reportError(e, '删除失败') }
}

async function openRoleMenus(row: RbacRole) {
  roleMenuTarget.value = row.id
  roleMenuName.value = row.name
  roleMenuVisible.value = true
  const checked = await listRoleMenus(row.id)
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(checked)
}
async function submitRoleMenus() {
  const checked = menuTreeRef.value?.getCheckedKeys() || []
  try {
    await assignRoleMenus(roleMenuTarget.value, checked)
    ElMessage.success('已保存')
    roleMenuVisible.value = false
  } catch (e: unknown) { reportError(e, '保存失败') }
}

async function loadProjectsForSelect() {
  try { projectOptions.value = await listProjects() } catch { projectOptions.value = [] }
}

/** 左侧项目列表点击：切换项目并加载其成员（重复点击当前项目不重复请求） */
function selectProject(projectId: string) {
  if (userProjectId.value === projectId) return
  userProjectId.value = projectId
  loadProjectMembers()
}

/** 选定项目后拉取该项目全部成员，并并发回填各自的角色 */
async function loadProjectMembers() {
  memberRows.value = []
  if (!userProjectId.value) return
  userRoleLoading.value = true
  try {
    const members = await listProjectMembers(userProjectId.value)
    memberRows.value = await Promise.all(members.map(async (m) => ({
      userId: m.userId,
      username: m.username,
      displayName: m.displayName,
      avatarUrl: m.avatarUrl,
      // 单个成员角色查询失败时降级为空，避免整表因一人异常而不可用
      roleIds: await listUserRoles(m.userId, userProjectId.value).catch(() => [] as string[])
    })))
  } catch (e: unknown) {
    reportError(e, '加载项目成员失败')
  } finally {
    userRoleLoading.value = false
  }
}

async function saveUserRoles(row: { userId: string; roleIds: string[] }) {
  if (!userProjectId.value) return
  try {
    await assignUserRoles(row.userId, userProjectId.value, row.roleIds)
    ElMessage.success('已保存用户角色')
  } catch (e: unknown) { reportError(e, '保存失败') }
}

async function loadAllPermissions() {
  if (allPermissions.value.length) return
  try { allPermissions.value = await listPermissions() } catch (e: unknown) { reportError(e, '加载权限失败') }
}

/** 左侧角色列表点击：切换角色并加载其权限（重复点击当前角色不重复请求） */
function selectMatrixRole(roleId: string) {
  if (matrixRoleId.value === roleId) return
  matrixRoleId.value = roleId
  loadMatrixForRole(roleId)
}
async function loadMatrixForRole(roleId: string) {
  if (!roleId) { matrixChecked.value = []; return }
  matrixLoading.value = true
  try {
    await loadAllPermissions()
    matrixChecked.value = await listRolePermissions(roleId)
  } catch (e: unknown) { reportError(e, '加载角色权限失败') } finally { matrixLoading.value = false }
}
async function saveMatrix() {
  if (!matrixRoleId.value) return
  try {
    await assignRolePermissions(matrixRoleId.value, matrixChecked.value)
    ElMessage.success('已保存权限配置')
  } catch (e: unknown) { reportError(e, '保存失败') }
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadMenus()])
  await loadProjectsForSelect()
})
</script>

<style scoped>
/* 父级树选择器：对齐 .input-base 的高度、圆角与描边，避免与同表单的原生输入框割裂 */
:deep(.el-select__wrapper) {
  min-height: 38px;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  box-shadow: 0 0 0 1px var(--line) inset;
}
:deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--primary) inset;
}

/* 权限码 chip：未选用浅描边，选中用品牌蓝半透明填充，与左侧导航选中态同源 */
.perm-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border-radius: 9999px;
  border: 1px solid var(--glass-line);
  background-color: rgba(255, 255, 255, 0.6);
  padding: 0.25rem 0.75rem;
  font-size: 0.75rem;
  line-height: 1rem;
  color: var(--ink-regular);
  transition: background-color 0.15s, border-color 0.15s, color 0.15s;
}
.perm-chip:hover {
  border-color: rgba(64, 158, 255, 0.4);
  color: var(--ink);
}
.perm-chip-on {
  border-color: rgba(64, 158, 255, 0.45);
  background-color: rgba(64, 158, 255, 0.14);
  color: var(--primary);
  font-weight: 500;
}

/* btn-primary 为全局样式，未定义禁用态，这里补齐矩阵保存按钮的置灰 */
.btn-primary:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  transform: none;
  box-shadow: none;
}
</style>

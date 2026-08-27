<template>
  <div class="rbac">
    <div class="header">
      <h1>权限管理</h1>
      <p class="tip">系统管理员可维护角色、菜单（导航）、权限码，以及用户-角色分配（受后端管理员鉴权保护）。</p>
    </div>

    <el-tabs v-model="tab">
      <!-- 角色管理 -->
      <el-tab-pane label="角色" name="roles">
        <div class="toolbar">
          <el-button type="primary" @click="openRole()">新建角色</el-button>
        </div>
        <el-table :data="roles" v-loading="roleLoading" border>
          <el-table-column prop="code" label="编码" width="160" />
          <el-table-column prop="name" label="名称" width="160" />
          <el-table-column prop="description" label="描述" />
          <el-table-column label="菜单（导航）" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="openRoleMenus(row)">配置</el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="openRole(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="delRole(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 菜单（导航） -->
      <el-tab-pane label="菜单（导航）" name="menus">
        <div class="toolbar">
          <el-button type="primary" @click="openMenu()">新建菜单</el-button>
        </div>
        <el-table :data="menus" v-loading="menuLoading" border row-key="id" default-expand-all :tree-props="{ children: 'children' }">
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="code" label="编码" width="200" />
          <el-table-column prop="type" label="类型" width="100">
            <template #default>
              <el-tag type="primary">菜单</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="openMenu(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="delMenu(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 用户角色 -->
      <el-tab-pane label="用户角色" name="users">
        <div class="toolbar">
          <el-select v-model="userProjectId" filterable placeholder="选择项目" style="width: 240px" @change="loadUserRoles">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.name} (${p.projectKey})`" :value="p.id" />
          </el-select>
          <el-select v-model="userKeyword" filterable placeholder="选择用户" style="width: 240px" @change="onUserPicked">
            <el-option v-for="u in userOptions" :key="u.id" :label="`${u.displayName} (${u.username})`" :value="u.id" />
          </el-select>
        </div>
        <el-table :data="userRoleRows" v-loading="userRoleLoading" border>
          <el-table-column prop="username" label="用户名" width="160" />
          <el-table-column prop="displayName" label="显示名" width="160" />
          <el-table-column label="角色" min-width="240">
            <template #default="{ row }">
              <el-select v-model="row.roleIds" multiple style="width: 100%" @change="saveUserRoles(row)">
                <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <!-- 权限矩阵 -->
      <el-tab-pane label="权限矩阵" name="matrix">
        <div class="toolbar">
          <el-select v-model="matrixRoleId" filterable placeholder="选择角色" style="width: 240px" @change="loadMatrixForRole">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <el-button type="primary" :disabled="!matrixRoleId" @click="saveMatrix">保存</el-button>
        </div>
        <div v-if="!matrixRoleId" class="empty-tip">请先选择角色</div>
        <div v-else v-loading="matrixLoading" class="matrix-wrap">
          <div v-for="g in matrixGroups" :key="g.module" class="matrix-group">
            <div class="matrix-group-title">{{ g.module }}</div>
            <el-checkbox-group v-model="matrixChecked">
              <el-checkbox v-for="p in g.items" :key="p.code" :value="p.code" :label="p.code">
                <span class="perm-label">{{ p.name }}</span>
                <span class="perm-code">{{ p.code }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 角色对话框 -->
    <el-dialog v-model="roleVisible" :title="roleEditing ? '编辑角色' : '新建角色'" width="460px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="编码" required><el-input v-model="roleForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="roleForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="roleForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- 菜单对话框 -->
    <el-dialog v-model="menuVisible" :title="menuEditing ? '编辑菜单' : '新建菜单'" width="460px">
      <el-form :model="menuForm" label-width="80px">
        <el-form-item label="编码" required><el-input v-model="menuForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="menuForm.name" /></el-form-item>
        <el-form-item label="父级">
          <el-select v-model="menuForm.parentId" clearable placeholder="无（顶级）">
            <el-option v-for="m in menus" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="menuForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="menuVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMenu">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色-菜单分配 -->
    <el-dialog v-model="roleMenuVisible" :title="`配置角色菜单：${roleMenuName}`" width="480px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
      />
      <template #footer>
        <el-button @click="roleMenuVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoleMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRoles, createRole, updateRole, deleteRole,
  listMenus, createMenu, updateMenu, deleteMenu,
  listRoleMenus, assignRoleMenus,
  listUserRoles, assignUserRoles,
  listPermissions, listRolePermissions, assignRolePermissions,
  listProjects, listUsers
} from '@/api'
import type { RbacRole, RbacMenu, PermissionDef, Project, UserVO } from '@/api/types'

const tab = ref('roles')
const roles = ref<RbacRole[]>([])
const menus = ref<RbacMenu[]>([])
const roleLoading = ref(false)
const menuLoading = ref(false)

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
const menuTreeRef = ref<any>(null)

// 用户角色
const userProjectId = ref('')
const userKeyword = ref('')
const userOptions = ref<UserVO[]>([])
const projectOptions = ref<Project[]>([])
const userRoleRows = ref<{ userId: string; username: string; displayName: string; roleIds: string[] }[]>([])
const userRoleLoading = ref(false)

// 权限矩阵
const matrixRoleId = ref('')
const matrixLoading = ref(false)
const allPermissions = ref<PermissionDef[]>([])
const matrixChecked = ref<string[]>([])
const matrixGroups = computed(() => {
  const map = new Map<string, PermissionDef[]>()
  allPermissions.value.forEach((p) => {
    if (!map.has(p.module)) map.set(p.module, [])
    map.get(p.module)!.push(p)
  })
  return Array.from(map.entries()).map(([module, items]) => ({
    module,
    items: items.sort((a, b) => a.sortOrder - b.sortOrder),
  }))
})

const menuTree = computed(() => buildTree(menus.value))

async function loadRoles() {
  roleLoading.value = true
  try { roles.value = await listRoles() } catch (e: any) { ElMessage.error(e?.msg || '加载角色失败') } finally { roleLoading.value = false }
}
async function loadMenus() {
  menuLoading.value = true
  try { menus.value = await listMenus() } catch (e: any) { ElMessage.error(e?.msg || '加载菜单失败') } finally { menuLoading.value = false }
}

function buildTree(flat: RbacMenu[]) {
  const map = new Map<string, any>()
  const roots: any[] = []
  flat.forEach((m) => map.set(m.id, { ...m, children: [] }))
  flat.forEach((m) => {
    const node = map.get(m.id)!
    if (m.parentId && map.has(m.parentId)) map.get(m.parentId)!.children.push(node)
    else roots.push(node)
  })
  return roots
}

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
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
}
async function delRole(row: RbacRole) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('已删除')
    await loadRoles()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.msg || '删除失败') }
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
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
}
async function delMenu(row: RbacMenu) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '提示', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    await loadMenus()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.msg || '删除失败') }
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
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
}

async function loadProjectsForSelect() {
  try { projectOptions.value = await listProjects() } catch { projectOptions.value = [] }
  try { userOptions.value = await listUsers() } catch { userOptions.value = [] }
}
async function onUserPicked(userId: string) {
  if (!userProjectId.value) { ElMessage.warning('请先选择项目'); return }
  const rolesOfUser = await listUserRoles(userId, userProjectId.value)
  const u = userOptions.value.find((x) => x.id === userId)
  const exist = userRoleRows.value.find((r) => r.userId === userId)
  if (exist) { exist.roleIds = rolesOfUser } else {
    userRoleRows.value.push({ userId, username: u?.username || '', displayName: u?.displayName || '', roleIds: rolesOfUser })
  }
}
async function saveUserRoles(row: { userId: string; roleIds: string[] }) {
  if (!userProjectId.value) return
  try {
    await assignUserRoles(row.userId, userProjectId.value, row.roleIds)
    ElMessage.success('已保存用户角色')
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
}
async function loadUserRoles() {
  // 切换项目时清空已选用户
  userRoleRows.value = []
  userKeyword.value = ''
}

async function loadAllPermissions() {
  if (allPermissions.value.length) return
  try { allPermissions.value = await listPermissions() } catch (e: any) { ElMessage.error(e?.msg || '加载权限失败') }
}
async function loadMatrixForRole(roleId: string) {
  if (!roleId) { matrixChecked.value = []; return }
  matrixLoading.value = true
  try {
    await loadAllPermissions()
    matrixChecked.value = await listRolePermissions(roleId)
  } catch (e: any) { ElMessage.error(e?.msg || '加载角色权限失败') } finally { matrixLoading.value = false }
}
async function saveMatrix() {
  if (!matrixRoleId.value) return
  try {
    await assignRolePermissions(matrixRoleId.value, matrixChecked.value)
    ElMessage.success('已保存权限配置')
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadMenus()])
  await loadProjectsForSelect()
})
</script>

<style scoped>
.rbac { padding: 20px; }
.header h1 { font-size: 20px; margin: 0; }
.tip { color: #888; font-size: 13px; margin: 4px 0 16px; }
.toolbar { margin-bottom: 12px; display: flex; gap: 12px; }
.empty-tip { color: #999; padding: 40px 0; text-align: center; }
.matrix-wrap { display: flex; flex-direction: column; gap: 20px; }
.matrix-group { border: 1px solid #ebeef5; border-radius: 6px; padding: 14px 18px; }
.matrix-group-title { font-weight: 600; font-size: 14px; margin-bottom: 10px; color: #303133; text-transform: uppercase; }
.perm-label { font-size: 13px; }
.perm-code { font-size: 11px; color: #909399; margin-left: 6px; }
</style>

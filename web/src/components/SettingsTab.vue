<template>
  <div>
    <div class="card shadow-sm mb-3">
      <div class="card-header bg-white d-flex align-items-center">
        <i class="bi bi-lightning-charge me-1 text-primary"></i>
        <span>系统设置快捷项</span>
        <span class="ms-auto badge text-bg-light">已添加 {{ config?.settings?.length || 0 }}</span>
      </div>
      <div class="card-body">
        <p class="text-secondary small">
          开启后显示在手机主页与概览页，点击直接跳转。上下箭头调整顺序（主页按此顺序展示）。
        </p>

        <!-- 当前列表（有序，可勾选/排序/删除） -->
        <div v-if="settings.length" class="list-group mb-3">
          <div v-for="(s, i) in settings" :key="s.id"
               class="list-group-item d-flex align-items-center py-2">
            <input class="form-check-input me-2" type="checkbox" :id="'sw-' + s.id"
                   :checked="s.enabled" @change="toggle(s.id)" />
            <div class="me-auto">
              <label class="form-check-label mb-0" :for="'sw-' + s.id">{{ s.label || s.id }}</label>
              <div>
                <code class="text-secondary small">{{ s.action }}</code>
                <span v-if="!s.builtin" class="badge text-bg-warning ms-1">自定义</span>
                <span v-if="s.data" class="text-secondary small ms-1">· {{ s.data }}</span>
              </div>
            </div>
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-secondary" :disabled="i === 0"
                      @click="move(i, -1)" title="上移"><i class="bi bi-arrow-up"></i></button>
              <button class="btn btn-outline-secondary" :disabled="i === settings.length - 1"
                      @click="move(i, 1)" title="下移"><i class="bi bi-arrow-down"></i></button>
              <button class="btn btn-outline-danger" @click="remove(i)" title="删除"><i class="bi bi-trash"></i></button>
            </div>
          </div>
        </div>
        <p v-else class="text-secondary small">尚未添加任何设置项，从下方目录中选择添加。</p>

        <!-- 从本机扫描目录添加 -->
        <div class="mb-2 fw-semibold small">从本机可用入口添加</div>
        <div class="d-flex flex-wrap gap-2 mb-3">
          <button v-for="p in availablePresets" :key="p.id" class="btn btn-outline-primary btn-sm"
                  @click="addFromCatalog(p)">
            <i class="bi bi-plus-lg me-1"></i>{{ p.label || p.id }}
          </button>
          <span v-if="!availablePresets.length" class="text-secondary small">已全部添加</span>
        </div>

        <!-- 推荐（非标准入口，如证书安装） -->
        <div v-if="suggested.length" class="mb-2 fw-semibold small">推荐（非标准入口）</div>
        <div v-if="suggested.length" class="d-flex flex-wrap gap-2 mb-3">
          <button v-for="p in suggested" :key="p.id" class="btn btn-outline-secondary btn-sm"
                  @click="addFromCatalog(p)">
            <i class="bi bi-plus-lg me-1"></i>{{ p.label || p.id }}
          </button>
        </div>

        <!-- 自定义入口 -->
        <details class="mb-2">
          <summary class="fw-semibold small" style="cursor:pointer">自定义入口（高级）</summary>
          <div class="border rounded p-2 mt-2">
            <div class="row g-2">
              <div class="col-4">
                <input v-model="c.label" class="form-control form-control-sm" placeholder="显示名称" />
              </div>
              <div class="col-5">
                <input v-model="c.action" class="form-control form-control-sm" placeholder="Intent action，如 android.settings.WIFI_SETTINGS" />
              </div>
              <div class="col-3">
                <input v-model="c.data" class="form-control form-control-sm" placeholder="data URI（可选）" />
              </div>
            </div>
            <div class="form-text small">data 支持占位符 <code>{pkg}</code> 表示本应用包名。例如证书安装可尝试 action: <code>android.credentials.INSTALL</code>。</div>
            <button class="btn btn-primary btn-sm mt-2" :disabled="!c.label || !c.action" @click="addCustom">
              <i class="bi bi-plus-lg me-1"></i>添加自定义
            </button>
          </div>
        </details>
      </div>
    </div>

    <div class="card shadow-sm mb-3">
      <div class="card-header bg-white">
        <i class="bi bi-shield-lock me-1 text-primary"></i>访问口令
      </div>
      <div class="card-body d-flex align-items-center gap-2 flex-wrap">
        <code class="fs-5">{{ token }}</code>
        <button class="btn btn-outline-danger btn-sm" @click="rotate">
          <i class="bi bi-arrow-clockwise me-1"></i>重置口令
        </button>
        <span class="text-secondary small">重置后需在手机主页查看新口令</span>
      </div>
    </div>

    <div class="card shadow-sm">
      <div class="card-header bg-white">
        <i class="bi bi-info-circle me-1 text-primary"></i>说明
      </div>
      <div class="card-body small text-secondary">
        <ul class="mb-0 ps-3">
          <li>可用入口由 App 在手机上运行时扫描 <code>Settings</code> 的全部 ACTION 常量并过滤本机可解析项得到，自动适配不同系统/版本，无需手动维护。</li>
          <li>局域网服务器随 App 进程运行：打开 Goto 即提供 Web 管理，结束应用进程后关闭。</li>
          <li>口令仅为局域网弱防护，请勿将端口暴露到公网。</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api.js'

const config = ref(null)
const catalog = ref({ presets: [], added: [] })
const token = ref('')

const settings = computed(() => config.value?.settings || [])
const addedIds = computed(() => new Set(settings.value.map(s => s.id)))
const availablePresets = computed(() =>
  catalog.value.presets.filter(p => !addedIds.value.has(p.id) && p.builtin))
const suggested = computed(() =>
  catalog.value.presets.filter(p => !addedIds.value.has(p.id) && !p.builtin))

const c = ref({ label: '', action: '', data: '' })

async function load() {
  const cfg = await api('/api/config')
  config.value = cfg
  token.value = cfg.token
  catalog.value = await api('/api/settings-catalog')
}

async function save(nextSettings) {
  config.value = { ...config.value, settings: nextSettings }
  await api('/api/config', {
    method: 'PUT',
    body: JSON.stringify({ settings: nextSettings.map(s => ({
      id: s.id, label: s.label, action: s.action, data: s.data,
      builtin: s.builtin, enabled: s.enabled,
    })) }),
  })
}

function toggle(id) {
  const next = settings.value.map(s => s.id === id ? { ...s, enabled: !s.enabled } : s)
  save(next)
}

function move(i, dir) {
  const arr = settings.value.slice()
  const j = i + dir
  if (j < 0 || j >= arr.length) return
  const tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
  save(arr)
}

function remove(i) {
  const arr = settings.value.slice()
  arr.splice(i, 1)
  save(arr)
}

function addFromCatalog(p) {
  if (addedIds.value.has(p.id)) return
  const next = settings.value.concat([{
    id: p.id, label: p.label, action: p.action, data: p.data, builtin: p.builtin, enabled: true,
  }])
  save(next)
}

function addCustom() {
  const label = c.value.label.trim()
  const action = c.value.action.trim()
  if (!label || !action) return
  const id = 'custom_' + Date.now()
  const next = settings.value.concat([{
    id, label, action, data: c.value.data.trim(), builtin: false, enabled: true,
  }])
  c.value = { label: '', action: '', data: '' }
  save(next)
}

async function rotate() {
  const r = await api('/api/token', { method: 'POST' })
  token.value = r.token
  config.value = { ...config.value, token: r.token }
}

onMounted(load)
</script>

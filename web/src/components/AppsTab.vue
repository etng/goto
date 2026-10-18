<template>
  <div class="card shadow-sm">
    <div class="card-header bg-white">
      <i class="bi bi-phone me-1 text-primary"></i>应用管理
      <span class="text-secondary small ms-2">固定到手机主页的快捷应用</span>
    </div>
    <div class="card-body">
      <div class="input-group mb-3">
        <span class="input-group-text"><i class="bi bi-search"></i></span>
        <input v-model="query" class="form-control" placeholder="搜索应用名称或包名" />
        <button v-if="query" class="btn btn-outline-secondary" @click="query = ''">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
      <ul class="nav nav-tabs mb-2">
        <li class="nav-item" v-for="t in tabs" :key="t.key">
          <button class="nav-link" :class="{ active: tab === t.key }" @click="tab = t.key">
            {{ t.label }}
          </button>
        </li>
      </ul>
      <div v-if="usageDenied" class="alert alert-warning small py-2">
        <i class="bi bi-exclamation-triangle me-1"></i>
        手机未授权「使用情况访问」，常用列表为空。请在 Goto 主页点击横幅授权。
      </div>
      <ul class="list-group list-group-flush">
        <li v-for="app in shown" :key="app.pkg"
            class="list-group-item d-flex align-items-center gap-2 py-2 px-0">
          <img :src="iconUrl(app.pkg)" class="rounded" width="32" height="32" alt="" />
          <div class="flex-grow-1 overflow-hidden">
            <div class="text-truncate">{{ app.label }}</div>
            <div class="text-secondary small text-truncate">
              {{ app.pkg }}
              <span v-if="app.count" class="ms-1">· {{ app.count }} 次/7天</span>
              <span v-if="app.firstInstall" class="ms-1">· {{ fmtTime(app.firstInstall) }} 安装</span>
            </div>
          </div>
          <span v-if="app.isSystem" class="badge text-bg-light border">系统</span>
          <button class="btn btn-sm" :class="pinned.has(app.pkg) ? 'btn-warning' : 'btn-outline-primary'"
                  @click="togglePin(app.pkg)">
            <i class="bi" :class="pinned.has(app.pkg) ? 'bi-pin-angle-fill' : 'bi-pin-angle'"></i>
            {{ pinned.has(app.pkg) ? '已固定' : '固定' }}
          </button>
        </li>
        <li v-if="!shown.length" class="list-group-item text-secondary">无匹配应用</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, iconUrl, fmtTime } from '../api.js'

const tabs = [
  { key: 'frequent', label: '常用' },
  { key: 'new', label: '新安装(7天)' },
  { key: 'all', label: '全部' },
]

const tab = ref('frequent')
const query = ref('')
const data = ref({ all: [], frequent: [], new: [] })
const pinned = ref(new Set())
const usageDenied = ref(false)

const shown = computed(() => {
  let list = data.value[tab.value] || []
  if (query.value) {
    const q = query.value.toLowerCase()
    list = list.filter(a => a.label.toLowerCase().includes(q) || a.pkg.toLowerCase().includes(q))
  }
  return list
})

async function load() {
  const [r, cfg, info] = await Promise.all([
    api('/api/apps'),
    api('/api/config'),
    api('/api/info'),
  ])
  data.value = r
  pinned.value = new Set(cfg.pinnedApps)
  usageDenied.value = !info.usageAccess
}

async function togglePin(pkg) {
  const next = new Set(pinned.value)
  if (next.has(pkg)) next.delete(pkg)
  else next.add(pkg)
  pinned.value = next
  await api('/api/config', {
    method: 'PUT',
    body: JSON.stringify({ pinnedApps: [...next] }),
  })
}

onMounted(load)
</script>

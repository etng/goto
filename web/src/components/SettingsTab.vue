<template>
  <div>
    <div class="card shadow-sm mb-3">
      <div class="card-header bg-white">
        <i class="bi bi-lightning-charge me-1 text-primary"></i>系统设置快捷项
      </div>
      <div class="card-body">
        <p class="text-secondary small">开启后显示在手机主页与概览页，点击直接跳转对应系统页面。</p>
        <div v-for="s in catalog" :key="s.id" class="form-check form-switch d-flex align-items-center py-1">
          <input class="form-check-input me-2" type="checkbox" role="switch"
                 :id="'sw-' + s.id" :checked="enabled(s.id)" @change="toggle(s.id)" />
          <label class="form-check-label" :for="'sw-' + s.id">{{ labelOf(s.id) }}</label>
          <code class="ms-auto text-secondary small">{{ s.id }}</code>
        </div>
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
          <li>局域网服务器随 App 进程运行：打开 Goto 即提供 Web 管理，结束应用进程后关闭。</li>
          <li>口令仅为局域网弱防护，请勿将端口暴露到公网。</li>
          <li>「网页处理应用」收藏的是"能打开网页的应用"；特定域名（如京东、微信链接）被系统定义为 App Link 时，仅收藏列表仍会按域名精确匹配。</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const catalog = ref([])
const config = ref(null)
const token = ref('')

const LABELS = {
  dev_options: '开发者选项', wireless_debug: '无线调试', usage_access: '使用情况访问',
  app_list: '应用管理', notifications: '通知管理', wifi: 'WiFi', bluetooth: '蓝牙',
  location: '位置信息', storage: '存储', display: '显示', sound: '声音', battery: '电池',
  language: '语言', security: '安全', data_usage: '流量使用', accessibility: '无障碍',
  home_settings: '桌面与手势', app_details: '本应用信息',
}
const labelOf = (id) => LABELS[id] || id

const enabled = (id) => (config.value?.settings || []).find(s => s.id === id)?.enabled ?? false

async function load() {
  const cfg = await api('/api/config')
  config.value = cfg
  token.value = cfg.token
  catalog.value = cfg.settings.map(s => ({ id: s.id }))
}

async function toggle(id) {
  const settings = (config.value.settings || []).map(s =>
    s.id === id ? { ...s, enabled: !s.enabled } : s)
  config.value = { ...config.value, settings }
  await api('/api/config', { method: 'PUT', body: JSON.stringify({ settings }) })
}

async function rotate() {
  const r = await api('/api/token', { method: 'POST' })
  token.value = r.token
  config.value = { ...config.value, token: r.token }
}

onMounted(load)
</script>

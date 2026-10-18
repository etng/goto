<template>
  <div>
    <div class="card shadow-sm mb-3">
      <div class="card-header bg-white">
        <i class="bi bi-phone me-1 text-primary"></i>设备信息
      </div>
      <div class="card-body row g-3">
        <div class="col-sm-6">型号：<strong>{{ info.model }}</strong></div>
        <div class="col-sm-6">Android：API {{ info.sdk }}</div>
        <div class="col-sm-6">应用版本：{{ info.appVersion }}</div>
        <div class="col-sm-6">
          管理地址：<code>{{ info.ip ? `http://${info.ip}:${info.port}` : '未连接 WiFi' }}</code>
        </div>
        <div class="col-12">
          <span class="badge" :class="info.usageAccess ? 'text-bg-success' : 'text-bg-danger'">
            {{ info.usageAccess ? '已授权「使用情况访问」' : '未授权「使用情况访问」，无法读取高频应用' }}
          </span>
        </div>
      </div>
    </div>

    <div class="card shadow-sm mb-3">
      <div class="card-header bg-white">
        <i class="bi bi-lightning me-1 text-primary"></i>系统设置快捷（点击在手机上打开）
      </div>
      <div class="card-body d-flex flex-wrap gap-2">
        <button v-for="s in enabledSettings" :key="s.id"
                class="btn btn-outline-primary btn-sm" @click="openSettings(s.id)">
          {{ s.label }}
        </button>
        <span v-if="!enabledSettings.length" class="text-secondary small">未启用任何设置项</span>
      </div>
    </div>

    <div class="card shadow-sm">
      <div class="card-header bg-white">
        <i class="bi bi-globe me-1 text-primary"></i>打开网页（手机端按收藏规则处理）
      </div>
      <div class="card-body">
        <div class="input-group">
          <input v-model="url" class="form-control" placeholder="输入网址，如 jd.com"
                 @keyup.enter="openUrl" />
          <button class="btn btn-primary" :disabled="!url" @click="openUrl">
            <i class="bi bi-arrow-right"></i> 打开
          </button>
        </div>
        <div v-if="openResult" class="small mt-2 text-secondary">
          {{ openResult }}
        </div>
        <div v-if="quickLinks.length" class="d-flex flex-wrap gap-2 mt-3">
          <button v-for="q in quickLinks" :key="q.url" class="btn btn-outline-secondary btn-sm"
                  @click="openLink(q.url)">
            {{ q.label }} <i class="bi bi-box-arrow-up-right ms-1"></i>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api.js'

const info = ref({})
const config = ref(null)
const url = ref('')
const openResult = ref('')

const enabledSettings = computed(() =>
  (config.value?.settings || []).filter(s => s.enabled).map(s => ({ id: s.id, label: s.label || s.id }))
)
const quickLinks = computed(() => config.value?.quickLinks || [])

function labelOf(id) {
  return (config.value?.settings || []).find(s => s.id === id)?.label || id
}

async function openSettings(id) {
  try {
    const r = await api('/api/open/settings', { method: 'POST', body: JSON.stringify({ id }) })
    openResult.value = r.ok ? `已打开：${labelOf(id)}` : `打开失败：${labelOf(id)}`
  } catch (e) { openResult.value = `失败：${e.message}` }
}

async function openUrl() {
  if (!url.value) return
  try {
    const r = await api('/api/open/url', { method: 'POST', body: JSON.stringify({ url: url.value }) })
    const modeMap = { auto: '已用收藏应用打开', chooser_first: '已用收藏（首个）打开', default: '已交给系统默认打开' }
    openResult.value = `${modeMap[r.mode] || r.mode}${r.pkg ? ' · ' + r.pkg : ''}`
  } catch (e) { openResult.value = `失败：${e.message}` }
}

function openLink(u) { url.value = u; openUrl() }

onMounted(async () => {
  info.value = await api('/api/info')
  config.value = await api('/api/config')
})
</script>

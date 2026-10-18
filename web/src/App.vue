<template>
  <div class="container py-4" style="max-width: 860px">
    <header class="d-flex align-items-center justify-content-between mb-4">
      <div class="d-flex align-items-center gap-2">
        <i class="bi bi-share" style="font-size: 1.6rem; color: #2563eb"></i>
        <h1 class="h4 mb-0">Goto 管理</h1>
      </div>
      <button class="btn btn-outline-danger btn-sm" @click="logout" title="退出（清除本地口令）">
        <i class="bi bi-box-arrow-right me-1"></i>退出
      </button>
    </header>

    <!-- 口令登录 -->
    <div v-if="!ready" class="card shadow-sm">
      <div class="card-body">
        <h5 class="card-title"><i class="bi bi-shield-lock me-2"></i>访问口令</h5>
        <p class="text-secondary small mb-3">
          在手机 Goto 主页的「局域网配置」卡片中查看口令。
        </p>
        <div class="input-group">
          <input v-model="tokenInput" class="form-control" placeholder="6 位口令"
                 type="password" @keyup.enter="submitToken" autocomplete="off" />
          <button class="btn btn-primary" :disabled="!tokenInput" @click="submitToken">进入</button>
        </div>
        <div v-if="authError" class="text-danger small mt-2">{{ authError }}</div>
      </div>
    </div>

    <!-- 主界面 -->
    <template v-else>
      <ul class="nav nav-pills mb-4 gap-1 flex-nowrap overflow-auto">
        <li v-for="t in tabs" :key="t.key" class="nav-item">
          <button class="nav-link" :class="{ active: tab === t.key }" @click="tab = t.key">
            <i :class="t.icon" class="me-1"></i>{{ t.label }}
          </button>
        </li>
      </ul>

      <OverviewTab v-if="tab === 'overview'" :key="reloadKey" />
      <HandlersTab v-else-if="tab === 'handlers'" :key="reloadKey" />
      <AppsTab v-else-if="tab === 'apps'" :key="reloadKey" />
      <LinksTab v-else-if="tab === 'links'" :key="reloadKey" />
      <SettingsTab v-else :key="reloadKey" />
    </template>

    <footer class="text-center text-secondary small mt-5">
      Goto · 局域网内可访问，请勿暴露到公网
    </footer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, getToken, setToken, clearToken } from './api.js'
import OverviewTab from './components/OverviewTab.vue'
import HandlersTab from './components/HandlersTab.vue'
import AppsTab from './components/AppsTab.vue'
import LinksTab from './components/LinksTab.vue'
import SettingsTab from './components/SettingsTab.vue'

const tabs = [
  { key: 'overview', label: '概览', icon: 'bi-speedometer2' },
  { key: 'handlers', label: '网页应用', icon: 'bi-globe2' },
  { key: 'apps', label: '应用管理', icon: 'bi-phone' },
  { key: 'links', label: '快捷链接', icon: 'bi-link-45deg' },
  { key: 'settings', label: '设置', icon: 'bi-gear' },
]

const tab = ref('overview')
const ready = ref(false)
const tokenInput = ref('')
const authError = ref('')
const reloadKey = ref(0)

async function tryLoad() {
  authError.value = ''
  try {
    await api('/api/info')
    ready.value = true
  } catch (e) {
    ready.value = false
    if (e.status === 401) {
      clearToken()
      authError.value = '口令不正确或已过期'
    } else {
      authError.value = `无法连接：${e.message}`
    }
  }
}

function submitToken() {
  setToken(tokenInput.value.trim())
  tryLoad()
}

function logout() {
  clearToken()
  ready.value = false
}

onMounted(() => {
  if (getToken()) {
    tryLoad()
  } else {
    ready.value = false
  }
})
</script>

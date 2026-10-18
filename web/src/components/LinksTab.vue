<template>
  <div class="card shadow-sm mb-3">
    <div class="card-header bg-white">
      <i class="bi bi-link-45deg me-1 text-primary"></i>网页快捷链接
      <span class="text-secondary small ms-2">显示在手机主页，一键打开</span>
    </div>
    <div class="card-body">
      <div class="input-group mb-3">
        <input v-model="newLabel" class="form-control" placeholder="名称，如：京东搜索" />
        <input v-model="newUrl" class="form-control" placeholder="网址，如 https://search.jd.com/Search?keyword=x"
               @keyup.enter="add" />
        <button class="btn btn-primary" :disabled="!newLabel || !newUrl" @click="add">
          <i class="bi bi-plus-lg"></i> 添加
        </button>
      </div>
      <ul class="list-group list-group-flush">
        <li v-for="(l, i) in links" :key="i" class="list-group-item d-flex align-items-center gap-2 py-2 px-0">
          <i class="bi bi-globe text-secondary"></i>
          <div class="flex-grow-1 overflow-hidden">
            <div class="text-truncate">{{ l.label }}</div>
            <div class="text-secondary small text-truncate">{{ l.url }}</div>
          </div>
          <button class="btn btn-sm btn-outline-secondary" title="上移"
                  :disabled="i === 0" @click="move(i, -1)">
            <i class="bi bi-arrow-up"></i>
          </button>
          <button class="btn btn-sm btn-outline-secondary" title="下移"
                  :disabled="i === links.length - 1" @click="move(i, 1)">
            <i class="bi bi-arrow-down"></i>
          </button>
          <button class="btn btn-sm btn-outline-danger" title="删除" @click="remove(i)">
            <i class="bi bi-trash"></i>
          </button>
        </li>
        <li v-if="!links.length" class="list-group-item text-secondary">暂无快捷链接</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const links = ref([])
const newLabel = ref('')
const newUrl = ref('')

async function save() {
  await api('/api/config', { method: 'PUT', body: JSON.stringify({ quickLinks: links.value }) })
}

async function add() {
  const label = newLabel.value.trim()
  const url = newUrl.value.trim()
  if (!label || !url) return
  links.value.push({ label, url })
  newLabel.value = ''
  newUrl.value = ''
  await save()
}

function remove(i) {
  links.value.splice(i, 1)
  save()
}

function move(i, delta) {
  const j = i + delta
  if (j < 0 || j >= links.value.length) return
  const [a] = links.value.splice(i, 1)
  links.value.splice(j, 0, a)
  save()
}

onMounted(async () => {
  const cfg = await api('/api/config')
  links.value = cfg.quickLinks || []
})
</script>

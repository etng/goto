<template>
  <div class="card shadow-sm">
    <div class="card-header bg-white">
      <i class="bi bi-globe2 me-1 text-primary"></i>网页处理应用
      <span class="text-secondary small ms-2">
        打星收藏后，手机打开网页时只列出这些应用（不列全部）
      </span>
    </div>
    <div class="card-body p-0">
      <div v-if="loading" class="p-4 text-center text-secondary">加载中…</div>
      <ul class="list-group list-group-flush" v-else>
        <li v-for="h in handlers" :key="h.pkg"
            class="list-group-item d-flex align-items-center gap-2 py-2">
          <img :src="iconUrl(h.pkg)" class="rounded" width="30" height="30" alt="" />
          <div class="flex-grow-1 overflow-hidden">
            <div class="text-truncate">{{ h.label }}</div>
            <div class="text-secondary small text-truncate">{{ h.pkg }}</div>
          </div>
          <span class="badge text-bg-light border me-1" v-if="h.https">https</span>
          <span class="badge text-bg-light border me-1" v-if="h.http">http</span>
          <button v-if="isFav(h.pkg)" class="btn btn-sm btn-outline-secondary p-1"
                  title="上移（收藏优先级）" @click="move(h.pkg, -1)">
            <i class="bi bi-arrow-up"></i>
          </button>
          <button v-if="isFav(h.pkg)" class="btn btn-sm btn-outline-secondary p-1"
                  title="下移" @click="move(h.pkg, 1)">
            <i class="bi bi-arrow-down"></i>
          </button>
          <button class="btn btn-sm p-1" :class="isFav(h.pkg) ? 'text-warning' : 'text-secondary'"
                  :title="isFav(h.pkg) ? '取消收藏' : '收藏'"
                  @click="toggleFav(h.pkg)">
            <i class="bi" :class="isFav(h.pkg) ? 'bi-star-fill' : 'bi-star'"></i>
          </button>
        </li>
        <li v-if="!handlers.length" class="list-group-item text-secondary">未找到可打开网页的应用</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, iconUrl } from '../api.js'

const handlers = ref([])
const favorites = ref([])
const loading = ref(true)

const favSet = computed(() => new Set(favorites.value))
const isFav = (pkg) => favSet.value.has(pkg)

async function save(next) {
  favorites.value = next
  await api('/api/config', {
    method: 'PUT',
    body: JSON.stringify({ webFavorites: next }),
  })
}

function toggleFav(pkg) {
  save(isFav(pkg)
    ? favorites.value.filter(p => p !== pkg)
    : [...favorites.value, pkg])
}

function move(pkg, delta) {
  const l = [...favorites.value]
  const i = l.indexOf(pkg)
  const j = i + delta
  if (i < 0 || j < 0 || j >= l.length) return
  ;[l[i], l[j]] = [l[j], l[i]]
  save(l)
}

onMounted(async () => {
  const r = await api('/api/handlers')
  handlers.value = r.handlers
  favorites.value = r.favorites
  loading.value = false
})
</script>

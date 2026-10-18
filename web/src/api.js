const TOKEN_KEY = 'goto_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(t) {
  localStorage.setItem(TOKEN_KEY, t)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 统一 API 请求：自动附带访问口令。
 * 401 时抛错，由调用方触发重新输入口令。
 */
export async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) }
  const t = getToken()
  if (t) headers['X-Access-Token'] = t
  const res = await fetch(path, { ...opts, headers })
  if (res.status === 401) {
    const e = new Error('unauthorized')
    e.status = 401
    throw e
  }
  if (!res.ok) {
    let msg = res.statusText
    try { msg = (await res.json()).error || msg } catch (_) {}
    throw new Error(msg)
  }
  return res.json()
}

export function iconUrl(pkg) {
  return `/api/icon/${encodeURIComponent(pkg)}?token=${encodeURIComponent(getToken())}`
}

export function fmtTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

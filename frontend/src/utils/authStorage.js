const AUTH_KEYS = ['dropai_token', 'dropai_username', 'dropai_role', 'dropai_school_name']

function safeGet(storage, key) {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(storage, key, value) {
  try {
    if (value === undefined || value === null || value === '') storage.removeItem(key)
    else storage.setItem(key, String(value))
  } catch {
    // Ignore storage quota/privacy errors and keep the current flow alive.
  }
}

function safeRemove(storage, key) {
  try {
    storage.removeItem(key)
  } catch {
    // Ignore storage access errors.
  }
}

export function getAuthItem(key) {
  const sessionValue = safeGet(sessionStorage, key)
  if (sessionValue) return sessionValue

  const persistedValue = safeGet(localStorage, key)
  if (persistedValue) {
    safeSet(sessionStorage, key, persistedValue)
    return persistedValue
  }

  return ''
}

export function setAuthItem(key, value) {
  safeSet(sessionStorage, key, value)
  safeSet(localStorage, key, value)
}

export function removeAuthItem(key) {
  safeRemove(sessionStorage, key)
  safeRemove(localStorage, key)
}

export function hydrateAuthSession() {
  AUTH_KEYS.forEach(key => getAuthItem(key))
}

export function setAuthSession(result = {}) {
  setAuthItem('dropai_token', result.token || '')
  setAuthItem('dropai_username', result.username || '')
  setAuthItem('dropai_role', result.role || 'USER')

  if (result.schoolName) setAuthItem('dropai_school_name', result.schoolName)
  else removeAuthItem('dropai_school_name')
}

export function clearAuthSession() {
  AUTH_KEYS.forEach(removeAuthItem)
}

export function getAuthToken() {
  return getAuthItem('dropai_token')
}

export function getAuthRole() {
  return getAuthItem('dropai_role')
}

export function isAuthenticated() {
  return Boolean(getAuthToken())
}

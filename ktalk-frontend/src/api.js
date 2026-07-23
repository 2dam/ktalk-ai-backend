// When the frontend is served by Spring Boot, API calls use the same origin.
export const API_BASE = import.meta.env.VITE_API_BASE || ''
export const API_URL = `${API_BASE}/api/contents`
export const AI_URL = `${API_BASE}/api/ai`
export const AUTH_URL = `${API_BASE}/api/auth`
export const LEARNING_URL = `${API_BASE}/api/learning`
export const ASSESSMENT_URL = `${API_BASE}/api/assessment`
export const ASSEMBLY_URL = `${API_BASE}/api/assembly`
export const REVIEW_URL = `${API_BASE}/api/review`

// 로그인 토큰이 있으면 Authorization 헤더를 붙여준다. (복습 알람은 로그인 사용자 전용)
export function authHeaders(extra = {}) {
  const token = localStorage.getItem('token')
  return token ? { ...extra, Authorization: `Bearer ${token}` } : { ...extra }
}

export function hasToken() {
  return !!localStorage.getItem('token')
}

// 배포 시 Vercel/Netlify 등에서 VITE_API_BASE 환경변수로 실제 백엔드(Render) 주소를 지정한다.
export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
export const API_URL = `${API_BASE}/api/contents`
export const AI_URL = `${API_BASE}/api/ai`
export const AUTH_URL = `${API_BASE}/api/auth`
export const LEARNING_URL = `${API_BASE}/api/learning`

// When the frontend is served by Spring Boot, API calls use the same origin.
export const API_BASE = import.meta.env.VITE_API_BASE || ''
export const API_URL = `${API_BASE}/api/contents`
export const AI_URL = `${API_BASE}/api/ai`
export const AUTH_URL = `${API_BASE}/api/auth`
export const LEARNING_URL = `${API_BASE}/api/learning`
export const BILLING_URL = `${API_BASE}/api/billing`

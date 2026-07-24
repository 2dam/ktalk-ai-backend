import { useState } from 'react'
import axios from 'axios'
import ktalkLogo from './assets/ktalk-logo.png'
import { API_BASE, AUTH_URL } from './api'

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.87 2.7-6.62Z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.8.54-1.84.86-3.06.86-2.35 0-4.34-1.59-5.05-3.72H.95v2.33A9 9 0 0 0 9 18Z" />
      <path fill="#FBBC05" d="M3.95 10.7A5.4 5.4 0 0 1 3.67 9c0-.59.1-1.16.28-1.7V4.97H.95A9 9 0 0 0 0 9c0 1.45.35 2.83.95 4.03l3-2.33Z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.46 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .95 4.97l3 2.33C4.66 5.17 6.65 3.58 9 3.58Z" />
    </svg>
  )
}

const WELCOME_FEATURES = [
  { icon: '💬', copy: 'AI와 실시간으로 대화하며 연습' },
  { icon: '🎯', copy: '내 실력에 맞춘 맞춤 학습' },
  { icon: '🔥', copy: '하루 5분, 부담 없이 꾸준히' },
]

const WELCOME_PLANS = [
  { name: 'Pro', price: '$9.90', label: '개인 학습자' },
  { name: 'Business', price: '$19.90', label: '강사와 팀' },
]

export function AuthCard({ onAuthenticated, onClose, compact = false }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const updateField = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  const switchMode = (nextMode) => {
    setMode(nextMode)
    setError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const endpoint = mode === 'login' ? 'login' : 'register'
      const payload = mode === 'login'
        ? { username: form.username, password: form.password }
        : { username: form.username, email: form.email, password: form.password }
      const response = await axios.post(`${AUTH_URL}/${endpoint}`, payload)
      if (response.data.success) {
        localStorage.setItem('token', response.data.token)
        onAuthenticated(response.data.user)
      } else {
        setError(response.data.message || '요청을 처리하지 못했습니다.')
      }
    } catch (err) {
      setError(err.response?.data?.message || '요청을 처리하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <div className={`welcome-card glass-card auth-card${compact ? ' auth-card-compact' : ''}`}>
        {onClose && (
          <button type="button" className="auth-close" onClick={onClose} aria-label="닫기">×</button>
        )}
        <div className="welcome-brand">
          <img src={ktalkLogo} alt="" className="welcome-logo" />
          <span>ktalk</span>
        </div>

        {!compact && <div className="welcome-icon">💬</div>}

        {!compact && (
          <h1 className="welcome-title">
            매일 5분,
            <br />
            AI와 진짜 한국어 회화
          </h1>
        )}

        {!compact && (
          <div className="welcome-features">
            {WELCOME_FEATURES.map((item) => (
              <div className="value-item" key={item.copy}>
                <span>{item.icon}</span>
                <span>{item.copy}</span>
              </div>
            ))}
          </div>
        )}

        <div className="auth-heading">
          <h2>{mode === 'login' ? '로그인하고 시작하기' : '무료로 회원가입'}</h2>
          <p>
            {mode === 'login'
              ? '학습 유형 진단 결과와 학습 기록을 이어서 확인하세요.'
              : '몇 초면 가입 완료, 바로 진단과 학습을 시작할 수 있어요.'}
          </p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            <span>아이디</span>
            <input
              value={form.username}
              onChange={updateField('username')}
              autoComplete="username"
              required
            />
          </label>

          {mode === 'signup' && (
            <label>
              <span>이메일</span>
              <input
                type="email"
                value={form.email}
                onChange={updateField('email')}
                autoComplete="email"
                required
              />
            </label>
          )}

          <label>
            <span>비밀번호</span>
            <input
              type="password"
              value={form.password}
              onChange={updateField('password')}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={8}
              required
            />
          </label>

          {error && <p className="auth-error">⚠ {error}</p>}

          <button type="submit" className="primary-cta auth-submit" disabled={submitting}>
            {submitting ? '처리 중...' : mode === 'login' ? '로그인' : '회원가입'}
          </button>
        </form>

        <a className="google-login" href={`${API_BASE}/oauth2/authorization/google`}>
          <GoogleIcon />
          구글 계정으로 계속하기
        </a>

        <div className="auth-toggle">
          {mode === 'login' ? (
            <>
              <span>아직 계정이 없으신가요?</span>
              <button type="button" onClick={() => switchMode('signup')}>회원가입</button>
            </>
          ) : (
            <>
              <span>이미 계정이 있으신가요?</span>
              <button type="button" onClick={() => switchMode('login')}>로그인</button>
            </>
          )}
        </div>

        {!compact && (
          <div className="welcome-pricing" aria-label="요금제">
            {WELCOME_PLANS.map((plan) => (
              <div className="welcome-plan" key={plan.name}>
                <span>{plan.name}</span>
                <strong>{plan.price}</strong>
                <small>{plan.label}</small>
              </div>
            ))}
          </div>
        )}
      </div>
  )
}


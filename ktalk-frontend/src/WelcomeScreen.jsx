import { useState } from 'react'
import axios from 'axios'
import ktalkLogo from './assets/ktalk-logo.png'
import { API_BASE, AUTH_URL } from './api'
import { Bi } from './EnglishHint'

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
  { icon: '💬', copy: 'AI와 실시간으로 대화하며 연습', en: 'Practice by chatting with AI in real time' },
  { icon: '🎯', copy: '내 실력에 맞춘 맞춤 학습', en: 'Learning matched to your level' },
  { icon: '🔥', copy: '하루 5분, 부담 없이 꾸준히', en: 'Just 5 minutes a day, stress-free' },
]

export function AuthCard({ onAuthenticated, onClose, compact = false }) {
  const [form, setForm] = useState({ username: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const updateField = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  // 회원가입은 구글 계정으로만 받는다. 아이디/비밀번호 로그인은 예전에 가입한 기존 계정용이다.
  const handleSubmit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const response = await axios.post(`${AUTH_URL}/login`, {
        username: form.username,
        password: form.password,
      })
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
            <Bi en="5 minutes a day - real Korean conversation with AI">{''}</Bi>
          </h1>
        )}

        {!compact && (
          <div className="welcome-features">
            {WELCOME_FEATURES.map((item) => (
              <div className="value-item" key={item.copy}>
                <span>{item.icon}</span>
                <span><Bi en={item.en}>{item.copy}</Bi></span>
              </div>
            ))}
          </div>
        )}

        <div className="auth-heading">
          <h2><Bi en="Start with your Google account">구글 계정으로 시작하기</Bi></h2>
          <p><Bi en="Sign up and log in with just your Google account. Your progress is saved to it.">회원가입과 로그인은 구글 계정 하나로 끝나요. 학습 기록도 계정에 이어서 저장돼요.</Bi></p>
        </div>

        <a className="google-login" href={`${API_BASE}/oauth2/authorization/google`}>
          <GoogleIcon />
          <span><Bi en="Continue with Google">구글 계정으로 계속하기</Bi></span>
        </a>

        <details className="legacy-login">
          <summary><Bi en="Log in with an existing ID">기존 아이디로 로그인</Bi></summary>
          <form className="auth-form" onSubmit={handleSubmit}>
            <label>
              <span><Bi en="ID">아이디</Bi></span>
              <input
                value={form.username}
                onChange={updateField('username')}
                autoComplete="username"
                required
              />
            </label>

            <label>
              <span><Bi en="Password">비밀번호</Bi></span>
              <input
                type="password"
                value={form.password}
                onChange={updateField('password')}
                autoComplete="current-password"
                required
              />
            </label>

            {error && <p className="auth-error">⚠ {error}</p>}

            <button type="submit" className="primary-cta auth-submit" disabled={submitting}>
              <Bi en={submitting ? 'Processing...' : 'Log in'}>{submitting ? '처리 중...' : '로그인'}</Bi>
            </button>
          </form>
        </details>

        {!compact && (
          <div className="welcome-pricing" aria-label="무료 안내">
            <div className="welcome-plan">
              <span>K-Talk AI</span>
              <strong><Bi en="Free">무료</Bi></strong>
              <small><Bi en="All features are free to use">모든 기능 무료로 이용</Bi></small>
            </div>
          </div>
        )}
      </div>
  )
}


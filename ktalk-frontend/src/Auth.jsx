import { useState } from 'react'
import axios from 'axios'
import { API_BASE } from './api'

const API_URL = `${API_BASE}/api/auth`
const GOOGLE_LOGIN_URL = `${API_BASE}/oauth2/authorization/google`

function Auth({ onLogin, initialMode = 'login' }) {
  const [isLogin, setIsLogin] = useState(initialMode !== 'signup')
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    email: '',
  })

  const handleSubmit = async (event) => {
    event.preventDefault()
    try {
      const endpoint = isLogin ? '/login' : '/register'
      const response = await axios.post(`${API_URL}${endpoint}`, formData, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
      })

      if (response.data.success) {
        if (isLogin) {
          if (response.data.token) {
            localStorage.setItem('token', response.data.token)
          }
          onLogin(response.data.user)
        } else {
          alert('회원가입이 완료되었습니다. 이제 로그인해 주세요.')
          setIsLogin(true)
          setFormData({ username: '', password: '', email: '' })
        }
      }
    } catch (error) {
      alert(error.response?.data?.message || '요청 처리 중 오류가 발생했습니다.')
    }
  }

  const toggleMode = () => {
    setIsLogin((value) => !value)
    setFormData({ username: '', password: '', email: '' })
  }

  return (
    <div className="auth-card">
      <div className="auth-heading">
        <span className="eyebrow">K-Talk AI Account</span>
        <h2>{isLogin ? '다시 오신 걸 환영해요' : '무료 계정 만들기'}</h2>
        <p>{isLogin ? '학습 기록과 오답노트를 이어서 확인하세요.' : 'AI 학습 루틴을 저장하고 매일 복습하세요.'}</p>
      </div>

      <form onSubmit={handleSubmit} className="auth-form">
        <label>
          <span>아이디</span>
          <input
            type="text"
            placeholder="username"
            value={formData.username}
            onChange={(event) => setFormData({ ...formData, username: event.target.value })}
            required
          />
        </label>

        {!isLogin && (
          <label>
            <span>이메일</span>
            <input
              type="email"
              placeholder="you@example.com"
              value={formData.email}
              onChange={(event) => setFormData({ ...formData, email: event.target.value })}
              required
            />
          </label>
        )}

        <label>
          <span>비밀번호</span>
          <input
            type="password"
            placeholder="8자 이상"
            value={formData.password}
            onChange={(event) => setFormData({ ...formData, password: event.target.value })}
            minLength={isLogin ? undefined : 8}
            required
          />
        </label>

        <button type="submit" className="auth-submit">
          {isLogin ? '로그인' : '회원가입'}
        </button>
      </form>

      <a href={GOOGLE_LOGIN_URL} className="google-login">
        Google로 계속하기
      </a>

      <p className="auth-toggle">
        {isLogin ? '아직 계정이 없나요?' : '이미 계정이 있나요?'}
        <button type="button" onClick={toggleMode}>
          {isLogin ? '회원가입' : '로그인'}
        </button>
      </p>
    </div>
  )
}

export default Auth

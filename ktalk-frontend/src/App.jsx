import { useState, useEffect } from 'react'
import axios from 'axios'
import Auth from './Auth'
import ContentManager from './components/ContentManager'
import ClipAndLearn from './components/ClipAndLearn'
import CharacterChat from './components/CharacterChat'
import PronunciationCoach from './components/PronunciationCoach'
import PersonalizedLearning from './components/PersonalizedLearning'
import RecommendedChannels from './components/RecommendedChannels'
import { AUTH_URL } from './api'
import { TAB_COLORS } from './theme'
import ktalkLogo from './assets/ktalk-logo.png'
import './App.css'

const TABS = [
  { id: 'contents', label: '📚 콘텐츠 관리' },
  { id: 'clip', label: '🎬 Clip & Learn' },
  { id: 'chat', label: '💬 Character Chat' },
  { id: 'pronunciation', label: '🎤 Pronunciation Coach' },
  { id: 'personalized', label: '🎯 Personalized Learning' },
]

function App() {
  const [user, setUser] = useState(null)
  const [authChecked, setAuthChecked] = useState(false)
  const [activeTab, setActiveTab] = useState('contents')
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [passwordChanging, setPasswordChanging] = useState(false)

  useEffect(() => {
    // Google OAuth2 리다이렉트 처리: /oauth2/redirect?token=...
    const params = new URLSearchParams(window.location.search)
    const tokenFromRedirect = params.get('token')
    if (window.location.pathname === '/oauth2/redirect' && tokenFromRedirect) {
      localStorage.setItem('token', tokenFromRedirect)
      window.history.replaceState({}, '', '/')
    }

    // 랜딩페이지 등 외부에서 ?tab=xxx 로 특정 기능을 바로 열 수 있도록 지원
    const tabParam = params.get('tab')
    if (tabParam && TABS.some((t) => t.id === tabParam)) {
      setActiveTab(tabParam)
    }

    const token = localStorage.getItem('token')
    if (!token) {
      setAuthChecked(true)
      return
    }

    axios.get(`${AUTH_URL}/me`, {
      headers: { Authorization: `Bearer ${token}` }
    })
        .then((response) => {
          if (response.data.success) {
            setUser(response.data.user)
          }
        })
        .catch(() => {
          localStorage.removeItem('token')
        })
        .finally(() => setAuthChecked(true))
  }, [])

  const handleLogout = () => {
    localStorage.removeItem('token')
    setUser(null)
  }

  const handleChangePassword = async (e) => {
    e.preventDefault()
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      alert('새 비밀번호가 일치하지 않습니다.')
      return
    }

    setPasswordChanging(true)
    try {
      const token = localStorage.getItem('token')
      const response = await axios.post(
          `${AUTH_URL}/change-password`,
          { currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword },
          { headers: { Authorization: `Bearer ${token}` } }
      )
      if (response.data.success) {
        alert('비밀번호가 변경되었습니다.')
        setShowPasswordForm(false)
        setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      }
    } catch (error) {
      alert('비밀번호 변경 실패: ' + (error.response?.data?.message || error.message))
    } finally {
      setPasswordChanging(false)
    }
  }

  if (!authChecked) {
    return null
  }

  if (!user) {
    return <Auth onLogin={setUser} />
  }

  return (
      <div style={{ maxWidth: '900px', width: '100%', minWidth: 0, margin: '0 auto', padding: '20px', overflowX: 'hidden', boxSizing: 'border-box' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'clamp(1.1rem, 4.5vw, 1.6rem)' }}>
            <img src={ktalkLogo} alt="K-Talk" style={{ height: '32px', width: 'auto', flexShrink: 0 }} />
            <span style={{ display: 'flex', alignItems: 'baseline', flexWrap: 'wrap', gap: '6px' }}>
              <span>K-talk</span>
              <em style={{ fontSize: '0.55em', fontStyle: 'italic', fontWeight: 'normal', letterSpacing: '0.3px' }}>through k-pop, k-drama</em>
            </span>
          </h1>
          <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
            <span>안녕하세요, {user.username}님!</span>
            <button
                onClick={() => setShowPasswordForm(!showPasswordForm)}
                style={{ padding: '8px 16px', cursor: 'pointer', backgroundColor: '#6c757d', color: 'white', border: 'none', borderRadius: '4px', whiteSpace: 'nowrap' }}
            >
              비밀번호 변경
            </button>
            <button onClick={handleLogout} style={{ padding: '8px 16px', cursor: 'pointer', backgroundColor: '#dc3545', color: 'white', border: 'none', borderRadius: '4px', whiteSpace: 'nowrap' }}>
              로그아웃
            </button>
          </div>
        </div>

        {showPasswordForm && (
            <form onSubmit={handleChangePassword} style={{
              marginBottom: '20px', padding: '15px', border: '1px solid #ddd', borderRadius: '8px',
              display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center'
            }}>
              <input
                  type="password"
                  placeholder="현재 비밀번호"
                  value={passwordForm.currentPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                  style={{ padding: '8px', flex: '1 1 140px' }}
                  required
              />
              <input
                  type="password"
                  placeholder="새 비밀번호 (8자 이상)"
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                  style={{ padding: '8px', flex: '1 1 140px' }}
                  required
                  minLength={8}
              />
              <input
                  type="password"
                  placeholder="새 비밀번호 확인"
                  value={passwordForm.confirmPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                  style={{ padding: '8px', flex: '1 1 140px' }}
                  required
              />
              <button type="submit" disabled={passwordChanging} style={{ padding: '8px 16px', cursor: passwordChanging ? 'not-allowed' : 'pointer' }}>
                {passwordChanging ? '변경 중...' : '변경'}
              </button>
            </form>
        )}

        <nav style={{ display: 'flex', gap: 'clamp(2px, 1vw, 6px)', marginBottom: '24px', width: '100%' }}>
          {TABS.map((tab) => (
              <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  style={{
                    flex: '1 1 0',
                    minWidth: 0,
                    padding: 'clamp(4px, 1.2vw, 8px) clamp(2px, 1vw, 10px)',
                    fontSize: 'clamp(8px, 2.4vw, 14px)',
                    cursor: 'pointer',
                    border: 'none',
                    borderRadius: '10px',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    backgroundColor: activeTab === tab.id ? TAB_COLORS[tab.id].accent : '#e9ecef',
                    color: activeTab === tab.id ? 'white' : '#333',
                    fontWeight: activeTab === tab.id ? 'bold' : 'normal'
                  }}
              >
                {tab.label}
              </button>
          ))}
        </nav>

        <RecommendedChannels />

        {activeTab === 'contents' && <ContentManager />}
        {activeTab === 'clip' && <ClipAndLearn />}
        {activeTab === 'chat' && <CharacterChat />}
        {activeTab === 'pronunciation' && <PronunciationCoach />}
        {activeTab === 'personalized' && <PersonalizedLearning onNavigate={setActiveTab} />}
      </div>
  )
}

export default App

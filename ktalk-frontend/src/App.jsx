import { useState, useEffect } from 'react'
import axios from 'axios'
import Auth from './Auth'
import ContentManager from './components/ContentManager'
import ClipAndLearn from './components/ClipAndLearn'
import CharacterChat from './components/CharacterChat'
import PronunciationCoach from './components/PronunciationCoach'
import PersonalizedLearning from './components/PersonalizedLearning'
import { AUTH_URL } from './api'
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

  if (!authChecked) {
    return null
  }

  if (!user) {
    return <Auth onLogin={setUser} />
  }

  return (
      <div style={{ maxWidth: '900px', margin: '0 auto', padding: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h1>📚 K-Talk AI</h1>
          <div>
            <span style={{ marginRight: '15px' }}>안녕하세요, {user.username}님!</span>
            <button onClick={handleLogout} style={{ padding: '8px 16px', cursor: 'pointer', backgroundColor: '#dc3545', color: 'white', border: 'none', borderRadius: '4px' }}>
              로그아웃
            </button>
          </div>
        </div>

        <nav style={{ display: 'flex', gap: '8px', marginBottom: '24px', flexWrap: 'wrap' }}>
          {TABS.map((tab) => (
              <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  style={{
                    padding: '10px 16px',
                    cursor: 'pointer',
                    border: 'none',
                    borderRadius: '20px',
                    backgroundColor: activeTab === tab.id ? '#007bff' : '#e9ecef',
                    color: activeTab === tab.id ? 'white' : '#333',
                    fontWeight: activeTab === tab.id ? 'bold' : 'normal'
                  }}
              >
                {tab.label}
              </button>
          ))}
        </nav>

        {activeTab === 'contents' && <ContentManager />}
        {activeTab === 'clip' && <ClipAndLearn />}
        {activeTab === 'chat' && <CharacterChat />}
        {activeTab === 'pronunciation' && <PronunciationCoach />}
        {activeTab === 'personalized' && <PersonalizedLearning onNavigate={setActiveTab} />}
      </div>
  )
}

export default App

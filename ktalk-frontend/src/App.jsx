import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import Auth from './Auth'
import ContentManager from './components/ContentManager'
import ClipAndLearn from './components/ClipAndLearn'
import CharacterChat from './components/CharacterChat'
import PronunciationCoach from './components/PronunciationCoach'
import PersonalizedLearning from './components/PersonalizedLearning'
import RecommendedChannels from './components/RecommendedChannels'
import { AUTH_URL } from './api'
import ktalkLogo from './assets/ktalk-logo.png'
import './App.css'

const TABS = [
  { id: 'contents', label: 'AI 콘텐츠', short: '콘텐츠' },
  { id: 'clip', label: '유튜브 클립 학습', short: '클립' },
  { id: 'chat', label: 'AI 회화 연습', short: '회화' },
  { id: 'pronunciation', label: 'AI 발음 코치', short: '발음' },
  { id: 'personalized', label: '개인화 복습', short: '복습' },
]

const missionCards = [
  { title: '오늘의 미션', value: '12개', copy: '오늘 복습할 표현', tone: 'mint' },
  { title: '실전복습', value: '3분', copy: 'AI와 바로 말하기', tone: 'blue' },
  { title: '오답노트', value: '7개', copy: '다시 볼 표현', tone: 'rose' },
  { title: 'AI 발음 코치', value: '92점', copy: '최근 발음 정확도', tone: 'violet' },
  { title: '추천 유튜브 학습', value: '5개', copy: '내 수준 맞춤 클립', tone: 'amber' },
]

const featureCards = [
  {
    title: '유튜브 클립 학습',
    copy: '좋아하는 영상에서 표현을 뽑아 짧은 미션으로 학습합니다.',
  },
  {
    title: 'AI 회화 연습',
    copy: '오늘 배운 표현을 실제 대화처럼 말하며 복습합니다.',
  },
  {
    title: '발음 코치',
    copy: '내 발음을 듣고 자연스러운 억양과 정확도를 피드백합니다.',
  },
  {
    title: '오답노트',
    copy: '틀린 표현을 자동으로 모아 다음 복습 루프로 보냅니다.',
  },
  {
    title: '개인화 추천',
    copy: '레벨, 관심사, 학습 기록에 맞춰 다음 콘텐츠를 추천합니다.',
  },
]

function App() {
  const [user, setUser] = useState(null)
  const [authChecked, setAuthChecked] = useState(false)
  const [activeTab, setActiveTab] = useState('contents')
  const [showLogin, setShowLogin] = useState(false)
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [passwordChanging, setPasswordChanging] = useState(false)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const tokenFromRedirect = params.get('token')

    if (window.location.pathname === '/oauth2/redirect' && tokenFromRedirect) {
      localStorage.setItem('token', tokenFromRedirect)
      window.history.replaceState({}, '', '/')
    }

    const tabParam = params.get('tab')
    if (tabParam && TABS.some((tab) => tab.id === tabParam)) {
      setActiveTab(tabParam)
    }

    const token = localStorage.getItem('token')
    if (!token) {
      setAuthChecked(true)
      return
    }

    axios.get(`${AUTH_URL}/me`, {
      headers: { Authorization: `Bearer ${token}` },
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

  const isLoggedIn = authChecked && !!user

  // 로그인 전 방문자에게는 "3일 연속 학습", "EXP 1,240" 같은 개인화 지표를
  // 보여줄 수 없다 — 아직 아무 활동도 없는 사람에게 남의 기록을 자기 것처럼
  // 보여주는 셈이라 오해를 준다(실제로도 하드코딩된 예시 값이지 실제 사용자
  // 데이터가 아니다). 로그인 전에는 숫자 지표 대신 이 앱이 뭘 해주는지를
  // 설명하는 항목으로 바꾼다.
  const stats = useMemo(() => [
    { label: '스트릭', value: '3일', icon: '🔥' },
    { label: 'EXP', value: '1,240', icon: '⚡' },
    { label: '레벨', value: 'Lv. 8', icon: '🏅' },
    { label: '오늘 복습', value: '12개', icon: '🎯' },
  ], [])

  const valueProps = useMemo(() => [
    { icon: '🎬', copy: '유튜브 클립에서 표현을 뽑아 학습' },
    { icon: '💬', copy: 'AI와 실시간으로 대화하며 연습' },
    { icon: '🎯', copy: '내 수준에 맞춘 맞춤 복습' },
  ], [])

  const handleLoginSuccess = (nextUser) => {
    setUser(nextUser)
    setShowLogin(false)
  }

  const handleLogout = () => {
    localStorage.removeItem('token')
    setUser(null)
    setShowPasswordForm(false)
  }

  const handleChangePassword = async (event) => {
    event.preventDefault()
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
        { headers: { Authorization: `Bearer ${token}` } },
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

  const jumpToExperience = (tabId = 'contents') => {
    setActiveTab(tabId)
    document.getElementById('ai-experience')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="ktalk-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="K-Talk AI 홈">
          <img src={ktalkLogo} alt="" className="brand-logo" />
          <span>
            <strong>K-Talk AI</strong>
            <small>Learn with clips and conversation</small>
          </span>
        </a>

        <nav className="header-nav" aria-label="주요 메뉴">
          <a href="#features">기능</a>
          <a href="#ai-experience">복습</a>
          <a href="#pricing">가격</a>
        </nav>

        <div className="header-actions">
          {authChecked && user ? (
            <div className="user-chip">
              <span>{user.username}</span>
              <button type="button" onClick={() => setShowPasswordForm((value) => !value)}>설정</button>
              <button type="button" onClick={handleLogout}>로그아웃</button>
            </div>
          ) : (
            <button className="login-button" type="button" onClick={() => setShowLogin(true)}>
              로그인
            </button>
          )}
        </div>
      </header>

      {showPasswordForm && user && (
        <form className="password-panel glass-card" onSubmit={handleChangePassword}>
          <input
            type="password"
            placeholder="현재 비밀번호"
            value={passwordForm.currentPassword}
            onChange={(event) => setPasswordForm({ ...passwordForm, currentPassword: event.target.value })}
            required
          />
          <input
            type="password"
            placeholder="새 비밀번호"
            value={passwordForm.newPassword}
            onChange={(event) => setPasswordForm({ ...passwordForm, newPassword: event.target.value })}
            minLength={8}
            required
          />
          <input
            type="password"
            placeholder="새 비밀번호 확인"
            value={passwordForm.confirmPassword}
            onChange={(event) => setPasswordForm({ ...passwordForm, confirmPassword: event.target.value })}
            required
          />
          <button type="submit" disabled={passwordChanging}>
            {passwordChanging ? '변경 중' : '변경'}
          </button>
        </form>
      )}

      <main id="top">
        <section className="hero-section">
          <div className="hero-copy">
            <div className="eyebrow">AI 영어 루틴 메이트</div>
            <h1>K-pop, K-drama로 배우고, AI와 말하며 복습하세요</h1>
            <p className="hero-subtitle">
              오늘 배운 표현을 실전 대화로 바꾸는 AI 영어 학습 앱
            </p>
            <div className="hero-actions">
              <button className="primary-cta" type="button" onClick={() => jumpToExperience('contents')}>
                무료로 시작하기
              </button>
              <button className="secondary-cta" type="button" onClick={() => jumpToExperience('chat')}>
                AI 회화 체험하기
              </button>
            </div>
            {isLoggedIn ? (
              <div className="trust-row" aria-label="학습 지표">
                {stats.map((stat) => (
                  <div className="mini-stat" key={stat.label}>
                    <span>{stat.icon}</span>
                    <strong>{stat.value}</strong>
                    <small>{stat.label}</small>
                  </div>
                ))}
              </div>
            ) : (
              <div className="value-row" aria-label="주요 기능">
                {valueProps.map((item) => (
                  <div className="value-item" key={item.copy}>
                    <span>{item.icon}</span>
                    <span>{item.copy}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {isLoggedIn ? (
            <aside className="dashboard-preview glass-card" aria-label="학습 대시보드 미리보기">
              <div className="preview-top">
                <div>
                  <span className="preview-kicker">오늘의 대시보드</span>
                  <h2>3일 연속 학습</h2>
                </div>
                <span className="streak-badge">🔥</span>
              </div>
              <div className="progress-ring" aria-label="진행률 68%">
                <span>68%</span>
              </div>
              <div className="preview-grid">
                <div>
                  <small>오늘 복습</small>
                  <strong>12개</strong>
                </div>
                <div>
                  <small>획득 EXP</small>
                  <strong>+180</strong>
                </div>
              </div>
              <button type="button" className="start-review" onClick={() => jumpToExperience('contents')}>
                실전복습 시작
              </button>
            </aside>
          ) : (
            <aside className="dashboard-preview glass-card" aria-label="AI 회화 예시">
              <div className="preview-top">
                <div>
                  <span className="preview-kicker">AI 회화 예시</span>
                  <h2>이렇게 연습해요</h2>
                </div>
                <span className="streak-badge">💬</span>
              </div>
              <div className="chat-preview">
                <div className="chat-bubble ai">Nice to meet you! What did you do this weekend?</div>
                <div className="chat-bubble me">I watched a K-drama and practiced this line!</div>
                <div className="chat-bubble ai">Great sentence 👍 Try this next: "It was so much fun."</div>
              </div>
              <button type="button" className="start-review" onClick={() => jumpToExperience('chat')}>
                AI 회화 체험하기
              </button>
            </aside>
          )}
        </section>

        <section className="mission-strip" aria-label="오늘의 학습 카드">
          {missionCards.map((card) => (
            <article className={`mission-card ${card.tone}`} key={card.title}>
              <span>{card.title}</span>
              <strong>{card.value}</strong>
              <small>{card.copy}</small>
            </article>
          ))}
        </section>

        <section className="section-block" id="features">
          <div className="section-heading">
            <span className="eyebrow">Feature Cards</span>
            <h2>말하고, 틀리고, 다시 익히는 학습 루프</h2>
          </div>
          <div className="feature-grid">
            {featureCards.map((feature) => (
              <article className="feature-card glass-card" key={feature.title}>
                <h3>{feature.title}</h3>
                <p>{feature.copy}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="loop-section glass-card">
          <div>
            <span className="eyebrow">Review Loop</span>
            <h2>학습 → AI 대화 → 오답 저장 → 복습 → EXP 보상</h2>
          </div>
          <div className="loop-steps">
            {['학습', 'AI 대화', '오답 저장', '복습', 'EXP 보상'].map((step) => (
              <span key={step}>{step}</span>
            ))}
          </div>
        </section>

        <section className="section-block learning-board" id="ai-experience">
          <div className="section-heading">
            <span className="eyebrow">AI Learning Lab</span>
            <h2>로그인 없이 먼저 둘러보세요</h2>
            <p>콘텐츠 생성, 유튜브 학습, 회화, 발음 코치까지 기존 기능을 그대로 체험할 수 있습니다.</p>
          </div>

          <RecommendedChannels />

          <nav className="app-tabs" aria-label="AI 학습 기능">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={activeTab === tab.id ? 'active' : ''}
                onClick={() => setActiveTab(tab.id)}
              >
                <span>{tab.label}</span>
                <small>{tab.short}</small>
              </button>
            ))}
          </nav>

          <div className="tool-surface glass-card">
            {activeTab === 'contents' && <ContentManager />}
            {activeTab === 'clip' && <ClipAndLearn />}
            {activeTab === 'chat' && <CharacterChat />}
            {activeTab === 'pronunciation' && <PronunciationCoach />}
            {activeTab === 'personalized' && <PersonalizedLearning onNavigate={setActiveTab} />}
          </div>
        </section>

        <section className="pricing-note" id="pricing">
          <div>
            <span className="eyebrow">Pricing</span>
            <h2>무료로 시작하고, 필요한 만큼 확장하세요</h2>
          </div>
          <button type="button" className="secondary-cta" onClick={() => setShowLogin(true)}>
            계정 만들기
          </button>
        </section>
      </main>

      {showLogin && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setShowLogin(false)}>
          <div className="auth-modal" role="dialog" aria-modal="true" aria-label="로그인" onMouseDown={(event) => event.stopPropagation()}>
            <button className="modal-close" type="button" aria-label="로그인 닫기" onClick={() => setShowLogin(false)}>
              ×
            </button>
            <Auth onLogin={handleLoginSuccess} />
          </div>
        </div>
      )}
    </div>
  )
}

export default App

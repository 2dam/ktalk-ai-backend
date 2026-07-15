import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import Auth from './Auth'
import ContentManager from './components/ContentManager'
import ClipAndLearn from './components/ClipAndLearn'
import CharacterChat from './components/CharacterChat'
import PronunciationCoach from './components/PronunciationCoach'
import PersonalizedLearning from './components/PersonalizedLearning'
import AssessmentSurvey from './components/AssessmentSurvey'
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
  { id: 'assessment', label: '학습 유형 진단', short: '유형진단' },
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
    title: '학습 유형 진단',
    copy: '20문항 진단으로 6가지 유형 중 나에게 맞는 유형을 찾고, 맞춤 교재·커리큘럼을 추천받습니다.',
  },
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

const howSteps = [
  {
    kicker: '01 · DISCOVER',
    title: '나의 학습 유형 진단',
    desc: '생활습관, 집중력 패턴, 학습 동기 등 20문항으로 6가지 유형 중 나와 가장 가까운 유형을 찾아요.',
    link: '약 5~7분 소요 →',
  },
  {
    kicker: '02 · PERSONALIZE',
    title: '교재와 8주 커리큘럼 추천',
    desc: '진단된 유형에 맞춰 교재·강의·앱과 단계별 커리큘럼을 자동으로 구성해요.',
    link: '완전 맞춤 구성 →',
  },
  {
    kicker: '03 · GROW',
    title: '학습 → AI 대화 → 복습 루프',
    desc: '유튜브 클립과 AI 회화·발음 코치로 실전 연습하고, 개인화 복습으로 다시 다져요.',
    link: '꾸준히 반복하기 →',
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

  const trustPoints = useMemo(() => [
    { icon: '✓', title: '6가지 학습 유형', desc: '생활습관·집중력·동기를 함께 분석' },
    { icon: '✦', title: 'TOPIK 맞춤 커리큘럼', desc: '유형별 교재·강의·8주 학습 전략' },
    { icon: '↗', title: 'AI 회화·발음 코치', desc: '매일 실전처럼 말하고 복습' },
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
            <p className="eyebrow">K-CONTENT × PERSONAL AI</p>
            <h1>좋아하는 콘텐츠로,<br /><em>나답게 배우는</em> 한국어.</h1>
            <p className="hero-subtitle">
              20문항 학습 유형 진단으로 나에게 맞는 교재와 8주 커리큘럼을 추천받고, AI와 함께 실전 회화까지 연습하세요.
            </p>
            <div className="hero-actions">
              <button className="primary-cta" type="button" onClick={() => jumpToExperience('assessment')}>
                무료 학습 유형 진단 시작하기 →
              </button>
              <button className="secondary-cta" type="button" onClick={() => jumpToExperience('chat')}>
                AI 회화 체험하기
              </button>
            </div>
            <div className="trust-row" aria-label="핵심 기능">
              {trustPoints.map((point) => (
                <div className="mini-stat" key={point.title}>
                  <span>{point.icon}</span>
                  <div>
                    <strong>{point.title}</strong>
                    <small>{point.desc}</small>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <aside className="dashboard-preview glass-card" aria-label="학습 유형 진단 결과 미리보기">
            <div className="preview-top">
              <div>
                <span className="preview-kicker">MY LEARNING MAP</span>
                <h2>회원님의 맞춤 학습</h2>
              </div>
              <span className="streak-badge">🧠</span>
            </div>
            <div className="progress-ring" aria-label="진단된 학습 유형">
              <span className="type-label">STRATEGIC ANALYST</span>
              <span>전략적 분석가</span>
              <small>스스로 계획하고 오답을 분석하는 능력이 뛰어난 자기주도형 학습자예요.</small>
            </div>
            <div className="preview-grid">
              <div className="cover">
                <small>TOPIK 맞춤 교재</small>
                <b>3~4급</b>
                <span>PLAN &amp; ANALYZE</span>
              </div>
              <div className="book-copy">
                <p className="eyebrow">이번 달 교재</p>
                <h3>기출문제집 + 오답 노트 전용 교재</h3>
                <p>기출 5회독 · 영역별 약점 데이터화</p>
              </div>
            </div>
            <div className="week-grid">
              <span>월<br />클립</span>
              <span>화<br />회화</span>
              <span>수<br />발음</span>
              <span>목<br />회화</span>
              <span>금<br />복습</span>
            </div>
            <button type="button" className="start-review" onClick={() => jumpToExperience('assessment')}>
              내 학습 유형 진단하기
            </button>
          </aside>
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

        <section className="loop-section">
          <div className="how-head">
            <span className="eyebrow">HOW K-TALK WORKS</span>
            <h2>진단부터 학습 루프까지</h2>
            <p>추천만 하고 끝나지 않아요. 진단 결과가 실제 학습 흐름으로 이어집니다.</p>
          </div>
          <div className="loop-steps">
            {howSteps.map((step) => (
              <article key={step.kicker}>
                <span className="step-kicker">{step.kicker}</span>
                <span className="step-title">{step.title}</span>
                <span className="step-desc">{step.desc}</span>
                <span className="step-link">{step.link}</span>
              </article>
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
            {activeTab === 'assessment' && <AssessmentSurvey />}
          </div>
        </section>

        <section className="pricing-note" id="pricing">
          <h2>나에게 맞는 한국어 공부,<br />오늘 처음 만나보세요.</h2>
          <p>진단부터 8주 커리큘럼까지 무료로 시작할 수 있어요.</p>
          <button type="button" className="secondary-cta" onClick={() => jumpToExperience('assessment')}>
            내 학습 유형 알아보기 →
          </button>
        </section>
      </main>

      <footer className="site-footer">
        <span>© 2026 K-Talk AI · Made with ♥ in Seoul</span>
        <span>K-콘텐츠 기반 AI 한국어 학습</span>
      </footer>

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
